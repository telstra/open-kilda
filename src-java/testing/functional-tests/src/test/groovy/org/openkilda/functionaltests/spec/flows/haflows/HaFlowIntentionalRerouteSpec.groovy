package org.openkilda.functionaltests.spec.flows.haflows

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.HA_FLOW
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_PROPS_DB_RESET
import static org.openkilda.functionaltests.helpers.model.FlowEncapsulationType.TRANSIT_VLAN
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.HaFlowFactory
import org.openkilda.functionaltests.helpers.IslHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.FlowWithSubFlowsEntityPath
import org.openkilda.functionaltests.helpers.model.SwitchTriplet
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.northbound.dto.v2.flows.FlowPathV2.PathNodeV2
import org.openkilda.northbound.dto.v2.haflows.HaFlowRerouteResult
import org.openkilda.testing.model.topology.TopologyDefinition.Isl

import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.Shared

@Narrative("Verify that on-demand HA-Flow reroute operations are performed accurately.")
@Tags([HA_FLOW])
class HaFlowIntentionalRerouteSpec extends HealthCheckSpecification {

    @Shared
    @Autowired
    HaFlowFactory haFlowFactory

    @Tags(ISL_PROPS_DB_RESET)
    def "Not able to reroute to a path with not enough bandwidth available"() {
        given: "An HA-Flow with alternate paths available"
        def swT = switchTriplets.all().findSwitchTripletWithAlternativePaths()
        assumeTrue(swT != null, "No suiting switches found")
        def haFlow = haFlowFactory.getBuilder(swT).withBandwidth(10000)
                .build().create()

        def initialPath = haFlow.retrievedAllEntityPaths()
        def involvedIsls = initialPath.getInvolvedIsls()

        when: "Make the current path less preferable than alternatives"
        islHelper.updateIslsCost(involvedIsls, IslHelper.NOT_PREFERABLE_COST * 3)

        and: "Make all alternative paths to have not enough bandwidth to handle the HA-Flow"
        def alternativePaths = getAlternativesPaths(initialPath, swT)
        setBandwidthForAlternativesPaths(involvedIsls, alternativePaths, haFlow.maximumBandwidth - 1)

        and: "Init a reroute to a more preferable path"
        def rerouteResponse = haFlow.reroute()

        then: "The HA-Flow is NOT rerouted because of not enough bandwidth on alternative paths"
        !rerouteResponse.rerouted

        // haFlow.waitForBeingInState(FlowState.UP) should replace below line after fixing a defect https://github.com/telstra/open-kilda/issues/5547
        Wrappers.wait(WAIT_OFFSET) { assert northboundV2.getHaFlow(haFlow.haFlowId).status == FlowState.UP.toString() }

        assertRerouteResponsePaths(initialPath, rerouteResponse)
        haFlow.retrievedAllEntityPaths() == initialPath

        and: "And involved switches pass validation"
        def mainPathInvolvedSwitches = initialPath.getInvolvedSwitches()
        switchHelper.synchronizeAndCollectFixedDiscrepancies(mainPathInvolvedSwitches).isEmpty()

        and: "HA-Flow pass validation"
        haFlow.validate().asExpected
    }

    @Tags(ISL_PROPS_DB_RESET)
    def "Able to reroute to a better path if it has enough bandwidth"() {
        given: "An HA-Flow with alternate paths available"
        def swT = switchTriplets.all().findSwitchTripletWithAlternativePaths()
        assumeTrue(swT != null, "No suiting switches found")
        def haFlow = haFlowFactory.getBuilder(swT).withEncapsulationType(TRANSIT_VLAN)
                .withBandwidth(10000).build().create()

        def initialPath = haFlow.retrievedAllEntityPaths()
        String ep1FlowId = haFlow.subFlows.find { it.endpointSwitchId == swT.ep1.dpId }.haSubFlowId
        String ep2FlowId = haFlow.subFlows.find { it.endpointSwitchId == swT.ep2.dpId }.haSubFlowId
        List<Isl> initialIslsSubFlow1 = initialPath.getSubFlowIsls(ep1FlowId)
        List<Isl> initialIslsSubFlow2 = initialPath.getSubFlowIsls(ep2FlowId)

        def initialFlowIsls = (initialIslsSubFlow1 + initialIslsSubFlow2).unique()
        def initialInvolvedSwitchIds = initialPath.getInvolvedSwitches()

        when: "Make one of the alternative paths to be the most preferable among all others"
        def availablePathsIslsEp1 =  swT.retrieveAvailablePathsEp1().collect { it.getInvolvedIsls() }
        def availablePathsIslsEp2 = swT.retrieveAvailablePathsEp2().collect { it.getInvolvedIsls() }
        def preferableAltPathForSubFlow1 = availablePathsIslsEp1.find { it != initialIslsSubFlow1 }
        def preferableAltPathForSubFlow2 = availablePathsIslsEp2.find { it != initialIslsSubFlow2 }

        withPool {
            availablePathsIslsEp1.findAll { it != preferableAltPathForSubFlow1 }.eachParallel {
                islHelper.makePathIslsMorePreferable(preferableAltPathForSubFlow1, it)
            }
            availablePathsIslsEp2.findAll { it != preferableAltPathForSubFlow2 }.eachParallel {
                islHelper.makePathIslsMorePreferable(preferableAltPathForSubFlow2, it)
            }
        }

        and: "Make the future path to have exact bandwidth to handle the HA-Flow"
        def thinIsl = setBandwidthForAlternativesPaths(initialFlowIsls,
                [preferableAltPathForSubFlow1 + preferableAltPathForSubFlow2], haFlow.maximumBandwidth)

        and: "Init a reroute of the HA-Flow"
        def rerouteResponse = haFlow.reroute()
        haFlow.waitForBeingInState(FlowState.UP)

        then: "The HA-Flow is successfully rerouted and goes through the preferable path"
        rerouteResponse.rerouted
        def haFlowPathAfterReroute = haFlow.retrievedAllEntityPaths()
        def actualFlowIslsAfterReroute = haFlowPathAfterReroute.getInvolvedIsls()

        assertRerouteResponsePaths(haFlowPathAfterReroute, rerouteResponse)

        assert haFlowPathAfterReroute.getSubFlowIsls(ep1FlowId) == preferableAltPathForSubFlow1
        assert haFlowPathAfterReroute.getSubFlowIsls(ep2FlowId) == preferableAltPathForSubFlow2
        actualFlowIslsAfterReroute.containsAll(thinIsl)

        and: "And involved switches pass validation"
        def allInvolvedSwitchIds = (initialInvolvedSwitchIds + haFlowPathAfterReroute.getInvolvedSwitches()).unique()
        switchHelper.synchronizeAndCollectFixedDiscrepancies(allInvolvedSwitchIds).isEmpty()

        and: "HA-Flow pass validation"
        haFlow.validate().asExpected

        and: "'Thin' ISL has 0 available bandwidth left"
        Wrappers.wait(WAIT_OFFSET) {
            thinIsl.each { assert islUtils.getIslInfo(it).get().availableBandwidth == 0 }
        }
    }

    @Tags(ISL_PROPS_DB_RESET)
    def "Able to reroute to a path with not enough bandwidth available in case ignoreBandwidth=true"() {
        given: "an HA-Flow with alternate paths available"
        def swT = switchTriplets.all().findSwitchTripletWithAlternativePaths()
        assumeTrue(swT != null, "No suiting switches found")
        def haFlow = haFlowFactory.getBuilder(swT).withEncapsulationType(TRANSIT_VLAN)
                .withBandwidth(10000).withIgnoreBandwidth(true)
                .build().create()

        def initialPath = haFlow.retrievedAllEntityPaths()
        def initialInvolvedIsls = initialPath.getInvolvedIsls()
        def initialInvolvedSwitchIds = initialPath.getInvolvedSwitches()

        when: "Make the current path less preferable than alternatives"
        def alternativePaths = getAlternativesPaths(initialPath, swT)
        islHelper.updateIslsCost(initialInvolvedIsls, IslHelper.NOT_PREFERABLE_COST * 3)

        and: "Make all alternative paths to have not enough bandwidth to handle the HA-Flow"
        def newBw = haFlow.maximumBandwidth - 1
        def changedIsls = setBandwidthForAlternativesPaths(initialInvolvedIsls, alternativePaths, newBw)

        and: "Init a reroute to a more preferable path"
        def rerouteResponse = haFlow.reroute()

        then: "The HA-Flow is rerouted because ignoreBandwidth=true"
        rerouteResponse.rerouted

        initialPath.subFlowPaths.size() == rerouteResponse.subFlowPaths.size()
        initialPath.subFlowPaths.each {subFlow ->
            def rerouteSubFlowPath =  getSubFlowRerouteNodesResponse(rerouteResponse, subFlow.flowId)
            def subFlowNodes = subFlow.path.forward.nodes.toPathNodeV2()
            assert subFlowNodes != rerouteSubFlowPath
        }
        haFlow.waitForBeingInState(FlowState.UP)

        def haFlowPathAfterReroute = haFlow.retrievedAllEntityPaths()

        assertRerouteResponsePaths(haFlowPathAfterReroute, rerouteResponse)
        haFlowPathAfterReroute != initialPath

        and: "And involved switches pass validation"
        def allInvolvedSwitchIds = (initialInvolvedSwitchIds + haFlowPathAfterReroute.getInvolvedSwitches()).unique()
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
    }

    private List<List<Isl>> getAlternativesPaths(FlowWithSubFlowsEntityPath haFlowPath, SwitchTriplet swT) {
        List<List<Isl>> subFlowsPathIsls = haFlowPath.subFlowPaths.collect { it.getInvolvedIsls() }
        (swT.retrieveAvailablePathsEp1() + swT.retrieveAvailablePathsEp2()).collect { it.getInvolvedIsls() }.findAll{
            //HA-Flow has 2 sub-flows
            it != subFlowsPathIsls.first() && it != subFlowsPathIsls.last()
        }
    }

    private void assertRerouteResponsePaths(FlowWithSubFlowsEntityPath haFlowPath, HaFlowRerouteResult rerouteResponse) {
        assert haFlowPath.subFlowPaths.size() == rerouteResponse.subFlowPaths.size()
        haFlowPath.subFlowPaths.each {subFlow ->
            def rerouteSubFlowPath =  getSubFlowRerouteNodesResponse(rerouteResponse, subFlow.flowId)
            def subFlowNodes = subFlow.path.forward.nodes.toPathNodeV2()
            assert subFlowNodes == rerouteSubFlowPath
        }
    }

    private Collection<Isl> setBandwidthForAlternativesPaths(List<Isl> flowIsls, List<List<Isl>> alternativePaths, long newBandwidth) {
        Set<Isl> changedIsls = alternativePaths.flatten().unique().findAll { !flowIsls.contains(it) && !flowIsls.contains(it.reversed) }
        islHelper.setAvailableAndMaxBandwidth(changedIsls.collectMany {[it, it.reversed]}, newBandwidth)
        changedIsls
    }

    private List<PathNodeV2> getSubFlowRerouteNodesResponse(HaFlowRerouteResult rerouteResult, String subFlowId) {
        rerouteResult.subFlowPaths.find { it.flowId == subFlowId}.nodes
                .collect { PathNodeV2.builder().switchId(it.switchId).portNo(it.portNo).segmentLatency(null).build() }
    }
}
