package org.openkilda.functionaltests.spec.network

import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.ResourceLockConstants
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.model.system.KildaConfigurationDto
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.PathComputationStrategy
import org.openkilda.northbound.dto.v1.flows.FlowPatchDto
import org.openkilda.testing.model.topology.TopologyDefinition.Isl

import org.junit.jupiter.api.parallel.ResourceLock

@ResourceLock(ResourceLockConstants.DEFAULT_PATH_COMPUTATION)
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
        northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))
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
        northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))
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
}
