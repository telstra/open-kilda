package org.openkilda.functionaltests.spec.flows.haflows

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.HA_FLOW
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_PROPS_DB_RESET
import static org.openkilda.functionaltests.helpers.model.FlowEncapsulationType.TRANSIT_VLAN
import static org.openkilda.functionaltests.helpers.model.Switches.synchronizeAndCollectFixedDiscrepancies
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.factory.HaFlowFactory
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.IslExtended
import org.openkilda.functionaltests.helpers.model.Path
import org.openkilda.messaging.payload.flow.FlowState

import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative
import spock.lang.Shared

@Narrative("Verify that on-demand HA-Flow reroute operations are performed accurately.")
@Tags([HA_FLOW])
class HaFlowIntentionalRerouteSpec extends HealthCheckSpecification {

    static final Integer NOT_PREFERABLE_COST = 99999999

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
        def involvedIsls = isls.all().findInPath(initialPath)

        when: "Make the current path less preferable than alternatives"
        involvedIsls.each { it.updateCost(NOT_PREFERABLE_COST * 3)}

        and: "Make all alternative paths to have not enough bandwidth to handle the HA-Flow"
        isls.all().excludeIsls(involvedIsls).updateIslsAvailableAndMaxBandwidthInDb(haFlow.maximumBandwidth - 1)

        and: "Init a reroute to a more preferable path"
        def rerouteResponse = haFlow.reroute()

        then: "The HA-Flow is NOT rerouted because of not enough bandwidth on alternative paths"
        !rerouteResponse.rerouted

        // haFlow.waitForBeingInState(FlowState.UP) should replace below line after fixing a defect https://github.com/telstra/open-kilda/issues/5547
        Wrappers.wait(WAIT_OFFSET) { assert haFlow.retrieveDetails().status == FlowState.UP }

        initialPath.subFlowPaths.each { subFlowPath ->
            def rerouteNodes = rerouteResponse.subFlowPaths.find { it.flowId == subFlowPath.flowId }.nodes
            def actualNodes =  subFlowPath.path.forward.nodes.toPathNodeV2()
            //verify nodes from reroute response
            assert rerouteNodes == actualNodes
        }

        haFlow.retrievedAllEntityPaths() == initialPath

        and: "And involved switches pass validation"
        def mainPathInvolvedSwitches = switches.all().findSwitchesInPath(initialPath)
        synchronizeAndCollectFixedDiscrepancies(mainPathInvolvedSwitches).isEmpty()

        and: "HA-Flow pass validation"
        haFlow.validate().asExpected
    }

    @Tags(ISL_PROPS_DB_RESET)
    def "Able to reroute to a better path if it has enough bandwidth"() {
        given: "An HA-Flow with alternate paths available"
        def swT = switchTriplets.all().withAllDifferentEndpoints().withSharedEpEp1Ep2InChain().switchTriplets.find {
            // shared-ep1 should have 2 direct paths(one is used during flow creation, another will be changed to become preferable)
            it.pathsEp1.findAll { it.size() == 2 }.size() >= 2
        }
        assumeTrue(swT != null, "No suiting switches found")
        def haFlow = haFlowFactory.getBuilder(swT).withEncapsulationType(TRANSIT_VLAN)
                .withBandwidth(10000).build().create()

        def initialPath = haFlow.retrievedAllEntityPaths()
        String ep1FlowId = haFlow.subFlows.find { it.endpointSwitchId == swT.ep1.switchId }.haSubFlowId

        def initialFlowIsls = isls.all().findInPath(initialPath)
        def ep1FlowPathNodes = initialPath.getSubFlowMainPath(ep1FlowId).nodes.toPathNodeV2()

        when: "Make one of the alternative paths to be the most preferable among all others"
        Path preferableAltPathForSubFlow1, preferableAltPathForSubFlow2
        // 1 ISL (direct path) is 2 nodes
        List<Path> preferablePaths = swT.retrievePathsEp1WithNodesCount(2).findAll { it.retrieveNodes()!= ep1FlowPathNodes }
        def availablePathsIslsEp2 = swT.retrieveAvailablePathsEp2()

        List<IslExtended> islsOfEp2Path
        preferableAltPathForSubFlow1 = preferablePaths.find { prefPathSub1 ->
            preferableAltPathForSubFlow2 = availablePathsIslsEp2.find { prefPathSub2 ->
                islsOfEp2Path = isls.all().findInPath(prefPathSub2)
                def islsOfEp1Path = isls.all().findInPath(prefPathSub1)
                islsOfEp2Path.first() == islsOfEp1Path.first()
                    && initialFlowIsls.every { !it.isIncludedInPath(islsOfEp2Path)} }

            prefPathSub1 && preferableAltPathForSubFlow2
        }

        assert preferableAltPathForSubFlow1 && preferableAltPathForSubFlow2

        availablePathsIslsEp2.findAll{ it != preferableAltPathForSubFlow2 }.collect{ isls.all().findInPath(it) }
                .each{ isls.all().makePathIslsMorePreferable(islsOfEp2Path, it) }

        and: "Make the future path to have exact bandwidth to handle the HA-Flow"
        def thinIsl = isls.all().collectIslsFromPaths([preferableAltPathForSubFlow1, preferableAltPathForSubFlow2])
                .updateIslsAvailableAndMaxBandwidthInDb(haFlow.maximumBandwidth).getListOfIsls()

        and: "Init a reroute of the HA-Flow"
        def rerouteResponse = haFlow.reroute()

        then: "The HA-Flow is successfully rerouted and goes through the preferable path"
        rerouteResponse.rerouted
        assert rerouteResponse.subFlowPaths.size() == 2
        haFlow.waitForBeingInState(FlowState.UP)

        def haFlowPathAfterReroute = haFlow.retrievedAllEntityPaths()
        def actualFlowIslsAfterReroute = isls.all().findInPath(haFlowPathAfterReroute)

        haFlowPathAfterReroute.subFlowPaths.each { subFlowPath ->
            def rerouteNodes = rerouteResponse.subFlowPaths.find { it.flowId == subFlowPath.flowId }.nodes
            def actualNodes =  subFlowPath.path.forward.nodes.toPathNodeV2()
            //verify nodes from reroute response
            assert rerouteNodes == actualNodes

            def expectedNodes = subFlowPath.flowId == ep1FlowId ? preferableAltPathForSubFlow1 : preferableAltPathForSubFlow2
            //verify actual nodes are expected ones
            assert actualNodes == expectedNodes.nodes.nodes
        }

        thinIsl.each { assert it.isIncludedInPath(actualFlowIslsAfterReroute) }

        and: "And involved switches pass validation"
        def allInvolvedSwitchIds = switches.all().findSpecific(
                [initialPath, haFlowPathAfterReroute].collectMany { it.getInvolvedSwitches() })
        synchronizeAndCollectFixedDiscrepancies(allInvolvedSwitchIds).isEmpty()

        and: "HA-Flow pass validation"
        haFlow.validate().asExpected

        and: "'Thin' ISL has 0 available bandwidth left"
        Wrappers.wait(WAIT_OFFSET) {
            thinIsl.each { assert it.getNbDetails().availableBandwidth == 0 }
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
        def initialInvolvedIsls = isls.all().findInPath(initialPath)

        when: "Make the current path less preferable than alternatives"
        initialInvolvedIsls.each { it.updateCost(NOT_PREFERABLE_COST * 3) }

        and: "Make all alternative paths to have not enough bandwidth to handle the HA-Flow"
        def newBw = haFlow.maximumBandwidth - 1
        def changedIsls = isls.all().excludeIsls(initialInvolvedIsls)
                .updateIslsAvailableAndMaxBandwidthInDb(newBw).getListOfIsls()

        and: "Init a reroute to a more preferable path"
        def rerouteResponse = haFlow.reroute()

        then: "The HA-Flow is rerouted because ignoreBandwidth=true"
        rerouteResponse.rerouted
        initialPath.subFlowPaths.size() == rerouteResponse.subFlowPaths.size()

        initialPath.subFlowPaths.each { subFlowPath ->
            def rerouteNodes = rerouteResponse.subFlowPaths.find { it.flowId == subFlowPath.flowId }.nodes
            assert rerouteNodes != subFlowPath.path.forward.nodes.toPathNodeV2()
        }
        haFlow.waitForBeingInState(FlowState.UP)

        def haFlowPathAfterReroute = haFlow.retrievedAllEntityPaths()
        haFlowPathAfterReroute.subFlowPaths.each { subFlowPath ->
            def rerouteNodes = rerouteResponse.subFlowPaths.find { it.flowId == subFlowPath.flowId }.nodes
            def actualNodes =  subFlowPath.path.forward.nodes.toPathNodeV2()
            //verify nodes from reroute response
            assert rerouteNodes == actualNodes
        }

        isls.all().findInPath(haFlowPathAfterReroute) != initialInvolvedIsls

        and: "And involved switches pass validation"
        def allInvolvedSwitchIds = switches.all().findSpecific(
                [initialPath, haFlowPathAfterReroute].collectMany { it.getInvolvedSwitches() })
        synchronizeAndCollectFixedDiscrepancies(allInvolvedSwitchIds).isEmpty()

        and: "HA-Flow pass validation"
        haFlow.validate().asExpected

        and: "Available bandwidth was not changed while rerouting due to ignoreBandwidth=true"
        def allLinks = northbound.getAllLinks()
        withPool {
            changedIsls.eachParallel {
                assert it.getInfo(allLinks, false).availableBandwidth == newBw
                assert it.getInfo(allLinks, true).availableBandwidth == newBw
            }
        }
    }
}
