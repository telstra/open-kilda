package org.openkilda.functionaltests.spec.switches

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.helpers.Wrappers.timedLoop
import static org.openkilda.testing.Constants.DEFAULT_COST
import static org.openkilda.testing.Constants.PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.Path
import org.openkilda.functionaltests.helpers.factory.YFlowFactory
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchId
import org.openkilda.testing.model.topology.TopologyDefinition.Isl

import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Shared

class SwitchMaintenanceSpec extends HealthCheckSpecification {

    @Shared
    @Autowired
    FlowFactory flowFactory
    @Shared
    @Autowired
    YFlowFactory yFlowFactory

    @Tags(SMOKE)
    def "Maintenance mode can be set/unset for a particular switch"() {
        given: "An active switch"
        def sw = switches.all().first()

        when: "Set maintenance mode for the switch"
        def setMaintenance = sw.setMaintenance(true, false)

        then: "Maintenance flag for the switch is really set"
        setMaintenance.underMaintenance
        sw.getDetails().underMaintenance

        and: "Maintenance flag for all ISLs going through the switch is set as well"
        northbound.getAllLinks().findAll { sw.switchId in [it.source, it.destination]*.switchId }.each {
            assert it.underMaintenance
        }

        and: "Cost for ISLs is not changed"
        topology.islsForActiveSwitches.findAll { sw.switchId in [it.srcSwitch, it.dstSwitch]*.dpId }.each {
            assert database.getIslCost(it) == DEFAULT_COST
            assert database.getIslCost(it.reversed) == DEFAULT_COST
        }

        when: "Unset maintenance mode from the switch"
        def unsetMaintenance = sw.setMaintenance(false, false)

        then: "Maintenance flag for the switch is really unset"
        !unsetMaintenance.underMaintenance
        !sw.getDetails().underMaintenance

        and: "Maintenance flag for all ISLs going through the switch is unset as well"
        northbound.getAllLinks().findAll { sw.switchId in [it.source, it.destination]*.switchId }.each {
            assert !it.underMaintenance
        }

        and: "Cost for ISLs is not changed"
        topology.islsForActiveSwitches.findAll { sw.switchId in [it.srcSwitch, it.dstSwitch]*.dpId }.each {
            assert database.getIslCost(it) == DEFAULT_COST
            assert database.getIslCost(it.reversed) == DEFAULT_COST
        }
    }

    def "Flows can be evacuated (rerouted) from a particular switch(several Isls are in use) when setting maintenance mode for it"() {
        given: "Two active not neighboring switches and a switch to be maintained"
        SwitchId swId
        List<Isl> intermediateSwIsls
        List<Isl> flow1PathIsls
        def switchPair = switchPairs.all().nonNeighbouring().getSwitchPairs().find {
            List<Path> availablePath = it.retrieveAvailablePaths()
            flow1PathIsls = availablePath.find { Path aPath ->
                swId = aPath.getInvolvedSwitches().find { aSw ->
                    intermediateSwIsls = topology.getRelatedIsls(aSw)
                    availablePath.findAll { it != aPath }.find { !it.getInvolvedSwitches().contains(aSw) && intermediateSwIsls.size() > 2 }
                }
        }.getInvolvedIsls()
        } ?: assumeTrue(false, "No suiting switches found. Need a switch pair with at least 2 paths and one of the " +
        "paths should not use the maintenance switch")
        switchPair.retrieveAvailablePaths().collect { it.getInvolvedIsls() }.findAll { it != flow1PathIsls }
                .each { islHelper.makePathIslsMorePreferable(flow1PathIsls, it) }

        and: "Create a Flow going through these switches"
        def switchUnderTest = switches.all().findSpecific(swId)
        def flow1 = flowFactory.getRandom(switchPair)
        def flow1Path = flow1.retrieveAllEntityPaths()
        def flow1IntermediateSwIsl = flow1PathIsls.findAll { it in intermediateSwIsls  || it.reversed in intermediateSwIsls }
        assert flow1Path.getInvolvedIsls().containsAll(flow1IntermediateSwIsl)

        and: "Create an additional Flow that has the same intermediate switch, but other ISLs are involved into a path"
        def additionalSwitchPair = switchPairs.all().nonNeighbouring()
                .excludeSwitches([switchPair.src, switchUnderTest]).random()

        def flow2PathIsls = additionalSwitchPair.retrieveAvailablePaths()
                .find { it.getInvolvedSwitches().contains(swId) && it.getInvolvedIsls().intersect(flow1PathIsls).isEmpty() }.getInvolvedIsls()

        additionalSwitchPair.retrieveAvailablePaths().collect { it.getInvolvedIsls() }.findAll { it != flow2PathIsls }
                .each { islHelper.makePathIslsMorePreferable(flow2PathIsls, it) }

        def flow2 = flowFactory.getRandom(additionalSwitchPair, false)
        def flow2Path = flow2.retrieveAllEntityPaths()
        def flow2IntermediateSwIsl = flow2PathIsls.findAll { it in intermediateSwIsls  || it.reversed in intermediateSwIsls }
        assert flow2Path.getInvolvedIsls().containsAll(flow2IntermediateSwIsl)
        assert flow2IntermediateSwIsl.intersect(flow1IntermediateSwIsl).isEmpty()

        when: "Set maintenance mode without flows evacuation flag for some intermediate switch involved in flow paths"
        switchUnderTest.setMaintenance(true, false)

        then: "Flows are not evacuated (rerouted) and have the same paths"
        timedLoop(3) {
            assert flow1.retrieveAllEntityPaths() == flow1Path
            assert flow2.retrieveAllEntityPaths() == flow2Path
        }

        when: "Set maintenance mode again with flows evacuation flag for the same switch"
        switchUnderTest.setMaintenance(true, true)

        then: "Flows are evacuated (rerouted)"
        Wrappers.wait(PATH_INSTALLATION_TIME + WAIT_OFFSET) {
            [flow1, flow2].each { assert it.retrieveFlowStatus().status == FlowState.UP }
            assert flow1.retrieveAllEntityPaths() != flow1Path
            assert flow2.retrieveAllEntityPaths() != flow2Path
        }

        and: "Switch under maintenance is not involved in new flow paths"
        !flow1.retrieveAllEntityPaths().getInvolvedSwitches().contains(swId)
        !flow2.retrieveAllEntityPaths().getInvolvedSwitches().contains(swId)

    }

    @Tags(SMOKE)
    def "Both Y-Flow and Flow can be evacuated (rerouted) from a particular switch when setting maintenance mode for it"() {
        given: "Switch triplet has been selected"
        def swTriplet = profile == "hardware" ? switchTriplets.all().withSharedEpEp1Ep2InChain().random()
                : switchTriplets.all().nonNeighbouring().random() ?: assumeTrue(false, "No suiting switches found.")

        and: "Create a Y-Flow going through selected switches and has intermediate switch in a path"
        if (profile == "hardware") {
            //additional steps due to the HW limitation
            def availablePaths = swTriplet.pathsEp1[0].size() == 2 ?
                    swTriplet.retrieveAvailablePathsEp1().collect { it.getInvolvedIsls() } :
                    swTriplet.retrieveAvailablePathsEp2().collect { it.getInvolvedIsls() }
            // 1 isl is equal to 2 pathNodes
            def preferablePath = availablePaths.find { it.size() > 1 }
            availablePaths.findAll { it != preferablePath }.each {
                islHelper.makePathIslsMorePreferable(preferablePath, it)
            }
        }
        def yFlow = yFlowFactory.getRandom(swTriplet, false)
        def yFlowPath = yFlow.retrieveAllEntityPaths()
        def transitSw = switches.all().findSwitchesInPath(yFlowPath).find { it !in swTriplet.toList() }
        assert transitSw

        and: "Two active not neighboring switches and preferable path with intermediate switch"
        def dstSw = swTriplet.pathsEp1[0].size() == 4 ? swTriplet.ep1 : swTriplet.ep2
        def swPair = switchPairs.all().specificPair(swTriplet.shared, dstSw)
        def availablePaths = swPair.retrieveAvailablePaths()
        List<Isl> pathIsls = availablePaths.find { path -> path.getInvolvedSwitches().contains(transitSw.switchId) }.getInvolvedIsls()
        availablePaths.collect { it.getInvolvedIsls() }.findAll { it != pathIsls }
                .each { islHelper.makePathIslsMorePreferable(pathIsls, it) }

        and: "Create a Flow going through these switches"
        def flow = flowFactory.getRandom(swPair)
        def flowPath = flow.retrieveAllEntityPaths()
        assert flowPath.getInvolvedSwitches().contains(transitSw.switchId)

        when: "Set maintenance mode without flows evacuation flag for some intermediate switch involved in flow paths"
        transitSw.setMaintenance(true, false)

        then: "Both Flow and Y-Flow are not evacuated (rerouted) and have the same paths"
        timedLoop(3) {
            assert flow.retrieveAllEntityPaths() == flowPath
            assert yFlow.retrieveAllEntityPaths() == yFlowPath
        }

        when: "Set maintenance mode again with flows evacuation flag for the same switch"
        transitSw.setMaintenance(true, true)

        then: "Both Flow and Y-Flow are evacuated (rerouted)"
        Wrappers.wait(PATH_INSTALLATION_TIME + WAIT_OFFSET) {
            assert flow.retrieveFlowStatus().status == FlowState.UP
            assert yFlow.retrieveDetails().status == FlowState.UP

            assert flow.retrieveAllEntityPaths() != flowPath
            assert yFlow.retrieveAllEntityPaths() != yFlowPath
        }

        and: "Switch under maintenance is not involved in new flow paths"
        !flow.retrieveAllEntityPaths().getInvolvedSwitches().contains(transitSw.switchId)
        !yFlow.retrieveAllEntityPaths().getInvolvedSwitches().contains(transitSw.switchId)
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "Link discovered by a switch under maintenance is marked as maintained"() {
        given: "An active switch with an active link"
        def switchUnderTest = switches.all().random()
        def isl = topology.getRelatedIsls(switchUnderTest.switchId).first()

        and: "Bring port down on the switch to fail the link"
        islHelper.breakIsl(isl)

        and: "Delete the link"
        northbound.deleteLink(islUtils.toLinkParameters(isl))
        !islUtils.getIslInfo(isl)
        !islUtils.getIslInfo(isl.reversed)

        when: "Set maintenance mode for the switch"
        switchUnderTest.setMaintenance(true, false)

        and: "Bring port up to discover the deleted link"
        switchUnderTest.getPort(isl.srcPort).up()

        then: "The link is discovered and marked as maintained"
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            def links = northbound.getAllLinks()
            def islInfo = islUtils.getIslInfo(links, isl).get()
            def reverseIslInfo = islUtils.getIslInfo(links, isl.reversed).get()

            [islInfo, reverseIslInfo].each {
                assert it.state == IslChangeType.DISCOVERED
                assert it.underMaintenance
            }
        }
    }
}
