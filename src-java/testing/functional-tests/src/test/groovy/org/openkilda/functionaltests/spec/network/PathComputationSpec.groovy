package org.openkilda.functionaltests.spec.network

import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.Constants.DEFAULT_COST

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.model.system.KildaConfigurationDto
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.PathComputationStrategy
import org.openkilda.northbound.dto.v1.flows.FlowPatchDto
import org.openkilda.testing.model.topology.TopologyDefinition.Isl

class PathComputationSpec extends HealthCheckSpecification {

    @Tidy
    def "Default path computation strategy is used when flow does not specify it"() {
        given: "Default path computation strategy is COST"
        def initConfig = northbound.getKildaConfiguration()
        northbound.updateKildaConfiguration(
                new KildaConfigurationDto(pathComputationStrategy: PathComputationStrategy.COST))

        and: "Switch pair with two paths at least"
        def swPair = topologyHelper.switchPairs.find { it.paths.size() >= 2 }

        and: "Update paths so that one path has minimal total latency and the other has minimal total cost"
        def costEffectivePath = swPair.paths[0]
        def latencyEffectivePath = swPair.paths[1]
        swPair.paths.findAll { it != costEffectivePath }.each { pathHelper.makePathMorePreferable(costEffectivePath, it) }
        def latencyIsls = pathHelper.getInvolvedIsls(latencyEffectivePath).collectMany { [it, it.reversed] }
        Map<Isl, Long> originalLatencies = latencyIsls.collectEntries { [(it): northbound.getLink(it).latency] }
        latencyIsls.each { database.updateIslLatency(it, 1) }

        when: "Create flow without selecting path strategy"
        def flow = flowHelperV2.randomFlow(swPair).tap { it.pathComputationStrategy = null }
        def createResponse = flowHelperV2.addFlow(flow)

        then: "Flow is created with 'Cost' strategy (current default)"
        createResponse.pathComputationStrategy == PathComputationStrategy.COST.toString().toLowerCase()
        northboundV2.getFlow(flow.flowId).pathComputationStrategy == PathComputationStrategy.COST.toString().toLowerCase()

        and: "Flow is actually built on the path with the least cost"
        pathHelper.convert(northbound.getFlowPath(flow.flowId)) == costEffectivePath

        when: "Update default strategy to LATENCY"
        northbound.updateKildaConfiguration(
                new KildaConfigurationDto(pathComputationStrategy: PathComputationStrategy.LATENCY.toString()))

        then: "Existing flow remains with COST strategy and on the same path"
        northboundV2.getFlow(flow.flowId).pathComputationStrategy == PathComputationStrategy.COST.toString().toLowerCase()
        pathHelper.convert(northbound.getFlowPath(flow.flowId)) == costEffectivePath

        and: "Manual reroute of the flow responds that flow is already on the best path"
        !northboundV2.rerouteFlow(flow.flowId).rerouted

        when: "Create a new flow without specifying path computation strategy"
        def flow2 = flowHelperV2.randomFlow(swPair).tap { it.pathComputationStrategy = null }
        //re-set latencies in DB one more time in case they were recalculated automatically to higher values
        latencyIsls.each { database.updateIslLatency(it, 1) }
        def createResponse2 = flowHelperV2.addFlow(flow2)

        then: "New flow is created with 'Latency' strategy (current default)"
        createResponse2.pathComputationStrategy == PathComputationStrategy.LATENCY.toString().toLowerCase()
        northboundV2.getFlow(flow2.flowId).pathComputationStrategy == PathComputationStrategy.LATENCY.toString().toLowerCase()

        and: "New flow actually uses path with the least latency (ignoring cost)"
        pathHelper.convert(northbound.getFlowPath(flow2.flowId)) == latencyEffectivePath

        cleanup: "Restore kilda config and remove flows, restore costs and latencies"
        initConfig && northbound.updateKildaConfiguration(initConfig)
        flow && flowHelperV2.deleteFlow(flow.flowId)
        flow2 && flowHelperV2.deleteFlow(flow2.flowId)
        originalLatencies && originalLatencies.each { isl, latency -> database.updateIslLatency(isl, latency) }
        northbound.deleteLinkProps(northbound.getAllLinkProps())
    }

    @Tidy
    def "Flow path computation strategy can be updated from LATENCY to COST"() {
        given: "Switch pair with two paths at least"
        def swPair = topologyHelper.switchPairs.find { it.paths.size() >= 2 }

        and: "Update paths so that one path has minimal total latency and the other has minimal total cost"
        def costEffectivePath = swPair.paths[0]
        def latencyEffectivePath = swPair.paths[1]
        swPair.paths.findAll { it != costEffectivePath }.each { pathHelper.makePathMorePreferable(costEffectivePath, it) }
        def latencyIsls = pathHelper.getInvolvedIsls(latencyEffectivePath).collectMany { [it, it.reversed] }
        Map<Isl, Long> originalLatencies = latencyIsls.collectEntries { [(it): northbound.getLink(it).latency] }
        latencyIsls.each { database.updateIslLatency(it, 1) }

        when: "Create flow using Latency strategy"
        def flow = flowHelperV2.randomFlow(swPair)
                .tap { it.pathComputationStrategy = PathComputationStrategy.LATENCY.toString() }
        flowHelperV2.addFlow(flow)

        then: "Flow is built on the least-latency path"
        pathHelper.convert(northbound.getFlowPath(flow.flowId)) == latencyEffectivePath

        when: "Update flow path strategy to 'Cost'"
        flowHelperV2.updateFlow(flow.flowId,
                flow.tap { it.pathComputationStrategy = PathComputationStrategy.COST.toString() })

        then: "Flow path has changed to the least-cost path"
        pathHelper.convert(northbound.getFlowPath(flow.flowId)) == costEffectivePath

        cleanup: "Remove the flow, reset latencies and costs"
        flow && flowHelperV2.deleteFlow(flow.flowId)
        originalLatencies && originalLatencies.each { isl, latency -> database.updateIslLatency(isl, latency) }
        northbound.deleteLinkProps(northbound.getAllLinkProps())
    }

    @Tidy
    def "Target flow path computation strategy is not applied immediately in case flow was updated partially"() {
        given: "Switch pair with two paths at least"
        def swPair = topologyHelper.switchPairs.find { it.paths.size() >= 2 }

        and: "A flow with cost strategy"
        def latencyStrategy = PathComputationStrategy.LATENCY.toString().toLowerCase()
        def costStrategy = PathComputationStrategy.COST.toString().toLowerCase()
        def flow = flowHelperV2.randomFlow(swPair).tap { it.pathComputationStrategy = costStrategy }
        flowHelperV2.addFlow(flow)

        when: "Update path computation strategy(cost -> latency) via partialUpdate"
        northbound.partialUpdate(flow.flowId, new FlowPatchDto().tap {
            it.targetPathComputationStrategy = latencyStrategy
        })

        then: "Path computation strategy is not changed"
        with(northbound.getFlow(flow.flowId)) {
            pathComputationStrategy == costStrategy
            targetPathComputationStrategy == latencyStrategy
        }

        and: "Flow is valid"
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }

        when: "Sync the flow"
        northbound.synchronizeFlow(flow.flowId)
        Wrappers.wait(WAIT_OFFSET / 2) { assert northbound.getFlowStatus(flow.flowId).status == FlowState.UP }

        then: "Path computation strategy is updated and targetPathComputationStrategy is deleted"
        with(northbound.getFlow(flow.flowId)) {
            pathComputationStrategy == latencyStrategy
            !targetPathComputationStrategy
        }

        cleanup:
        flow && flowHelperV2.deleteFlow(flow.flowId)
    }

    @Tidy
    def "Target path computation strategy is applied after updating/rerouting a flow"() {
        given: "Switch pair with two paths at least"
        def swPair = topologyHelper.switchPairs.find { it.paths.size() >= 2 }

        and: "A flow with cost strategy"
        def latencyStrategy = PathComputationStrategy.LATENCY.toString().toLowerCase()
        def costStrategy = PathComputationStrategy.COST.toString().toLowerCase()
        def flow = flowHelperV2.randomFlow(swPair).tap { it.pathComputationStrategy = costStrategy }
        flowHelperV2.addFlow(flow)

        when: "Update path computation strategy(cost -> latency) via partialUpdate"
        northbound.partialUpdate(flow.flowId, new FlowPatchDto().tap {
            it.targetPathComputationStrategy = latencyStrategy
        })

        and: "Reroute the flow"
        northboundV2.rerouteFlow(flow.flowId)
        Wrappers.wait(WAIT_OFFSET / 2) { assert northbound.getFlowStatus(flow.flowId).status == FlowState.UP }

        then: "Path computation strategy is updated and targetPathComputationStrategy is deleted"
        with(northbound.getFlow(flow.flowId)) {
            pathComputationStrategy == latencyStrategy
            !targetPathComputationStrategy
        }

        when: "Update path computation strategy(latency -> cost) via partialUpdate"
        northbound.partialUpdate(flow.flowId, new FlowPatchDto().tap {
            it.targetPathComputationStrategy = costStrategy
        })

        and: "Update the flow"
        flowHelperV2.updateFlow(flow.flowId, flow.tap { it.pathComputationStrategy = null })

        then: "Path computation strategy is updated and targetPathComputationStrategy is deleted"
        with(northbound.getFlow(flow.flowId)) {
            pathComputationStrategy == costStrategy
            !targetPathComputationStrategy
        }

        cleanup:
        flow && flowHelperV2.deleteFlow(flow.flowId)
    }

    @Tidy
    def "System takes available bandwidth into account during creating a flow with COST_AND_AVAILABLE_BANDWIDTH strategy"() {
        given: "Two active neighboring switches with two diverse paths at least(short and long paths)"
        def allPaths
        def swPair = topologyHelper.getAllNeighboringSwitchPairs().find {
            allPaths = it.paths
            allPaths.unique(false) { a, b -> a.intersect(b) == [] ? 1 : 0 }.size() >= 2 &&
                    allPaths.find { it.size() > 2 }
        }

        List<PathNode> shortPath = allPaths.min { it.size() }
        //find path with more than two switches
        List<PathNode> longPath = allPaths.findAll { it != shortPath && it.size() != 2 }.min { it.size() }

        and: "All alternative paths unavailable (bring ports down)"
        def broughtDownIsls = []
        def otherIsls = []
        def involvedIslsOfShortPath = pathHelper.getInvolvedIsls(shortPath)
        def involvedIslsOfLongPath = pathHelper.getInvolvedIsls(longPath)
        def involvedIsls = (involvedIslsOfShortPath + involvedIslsOfLongPath).unique()
        allPaths.findAll { it != shortPath && it != longPath }.each {
            pathHelper.getInvolvedIsls(it).findAll { !(it in involvedIsls || it.reversed in involvedIsls) }.each {
                otherIsls.add(it)
            }
        }
        broughtDownIsls = otherIsls.unique { a, b -> a == b || a == b.reversed ? 0 : 1 }
        broughtDownIsls.every { antiflap.portDown(it.srcSwitch.dpId, it.srcPort) }
        wait(WAIT_OFFSET) {
            assert northbound.getAllLinks().findAll {
                it.state == IslChangeType.FAILED
            }.size() == otherIsls.size() * 2
        }

        and: "Costs of short and long paths are the same"
        def totalCostOfLongPath = involvedIslsOfLongPath.sum { northbound.getLink(it).cost ?: DEFAULT_COST }
        Integer newIslCost = (totalCostOfLongPath / involvedIslsOfShortPath.size()).toInteger()

        involvedIslsOfShortPath.each { isl ->
            northbound.updateLinkProps([islUtils.toLinkProps(isl, ["cost": newIslCost.toString()])])
        }

        and: "Sum of available bandwidth on longPath is less than on shortPath"
        def totalAvailBandwitchOfShortPath = involvedIslsOfShortPath.sum { northbound.getLink(it).availableBandwidth }
        Integer newIslAvailableBandwidth = ((totalAvailBandwitchOfShortPath - 2) / involvedIslsOfLongPath.size()).toInteger()
        involvedIslsOfLongPath.each { isl ->
            database.updateIslAvailableBandwidth(isl, newIslAvailableBandwidth)
        }

        when: "Create a flow with COST_AND_AVAILABLE_BANDWIDTH strategy"
        def flow = flowHelperV2.randomFlow(swPair)
        flow.pathComputationStrategy = PathComputationStrategy.COST_AND_AVAILABLE_BANDWIDTH
        flowHelperV2.addFlow(flow)

        then: "Flow is created on longPath because of flow strategy"
        pathHelper.convert(northbound.getFlowPath(flow.flowId)) == longPath

        cleanup:
        flow && flowHelperV2.deleteFlow(flow.flowId)
        broughtDownIsls.every { antiflap.portUp(it.srcSwitch.dpId, it.srcPort) }
        wait(discoveryInterval + WAIT_OFFSET) {
            assert northbound.getActiveLinks().size() == topology.islsForActiveSwitches.size() * 2
        }
        northbound.deleteLinkProps(northbound.getAllLinkProps())
        database.resetCosts()
    }
}
