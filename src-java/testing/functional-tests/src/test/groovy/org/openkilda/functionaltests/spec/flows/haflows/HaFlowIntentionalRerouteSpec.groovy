package org.openkilda.functionaltests.spec.flows.haflows

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_PROPS_DB_RESET
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.HaFlowHelper
import org.openkilda.functionaltests.helpers.HaPathHelper
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.FlowEncapsulationType
import org.openkilda.northbound.dto.v2.haflows.HaFlowPaths
import org.openkilda.northbound.dto.v2.haflows.HaFlowRerouteResult
import org.openkilda.northbound.dto.v2.haflows.HaSubFlow
import org.openkilda.northbound.dto.v2.haflows.HaSubFlowPath
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.Shared

@Narrative("Verify that on-demand HA-haFlow reroute operations are performed accurately.")
class HaFlowIntentionalRerouteSpec extends HealthCheckSpecification {
    @Autowired
    @Shared
    HaFlowHelper haFlowHelper
    @Autowired
    @Shared
    HaPathHelper haPathHelper

    @Tags(ISL_PROPS_DB_RESET)
    def "Not able to reroute to a path with not enough bandwidth available"() {
        given: "An HA-flow with alternate paths available"
        def swT = topologyHelper.findSwitchTripletWithAlternativePaths()
        assumeTrue(swT != null, "No suiting switches found")
        def haFlow = haFlowHelper.randomHaFlow(swT)
        haFlow.maximumBandwidth = 10000
        haFlowHelper.addHaFlow(haFlow)

        def currentPathResponse = northboundV2.getHaFlowPaths(haFlow.haFlowId)
        def currentPaths = currentPathResponse.subFlowPaths.collect {PathHelper.convert(it.forward)}

        when: "Make the current path less preferable than alternatives"
        def alternativePaths = (swT.pathsEp1 + swT.pathsEp2).findAll { !currentPaths.contains(it) }
        def completeCurrentPath = currentPaths.collectMany {it}
        pathHelper.makePathNotPreferable(completeCurrentPath)

        and: "Make all alternative paths to have not enough bandwidth to handle the haFlow"
        def currentIsls = pathHelper.getInvolvedIsls(completeCurrentPath)
        def changedIsls = setBandwidthForAlternativesPaths(currentIsls, alternativePaths, haFlow.maximumBandwidth - 1)


        and: "Init a reroute to a more preferable path"
        def rerouteResponse = haFlowHelper.rerouteHaFlow(haFlow.haFlowId)

        then: "The HA-flow is NOT rerouted because of not enough bandwidth on alternative paths"
        Wrappers.wait(WAIT_OFFSET) { assert haFlowHelper.getHaFlowStatus(haFlow.haFlowId) == FlowState.UP.toString() }
        !rerouteResponse.rerouted

        assertRerouteResponsePaths(currentPathResponse, rerouteResponse)

        northboundV2.getHaFlowPaths(haFlow.haFlowId) == currentPathResponse

        and: "And involved switches pass validation"
        switchHelper.synchronizeAndCollectFixedDiscrepancies(haFlowHelper.getInvolvedSwitches(currentPathResponse)).isEmpty()

        and: "HA-flow pass validation"
        northboundV2.validateHaFlow(haFlow.getHaFlowId()).asExpected

        cleanup: "Remove the HA-flow, restore the bandwidth on ISLs, reset costs"
        haFlow && haFlowHelper.deleteHaFlow(haFlow.haFlowId)
        withPool {
            changedIsls.eachParallel {
                database.resetIslBandwidth(it)
                database.resetIslBandwidth(it.reversed)
            }
        }
    }

    @Tags(ISL_PROPS_DB_RESET)
    def "Able to reroute to a better path if it has enough bandwidth"() {
        given: "An HA-flow with alternate paths available"
        def swT = topologyHelper.findSwitchTripletWithAlternativePaths()
        assumeTrue(swT != null, "No suiting switches found")
        def haFlow = haFlowHelper.randomHaFlow(swT)
        haFlow.maximumBandwidth = 10000
        haFlow.encapsulationType = FlowEncapsulationType.TRANSIT_VLAN
        def createResponse = haFlowHelper.addHaFlow(haFlow)

        def currentPathDto = northboundV2.getHaFlowPaths(haFlow.haFlowId)
        def currentPaths = currentPathDto.subFlowPaths.collect {PathHelper.convert(it.forward)}
        def oldInvolvedSwitchIds = haFlowHelper.getInvolvedSwitches(currentPathDto)

        when: "Make one of the alternative paths to be the most preferable among all others"
        def preferableAltSubPath1 = swT.pathsEp1.find {!currentPaths.contains(it)}
        def preferableAltSubPath2 = swT.pathsEp2.find {!currentPaths.contains(it)}
        withPool {
            swT.pathsEp1.findAll { it != preferableAltSubPath1 }.eachParallel {
                pathHelper.makePathMorePreferable(preferableAltSubPath1, it)
            }
            swT.pathsEp2.findAll { it != preferableAltSubPath2 }.eachParallel {
                pathHelper.makePathMorePreferable(preferableAltSubPath2, it)
            }
        }

        and: "Make the future path to have exact bandwidth to handle the HA-flow"
        def unitedAltPath = [preferableAltSubPath1, preferableAltSubPath2].collectMany { it }
        def currentIsls = haPathHelper.getInvolvedIsls(currentPathDto)
        def thinIsl = pathHelper.getInvolvedIsls(unitedAltPath).find {
            !currentIsls.contains(it) && !currentIsls.contains(it.reversed)
        }
        withPool {
            [thinIsl, thinIsl.reversed].eachParallel {
                database.updateIslMaxBandwidth(it, haFlow.maximumBandwidth)
                database.updateIslAvailableBandwidth(it, haFlow.maximumBandwidth)
            }
        }

        and: "Init a reroute of the HA-flow"
        def rerouteResponse = haFlowHelper.rerouteHaFlow(haFlow.haFlowId)
        Wrappers.wait(WAIT_OFFSET) { assert haFlowHelper.getHaFlowStatus(haFlow.haFlowId) == FlowState.UP.toString() }

        then: "The HA-flow is successfully rerouted and goes through the preferable path"
        def getPathsResponse = northboundV2.getHaFlowPaths(haFlow.haFlowId)

        rerouteResponse.rerouted
        assertRerouteResponsePaths(getPathsResponse, rerouteResponse)

        assertSubPath(createResponse.subFlows, swT.ep1, preferableAltSubPath1, getPathsResponse.subFlowPaths)
        assertSubPath(createResponse.subFlows, swT.ep2, preferableAltSubPath2, getPathsResponse.subFlowPaths)

        def unitedNewPath = getPathsResponse.subFlowPaths.collectMany {PathHelper.convert(it.forward)}
        pathHelper.getInvolvedIsls(unitedNewPath).contains(thinIsl)

        and: "And involved switches pass validation"
        def allInvolvedSwitchIds = oldInvolvedSwitchIds + haFlowHelper.getInvolvedSwitches(getPathsResponse)
        switchHelper.synchronizeAndCollectFixedDiscrepancies(allInvolvedSwitchIds).isEmpty()

        and: "HA-flow pass validation"
        northboundV2.validateHaFlow(haFlow.getHaFlowId()).asExpected

        and: "'Thin' ISL has 0 available bandwidth left"
        Wrappers.wait(WAIT_OFFSET) { assert islUtils.getIslInfo(thinIsl).get().availableBandwidth == 0 }

        cleanup: "Remove the HA-flow, restore bandwidths on ISLs, reset costs"
        haFlow && haFlowHelper.deleteHaFlow(haFlow.haFlowId)
        thinIsl && [thinIsl, thinIsl.reversed].each { database.resetIslBandwidth(it) }
    }

    @Tags(ISL_PROPS_DB_RESET)
    def "Able to reroute to a path with not enough bandwidth available in case ignoreBandwidth=true"() {
        given: "A HA-flow with alternate paths available"
        def swT = topologyHelper.findSwitchTripletWithAlternativePaths()
        assumeTrue(swT != null, "No suiting switches found")
        def haFlow = haFlowHelper.randomHaFlow(swT)
        haFlow.maximumBandwidth = 10000
        haFlow.ignoreBandwidth = true
        haFlow.encapsulationType = FlowEncapsulationType.TRANSIT_VLAN
        haFlowHelper.addHaFlow(haFlow)
        def currentPathResponse = northboundV2.getHaFlowPaths(haFlow.haFlowId)
        def currentPaths = currentPathResponse.subFlowPaths.collect {PathHelper.convert(it.forward)}
        def oldInvolvedSwitchIds = haFlowHelper.getInvolvedSwitches(currentPathResponse)

        when: "Make the current path less preferable than alternatives"
        def alternativePaths = (swT.pathsEp1 + swT.pathsEp2).findAll { !currentPaths.contains(it) }
        def completeCurrentPath = currentPaths.collectMany {it}
        pathHelper.makePathNotPreferable(completeCurrentPath)

        and: "Make all alternative paths to have not enough bandwidth to handle the HA-flow"
        def newBw = haFlow.maximumBandwidth - 1
        def currentIsls = pathHelper.getInvolvedIsls(completeCurrentPath)
        def changedIsls = setBandwidthForAlternativesPaths(currentIsls, alternativePaths, newBw)

        and: "Init a reroute to a more preferable path"
        def rerouteResponse = haFlowHelper.rerouteHaFlow(haFlow.haFlowId)

        then: "The HA-flow is rerouted because ignoreBandwidth=true"
        rerouteResponse.rerouted
        currentPathResponse.subFlowPaths.size() == rerouteResponse.subFlowPaths.size()
        for (HaSubFlowPath path : currentPathResponse.subFlowPaths) {
            def reroutedPath = rerouteResponse.subFlowPaths.find { it.flowId == path.flowId }
            assert PathHelper.toNodesV2(path.getForward()) != PathHelper.setNullLatency(reroutedPath.nodes)
        }

        Wrappers.wait(WAIT_OFFSET) { assert haFlowHelper.getHaFlowStatus(haFlow.haFlowId) == FlowState.UP.toString() }

        def updatedPaths = northboundV2.getHaFlowPaths(haFlow.haFlowId)
        updatedPaths.subFlowPaths.toSorted { it.flowId }.forward
                != currentPathResponse.subFlowPaths.toSorted {it.flowId}.forward

        and: "And involved switches pass validation"
        def allInvolvedSwitchIds = oldInvolvedSwitchIds + haFlowHelper.getInvolvedSwitches(updatedPaths)
        switchHelper.synchronizeAndCollectFixedDiscrepancies(allInvolvedSwitchIds).isEmpty()

        and: "HA-flow pass validation"
        northboundV2.validateHaFlow(haFlow.getHaFlowId()).asExpected

        and: "Available bandwidth was not changed while rerouting due to ignoreBandwidth=true"
        def allLinks = northbound.getAllLinks()
        withPool {
            changedIsls.eachParallel {
                islUtils.getIslInfo(allLinks, it).each {
                    assert it.get().availableBandwidth == newBw
                }
            }
        }

        cleanup: "Remove the HA-flow, restore the bandwidth on ISLs, reset costs"
        haFlow && haFlowHelper.deleteHaFlow(haFlow.haFlowId)
        withPool {
            changedIsls.eachParallel {
                database.resetIslBandwidth(it)
                database.resetIslBandwidth(it.reversed)
            }
        }
    }

    private void assertRerouteResponsePaths(HaFlowPaths getPathsResponse, HaFlowRerouteResult rerouteResponse) {
        getPathsResponse.subFlowPaths.size() == rerouteResponse.subFlowPaths.size()
        for (HaSubFlowPath path : getPathsResponse.subFlowPaths) {
            def reroutedPath = rerouteResponse.subFlowPaths.find { it.flowId == path.flowId }
            assert PathHelper.toNodesV2(path.getForward()) == PathHelper.setNullLatency(reroutedPath.nodes)
        }
    }

    private void assertSubPath(
            List<HaSubFlow> subFlows, Switch endpointSwitch, List<PathNode> expectedPath,  List<HaSubFlowPath> actualPaths) {
        def subFlowId = subFlows.find { it.endpoint.switchId == endpointSwitch.dpId }.flowId
        def actualPath = actualPaths.find { it.flowId == subFlowId }
        assert PathHelper.convert(actualPath.forward) == expectedPath
    }

    private Collection<Isl> setBandwidthForAlternativesPaths(currentIsls, alternativePaths, long newBandwidth) {
        Set<Isl> changedIsls = withPool {
            alternativePaths.collectParallel { List<PathNode> altPath ->
                pathHelper.getInvolvedIsls(altPath).find {
                    !currentIsls.contains(it) && !currentIsls.contains(it.reversed)
                }
            }
        } as Set

        withPool {
            changedIsls.eachParallel { Isl thinIsl ->
                [thinIsl, thinIsl.reversed].each {
                    database.updateIslMaxBandwidth(it, newBandwidth)
                    database.updateIslAvailableBandwidth(it, newBandwidth)
                }
            }
        }
        changedIsls
    }

    def cleanup() {
        northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))
        database.resetCosts(topology.isls)
    }
}
