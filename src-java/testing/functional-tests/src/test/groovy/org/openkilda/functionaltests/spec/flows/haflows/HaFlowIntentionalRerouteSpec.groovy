package org.openkilda.functionaltests.spec.flows.haflows

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.HA_FLOW
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_PROPS_DB_RESET
import static org.openkilda.messaging.payload.flow.FlowEncapsulationType.*
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.HaFlowAllEntityPaths
import org.openkilda.functionaltests.helpers.model.HaFlowExtended
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.northbound.dto.v2.haflows.HaFlowRerouteResult
import org.openkilda.testing.model.topology.TopologyDefinition.Isl

import spock.lang.Narrative

@Narrative("Verify that on-demand HA-Flow reroute operations are performed accurately.")
@Tags([HA_FLOW])
class HaFlowIntentionalRerouteSpec extends HealthCheckSpecification {

    @Tags(ISL_PROPS_DB_RESET)
    def "Not able to reroute to a path with not enough bandwidth available"() {
        given: "An HA-Flow with alternate paths available"
        def swT = topologyHelper.findSwitchTripletWithAlternativePaths()
        assumeTrue(swT != null, "No suiting switches found")
        def haFlow = HaFlowExtended.build(swT, northboundV2, topology).withBandwidth(10000).create()

        def initialPath = haFlow.retrievedAllEntityPaths()
        def involvedIsls = initialPath.getInvolvedIsls(true)

        when: "Make the current path less preferable than alternatives"
        pathHelper.updateIslsCost(involvedIsls, PathHelper.NOT_PREFERABLE_COST * 3)

        and: "Make all alternative paths to have not enough bandwidth to handle the HA-Flow"
        def alternativePaths = getAlternativesPaths(initialPath, (swT.pathsEp1 + swT.pathsEp2))
        def changedIsls = setBandwidthForAlternativesPaths(involvedIsls, alternativePaths, haFlow.maximumBandwidth - 1)

        and: "Init a reroute to a more preferable path"
        def rerouteResponse = haFlow.reroute()

        then: "The HA-Flow is NOT rerouted because of not enough bandwidth on alternative paths"
        !rerouteResponse.rerouted

        // haFlow.waitForBeingInState(FlowState.UP) should replace below line after fixing a defect https://github.com/telstra/open-kilda/issues/5547
        Wrappers.wait(WAIT_OFFSET) { assert northboundV2.getHaFlow(haFlow.haFlowId).status == FlowState.UP.toString() }

        assertRerouteResponsePaths(initialPath, rerouteResponse)

        haFlow.retrievedAllEntityPaths() == initialPath
        and: "And involved switches pass validation"
        def mainPathInvolvedSwitches = involvedIsls.collect { [it.srcSwitch.dpId, it.dstSwitch.dpId] }.flatten().unique()
        switchHelper.synchronizeAndCollectFixedDiscrepancies(mainPathInvolvedSwitches).isEmpty()

        and: "HA-Flow pass validation"
        haFlow.validate().asExpected

        cleanup: "Remove the HA-Flow, restore the bandwidth on ISLs, reset costs"
        haFlow && haFlow.delete()
        withPool {
            changedIsls.eachParallel {
                database.resetIslBandwidth(it)
                database.resetIslBandwidth(it.reversed)
            }
        }
    }

    @Tags(ISL_PROPS_DB_RESET)
    def "Able to reroute to a better path if it has enough bandwidth"() {
        given: "An HA-Flow with alternate paths available"
        def swT = topologyHelper.findSwitchTripletWithAlternativePaths()
        assumeTrue(swT != null, "No suiting switches found")
        def haFlow = HaFlowExtended.build(swT, northboundV2, topology)
                .withEncapsulationType(TRANSIT_VLAN).withBandwidth(10000).create()

        def initialPath = haFlow.retrievedAllEntityPaths()
        def initialPathNodesView = initialPath.subFlowPaths.collect { it.path.forward.nodes.toPathNode() }
        def initialInvolvedIsls = initialPath.getInvolvedIsls(true)
        String ep1FlowId = haFlow.subFlows.find { it.endpoint.switchId == swT.ep1.dpId }.flowId
        String ep2FlowId = haFlow.subFlows.find { it.endpoint.switchId == swT.ep2.dpId }.flowId
        def initialInvolvedSwitchIds = initialInvolvedIsls.collect { [it.srcSwitch.dpId, it.dstSwitch.dpId] }.flatten().unique()

        when: "Make one of the alternative paths to be the most preferable among all others"
        def preferableAltPathForSubFlow1 = swT.pathsEp1.find {!initialPathNodesView.contains(it)}
        def preferableAltPathForSubFlow2 = swT.pathsEp2.find {!initialPathNodesView.contains(it)}

        withPool {
            swT.pathsEp1.findAll { it != preferableAltPathForSubFlow1 }.eachParallel {
                pathHelper.makePathMorePreferable(preferableAltPathForSubFlow1, it)
            }
            swT.pathsEp2.findAll { it != preferableAltPathForSubFlow2 }.eachParallel {
                pathHelper.makePathMorePreferable(preferableAltPathForSubFlow2, it)
            }
        }

        and: "Make the future path to have exact bandwidth to handle the HA-Flow"
        def thinIsl = setBandwidthForAlternativesPaths(initialInvolvedIsls, [preferableAltPathForSubFlow1, preferableAltPathForSubFlow2], haFlow.maximumBandwidth)

        and: "Init a reroute of the HA-Flow"
        def rerouteResponse = haFlow.reroute()
        haFlow.waitForBeingInState(FlowState.UP)

        then: "The HA-Flow is successfully rerouted and goes through the preferable path"
        rerouteResponse.rerouted
        def haFlowPathAfterReroute = haFlow.retrievedAllEntityPaths()
        def actualIslsAfterReroute = haFlowPathAfterReroute.getInvolvedIsls(true)

        assertRerouteResponsePaths(haFlowPathAfterReroute, rerouteResponse)

        assert haFlowPathAfterReroute.subFlowPaths.find {it.flowId == ep1FlowId }
                .path.forward.nodes.toPathNode() == preferableAltPathForSubFlow1

        assert haFlowPathAfterReroute.subFlowPaths.find {it.flowId == ep2FlowId }
                .path.forward.nodes.toPathNode() == preferableAltPathForSubFlow2

        actualIslsAfterReroute.containsAll(thinIsl)

        and: "And involved switches pass validation"
        def allInvolvedSwitchIds = initialInvolvedSwitchIds + actualIslsAfterReroute.collect { [it.srcSwitch.dpId, it.dstSwitch.dpId] }.flatten().unique()
        switchHelper.synchronizeAndCollectFixedDiscrepancies(allInvolvedSwitchIds).isEmpty()

        and: "HA-Flow pass validation"
        haFlow.validate().asExpected

        and: "'Thin' ISL has 0 available bandwidth left"
        Wrappers.wait(WAIT_OFFSET) {
            thinIsl.each { assert islUtils.getIslInfo(it).get().availableBandwidth == 0 }
        }

        cleanup: "Remove the HA-Flow, restore bandwidths on ISLs, reset costs"
        haFlow && haFlow.delete()
        withPool {
            thinIsl.eachParallel {
                database.resetIslBandwidth(it)
                database.resetIslBandwidth(it.reversed)
            }
        }
    }

    @Tags(ISL_PROPS_DB_RESET)
    def "Able to reroute to a path with not enough bandwidth available in case ignoreBandwidth=true"() {
        given: "A HA-Flow with alternate paths available"
        def swT = topologyHelper.findSwitchTripletWithAlternativePaths()
        assumeTrue(swT != null, "No suiting switches found")
        def haFlow = HaFlowExtended.build(swT, northboundV2, topology).withEncapsulationType(TRANSIT_VLAN)
                .withBandwidth(10000).withIgnoreBandwidth(true).create()

        def initialPath = haFlow.retrievedAllEntityPaths()
        def initialPathNodesView = initialPath.subFlowPaths.collect { it.path.forward.nodes.toPathNode() }
        def initialInvolvedIsls = initialPath.getInvolvedIsls(true)
        def initialInvolvedSwitchIds = initialInvolvedIsls.collect { [it.srcSwitch.dpId, it.dstSwitch.dpId] }.flatten().unique()

        when: "Make the current path less preferable than alternatives"
        def alternativePaths = getAlternativesPaths(initialPath, (swT.pathsEp1 + swT.pathsEp2))
        pathHelper.makePathNotPreferable(initialPathNodesView.flatten())

        and: "Make all alternative paths to have not enough bandwidth to handle the HA-Flow"
        def newBw = haFlow.maximumBandwidth - 1
        def changedIsls = setBandwidthForAlternativesPaths(initialInvolvedIsls, alternativePaths, newBw)

        and: "Init a reroute to a more preferable path"
        def rerouteResponse = haFlow.reroute()

        then: "The HA-Flow is rerouted because ignoreBandwidth=true"
        rerouteResponse.rerouted

        initialPath.subFlowPaths.size() == rerouteResponse.subFlowPaths.size()
        initialPath.subFlowPaths.each {subFlow ->
            def rerouteSubFlowPath =  rerouteResponse.subFlowPaths.find { it.flowId == subFlow.flowId}.nodes
            assert subFlow.path.forward.nodes.toPathNodeV2() != PathHelper.setNullLatency(rerouteSubFlowPath)
        }
        haFlow.waitForBeingInState(FlowState.UP)

        def haFlowPathAfterReroute = haFlow.retrievedAllEntityPaths()
        def actualIslsAfterReroute = haFlowPathAfterReroute.getInvolvedIsls(true)

        assertRerouteResponsePaths(haFlowPathAfterReroute, rerouteResponse)
        haFlowPathAfterReroute != initialPath

        and: "And involved switches pass validation"
        def allInvolvedSwitchIds = initialInvolvedSwitchIds + actualIslsAfterReroute.collect { [it.srcSwitch.dpId, it.dstSwitch.dpId] }.flatten()
        switchHelper.synchronizeAndCollectFixedDiscrepancies(allInvolvedSwitchIds).isEmpty()

        and: "HA-Flow pass validation"
        haFlow.validate().asExpected

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
        haFlow && haFlow.delete()
        withPool {
            changedIsls.eachParallel {
                database.resetIslBandwidth(it)
                database.resetIslBandwidth(it.reversed)
            }
        }
    }

    private getAlternativesPaths(HaFlowAllEntityPaths actualPathNodes, List<List<PathNode>> existingPaths) {
        existingPaths.findAll {
            !actualPathNodes.subFlowPaths.collect { it.path.forward.nodes.toPathNode() }.contains(it)
        }
    }

    private void assertRerouteResponsePaths(HaFlowAllEntityPaths haFlowPath, HaFlowRerouteResult rerouteResponse) {
        assert haFlowPath.subFlowPaths.size() == rerouteResponse.subFlowPaths.size()
        haFlowPath.subFlowPaths.each {subFlow ->
            def rerouteSubFlowPath =  rerouteResponse.subFlowPaths.find { it.flowId == subFlow.flowId}.nodes
            assert subFlow.path.forward.nodes.toPathNodeV2() == PathHelper.setNullLatency(rerouteSubFlowPath)
        }
    }

    private Collection<Isl> setBandwidthForAlternativesPaths(currentIsls, alternativePaths, long newBandwidth) {
        //collecting only the first Isl from each alternative path
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
