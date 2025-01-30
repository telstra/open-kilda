package org.openkilda.functionaltests.spec.links

import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.testing.Constants.DEFAULT_COST
import static org.openkilda.testing.Constants.PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowEntityPath
import org.openkilda.messaging.payload.flow.FlowState

import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Shared

class LinkMaintenanceSpec extends HealthCheckSpecification {

    @Autowired
    @Shared
    FlowFactory flowFactory

    @Tags(SMOKE)
    def "Maintenance mode can be set/unset for a particular link"() {
        given: "An active link"
        def isl = topology.islsForActiveSwitches.first()

        when: "Set maintenance mode for the link"
        def response = islHelper.setLinkMaintenance(isl, true, false)

        then: "Maintenance flag for forward and reverse ISLs is really set"
        response.each { assert it.underMaintenance }

        and: "Cost for ISLs is not changed"
        database.getIslCost(isl) == DEFAULT_COST
        database.getIslCost(isl.reversed) == DEFAULT_COST

        when: "Unset maintenance mode from the link"
        response = northbound.setLinkMaintenance(islUtils.toLinkUnderMaintenance(isl, false, false))

        then: "Maintenance flag for forward and reverse ISLs is really unset"
        response.each { assert !it.underMaintenance }

        and: "Cost for ISLs is changed to the default value"
        database.getIslCost(isl) == DEFAULT_COST
        database.getIslCost(isl.reversed) == DEFAULT_COST
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
        def isl = flow1Path.getInvolvedIsls().first()
        islHelper.setLinkMaintenance(isl, true, false)

        then: "Flows are not evacuated (rerouted) and have the same paths"
        flow1.retrieveAllEntityPaths() == flow1Path
        flow2.retrieveAllEntityPaths() == flow2Path

        when: "Set maintenance mode again with flows evacuation flag for the same link"
        northbound.setLinkMaintenance(islUtils.toLinkUnderMaintenance(isl, true, true))

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
        !flow1PathUpdated.getInvolvedIsls().contains(isl)
        !flow2PathUpdated.getInvolvedIsls().contains(isl)
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "Flows are rerouted to a path with link under maintenance when there are no other paths available"() {
        given: "Two active not neighboring switches with two possible paths at least"
        def switchPair = switchPairs.all().nonNeighbouring().withAtLeastNPaths(2).random()

        and: "Create a couple of flows going through these switches"
        def flow1 = flowFactory.getRandom(switchPair)
        def flow1Path = flow1.retrieveAllEntityPaths()

        def flow2 = flowFactory.getRandom(switchPair, false, FlowState.UP, flow1.occupiedEndpoints())
        def flow2Path = flow2.retrieveAllEntityPaths()
        assert flow1Path.getPathNodes() == flow2Path.getPathNodes()

        and: "Make only one alternative path available for both flows"
        def flow1ActualIsl = flow1Path.getInvolvedIsls().first()
        def altIsls = topology.getRelatedIsls(switchPair.src) - flow1ActualIsl
        /* altIsls can have only 1 element (the only one alt ISL).
        In this case it will be set under maintenance mode, and breaking the other
        alternative ISLs will be skipped: "altIsls - altIsls.first()" will be empty. */
        islHelper.breakIsls(altIsls - altIsls.first())

        and: "Set maintenance mode for the first link involved in alternative path"
        def islUnderMaintenance = altIsls.first()
        islHelper.setLinkMaintenance(islUnderMaintenance, true, false)

        when: "Force flows to reroute by bringing port down on the source switch"
        islHelper.breakIsl(flow1ActualIsl)

        then: "Flows are rerouted to alternative path with link under maintenance"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET * 2) {
            [flow1, flow2].each { flow ->  assert flow.retrieveFlowStatus().status == FlowState.UP }

            def flow1PathUpdated = flow1.retrieveAllEntityPaths()
            def flow2PathUpdated = flow2.retrieveAllEntityPaths()

            assert flow1PathUpdated != flow1Path
            assert flow2PathUpdated != flow2Path
            assert flow1PathUpdated.getInvolvedIsls().contains(islUnderMaintenance)
            assert flow2PathUpdated.getInvolvedIsls().contains(islUnderMaintenance)
        }
    }
}
