package org.openkilda.functionaltests.spec.links

import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.helpers.Wrappers.timedLoop
import static org.openkilda.functionaltests.helpers.model.Isls.breakIsls
import static org.openkilda.testing.Constants.DEFAULT_COST
import static org.openkilda.testing.Constants.PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowEntityPath
import org.openkilda.functionaltests.helpers.model.FlowWithSubFlowsEntityPath
import org.openkilda.functionaltests.helpers.factory.YFlowFactory
import org.openkilda.messaging.payload.flow.FlowState

import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Shared

class LinkMaintenanceSpec extends HealthCheckSpecification {

    @Autowired
    @Shared
    FlowFactory flowFactory

    @Autowired
    @Shared
    YFlowFactory yFlowFactory

    @Tags(SMOKE)
    def "Maintenance mode can be set/unset for a particular link"() {
        given: "An active link"
        def isl = isls.all().random()

        when: "Set maintenance mode for the link"
        def response = isl.setMaintenance(true, true)

        then: "Maintenance flag for forward and reverse ISLs is really set"
        response.each { assert it.underMaintenance }

        and: "Cost for ISLs is not changed"
        isl.getCostFromDb() == DEFAULT_COST
        isl.reversed.getCostFromDb() == DEFAULT_COST

        when: "Unset maintenance mode from the link"
        response = isl.unsetMaintenance()

        then: "Maintenance flag for forward and reverse ISLs is really unset"
        response.each { assert !it.underMaintenance }

        and: "Cost for ISLs is changed to the default value"
        isl.getCostFromDb() == DEFAULT_COST
        isl.reversed.getCostFromDb() == DEFAULT_COST
    }

    def "Flows can be evacuated (rerouted) from a particular link when setting maintenance mode for it"() {
        given: "Two active not neighboring switches with two possible paths at least"
        def switchPair = switchPairs.all().nonNeighbouring().withAtLeastNPaths(2).random()

        and: "Create a couple of flows going through these switches"
        def flow1 = flowFactory.getRandom(switchPair)
        def flow1Path = flow1.retrieveAllEntityPaths()

        def flow2 = flowFactory.getRandom(switchPair, false, FlowState.UP, flow1.occupiedEndpoints())
        def flow2Path = flow2.retrieveAllEntityPaths()
        assert flow1Path.getPathNodes() == flow2Path.getPathNodes()

        when: "Set maintenance mode without flows evacuation flag for the first link involved in flow paths"
        def isl = isls.all().findInPath(flow1Path).first()
        isl.setMaintenance(true, false)

        then: "Flows are not evacuated (rerouted) and have the same paths"
        timedLoop(3) {
            assert flow1.retrieveAllEntityPaths() == flow1Path
            assert flow2.retrieveAllEntityPaths() == flow2Path
        }

        when: "Set maintenance mode again with flows evacuation flag for the same link"
        northbound.setLinkMaintenance(isl.toLinkUnderMaintenance(true, true))

        then: "Flows are evacuated (rerouted)"
        FlowEntityPath flow1PathUpdated, flow2PathUpdated
        Wrappers.wait(PATH_INSTALLATION_TIME + WAIT_OFFSET) {
            [flow1, flow2].each { flow -> assert flow.retrieveFlowStatus().status == FlowState.UP }

            flow1PathUpdated = flow1.retrieveAllEntityPaths()
            flow2PathUpdated = flow2.retrieveAllEntityPaths()

            assert flow1PathUpdated != flow1Path
            assert flow2PathUpdated != flow2Path
        }

        and: "Link under maintenance is not involved in new flow paths"
        !isls.all().findInPath(flow1PathUpdated).contains(isl)
        !isls.all().findInPath(flow2PathUpdated).contains(isl)
    }

    def "Y-Flows can be evacuated (rerouted) from a particular link when setting maintenance mode for it"() {
        given: "Switch triplet with two possible paths at least for non-neighbouring switches"
        def swTriplet = switchTriplets.all().nonNeighbouring().withAtLeastNNonOverlappingPaths(2).random()

        and: "Create Y-Flows going through selected switch triplet"
        def yFlow1 = yFlowFactory.getRandom(swTriplet, false)
        def yFlow1Path = yFlow1.retrieveAllEntityPaths()

        def yFlow2 = yFlowFactory.getRandom(swTriplet, false, yFlow1.occupiedEndpoints())
        def yFlow2Path = yFlow2.retrieveAllEntityPaths()
        assert yFlow1Path.getInvolvedIsls().sort() == yFlow2Path.getInvolvedIsls().sort()

        when: "Set maintenance mode without flows evacuation flag for the first link involved in flow paths"
        def isl = isls.all().findInPath(yFlow1Path).first()
        isl.setMaintenance(true, false)

        then: "Y-Flows are not evacuated (rerouted) and have the same paths"
        timedLoop(3) {
            assert yFlow1.retrieveAllEntityPaths() == yFlow1Path
            assert yFlow2.retrieveAllEntityPaths() == yFlow2Path
        }

        when: "Set maintenance mode again with flows evacuation flag for the same link"
        northbound.setLinkMaintenance(isl.toLinkUnderMaintenance(true, true))

        then: "Y-Flows are evacuated (rerouted) and link under maintenance is not involved in new flow paths"
        FlowWithSubFlowsEntityPath yFlow1PathUpdated, yFlow2PathUpdated
        Wrappers.wait(PATH_INSTALLATION_TIME + WAIT_OFFSET) {
            [yFlow1, yFlow2].each { flow -> assert flow.retrieveDetails().status == FlowState.UP }
            yFlow1PathUpdated = yFlow1.retrieveAllEntityPaths()
            yFlow2PathUpdated = yFlow2.retrieveAllEntityPaths()

            assert yFlow1PathUpdated != yFlow1Path
            assert yFlow2PathUpdated != yFlow2Path
        }

        and: "Link under maintenance is not involved in new Y-Flow paths"
        !isls.all().findInPath(yFlow1PathUpdated).contains(isl)
        !isls.all().findInPath(yFlow2PathUpdated).contains(isl)
    }

    @Tags(SMOKE)
    def "Both Y-Flow and Flow can be evacuated (rerouted) from a particular link when setting maintenance mode for it"() {
        given: "Switch triplet with active switches"
        def swTriplet = switchTriplets.all().withSharedEpEp1Ep2InChain().random()

        and: "Create Y-Flows going through selected switch triplet"
        def yFlow = yFlowFactory.getRandom(swTriplet, false)
        def yFlowPath = yFlow.retrieveAllEntityPaths()
        def isl = isls.all().findInPath(yFlowPath).first()

        and: "Switch pair has been selected based on Y-Flow used Isl"
        def switchPair = switchPairs.all().specificPair(isl.srcSwId, isl.dstSwId)

        and: "Create Flow going through selected switch pair"
        def flow = flowFactory.getRandom(switchPair)
        def flowPath = flow.retrieveAllEntityPaths()
        assert isls.all().findInPath(flowPath).contains(isl)

        when: "Set maintenance mode without flows evacuation flag for the first link involved in flow paths"
        isl.setMaintenance(true, false)

        then: "Both Y-Flow and Flow are not evacuated (rerouted) and have the same paths"
        timedLoop(3) {
            assert flow.retrieveAllEntityPaths() == flowPath
            assert yFlow.retrieveAllEntityPaths() == yFlowPath
        }

        when: "Set maintenance mode again with flows evacuation flag for the same link"
        northbound.setLinkMaintenance(isl.toLinkUnderMaintenance(true, true))

        then: "Both Y-Flow and Flow are evacuated (rerouted)"
        Wrappers.wait(PATH_INSTALLATION_TIME + WAIT_OFFSET) {
            assert flow.retrieveFlowStatus().status == FlowState.UP
            assert yFlow.retrieveDetails().status == FlowState.UP

            assert flow.retrieveAllEntityPaths() != flowPath
            assert yFlow.retrieveAllEntityPaths() != yFlowPath
        }

        and: "Link under maintenance is not involved in new flow paths"
        !isls.all().findInPath(flow.retrieveAllEntityPaths()).contains(isl)
        !isls.all().findInPath(yFlow.retrieveAllEntityPaths()).contains(isl)
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "Flows are rerouted to a path with link under maintenance when there are no other paths available"() {
        given: "Two active not neighboring switches with two possible paths at least"
        def switchPair = switchPairs.all().nonNeighbouring().withAtLeastNPaths(2).random()

        and: "Create a couple of flows going through these switches"
        def flow1 = flowFactory.getRandom(switchPair)
        def flow1Isls = isls.all().findInPath(flow1.retrieveAllEntityPaths())

        def flow2 = flowFactory.getRandom(switchPair, false, FlowState.UP, flow1.occupiedEndpoints())
        def flow2Isls = isls.all().findInPath(flow2.retrieveAllEntityPaths())
        assert flow1Isls == flow2Isls

        and: "Make only one alternative path available for both flows"
        def flow1ActualIsl = flow1Isls.first()
        def altIsls = isls.all().relatedTo(switchPair.src).excludeIsls(flow1Isls).getListOfIsls()
        /* altIsls can have only 1 element (the only one alt ISL).
        In this case it will be set under maintenance mode, and breaking the other
        alternative ISLs will be skipped: "altIsls - altIsls.first()" will be empty. */
        breakIsls(altIsls - altIsls.first())

        and: "Set maintenance mode for the first link involved in alternative path"
        def islUnderMaintenance = altIsls.first()
        islUnderMaintenance.setMaintenance(true, false)

        when: "Force flows to reroute by bringing port down on the source switch"
        flow1ActualIsl.breakIt()

        then: "Flows are rerouted to alternative path with link under maintenance"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET * 2) {
            [flow1, flow2].each { flow -> assert flow.retrieveFlowStatus().status == FlowState.UP }

            def flow1UpdatedIsls = isls.all().findInPath(flow1.retrieveAllEntityPaths())
            def flow2UpdatedIsls = isls.all().findInPath(flow2.retrieveAllEntityPaths())

            assert flow1UpdatedIsls != flow1Isls
            assert flow2UpdatedIsls != flow2Isls
            assert flow1UpdatedIsls.contains(islUnderMaintenance)
            assert flow2UpdatedIsls.contains(islUnderMaintenance)
        }
    }
}
