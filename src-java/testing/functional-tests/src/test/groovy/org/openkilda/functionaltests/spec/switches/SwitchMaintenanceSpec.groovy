package org.openkilda.functionaltests.spec.switches

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.testing.Constants.DEFAULT_COST
import static org.openkilda.testing.Constants.PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

class SwitchMaintenanceSpec extends HealthCheckSpecification {

    @Tags(SMOKE)
    def "Maintenance mode can be set/unset for a particular switch"() {
        given: "An active switch"
        def sw = topology.activeSwitches.first()

        when: "Set maintenance mode for the switch"
        def setMaintenance = switchHelper.setSwitchMaintenance(sw.dpId, true, false)

        then: "Maintenance flag for the switch is really set"
        setMaintenance.underMaintenance
        northbound.getSwitch(sw.dpId).underMaintenance

        and: "Maintenance flag for all ISLs going through the switch is set as well"
        northbound.getAllLinks().findAll { sw.dpId in [it.source, it.destination]*.switchId }.each {
            assert it.underMaintenance
        }

        and: "Cost for ISLs is not changed"
        topology.islsForActiveSwitches.findAll { sw.dpId in [it.srcSwitch, it.dstSwitch]*.dpId }.each {
            assert database.getIslCost(it) == DEFAULT_COST
            assert database.getIslCost(it.reversed) == DEFAULT_COST
        }

        when: "Unset maintenance mode from the switch"
        def unsetMaintenance = northbound.setSwitchMaintenance(sw.dpId, false, false)

        then: "Maintenance flag for the switch is really unset"
        !unsetMaintenance.underMaintenance
        !northbound.getSwitch(sw.dpId).underMaintenance

        and: "Maintenance flag for all ISLs going through the switch is unset as well"
        northbound.getAllLinks().findAll { sw.dpId in [it.source, it.destination]*.switchId }.each {
            assert !it.underMaintenance
        }

        and: "Cost for ISLs is not changed"
        topology.islsForActiveSwitches.findAll { sw.dpId in [it.srcSwitch, it.dstSwitch]*.dpId }.each {
            assert database.getIslCost(it) == DEFAULT_COST
            assert database.getIslCost(it.reversed) == DEFAULT_COST
        }
    }

    @Tags(SMOKE)
    def "Flows can be evacuated (rerouted) from a particular switch when setting maintenance mode for it"() {
        given: "Two active not neighboring switches and a switch to be maintained"
        TopologyDefinition.Switch sw
        List<PathNode> path
        def switchPair = switchPairs.all().nonNeighbouring().getSwitchPairs().find {
            path = it.paths.find { aPath ->
                sw = pathHelper.getInvolvedSwitches(aPath).find { aSw ->
                    it.paths.findAll { it != aPath }.find { !pathHelper.getInvolvedSwitches(it).contains(aSw) }
                }
            }
        } ?: assumeTrue(false, "No suiting switches found. Need a switch pair with at least 2 paths and one of the " +
        "paths should not use the maintenance switch")
        switchPair.paths.findAll { it != path }.each { pathHelper.makePathMorePreferable(path, it) }

        and: "Create a couple of flows going through these switches"
        def flow1 = flowHelperV2.randomFlow(switchPair)
        flowHelperV2.addFlow(flow1)
        def flow2 = flowHelperV2.randomFlow(switchPair, false, [flow1])
        flowHelperV2.addFlow(flow2)
        assert PathHelper.convert(northbound.getFlowPath(flow1.flowId)) == path
        assert PathHelper.convert(northbound.getFlowPath(flow2.flowId)) == path

        when: "Set maintenance mode without flows evacuation flag for some intermediate switch involved in flow paths"
        switchHelper.setSwitchMaintenance(sw.dpId, true, false)

        then: "Flows are not evacuated (rerouted) and have the same paths"
        PathHelper.convert(northbound.getFlowPath(flow1.flowId)) == path
        PathHelper.convert(northbound.getFlowPath(flow2.flowId)) == path

        when: "Set maintenance mode again with flows evacuation flag for the same switch"
        northbound.setSwitchMaintenance(sw.dpId, true, true)

        then: "Flows are evacuated (rerouted)"
        def flow1PathUpdated, flow2PathUpdated
        Wrappers.wait(PATH_INSTALLATION_TIME + WAIT_OFFSET) {
            [flow1, flow2].each { assert northboundV2.getFlowStatus(it.flowId).status == FlowState.UP }

            flow1PathUpdated = PathHelper.convert(northbound.getFlowPath(flow1.flowId))
            flow2PathUpdated = PathHelper.convert(northbound.getFlowPath(flow2.flowId))

            assert flow1PathUpdated != path
            assert flow2PathUpdated != path
        }

        and: "Switch under maintenance is not involved in new flow paths"
        !(sw in pathHelper.getInvolvedSwitches(flow1PathUpdated))
        !(sw in pathHelper.getInvolvedSwitches(flow2PathUpdated))
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "Link discovered by a switch under maintenance is marked as maintained"() {
        given: "An active link"
        def isl = topology.islsForActiveSwitches.first()

        and: "Bring port down on the switch to fail the link"
        islHelper.breakIsl(isl)

        and: "Delete the link"
        northbound.deleteLink(islUtils.toLinkParameters(isl))
        !islUtils.getIslInfo(isl)
        !islUtils.getIslInfo(isl.reversed)

        when: "Set maintenance mode for the switch"
        switchHelper.setSwitchMaintenance(isl.srcSwitch.dpId, true, false)

        and: "Bring port up to discover the deleted link"
        antiflap.portUp(isl.srcSwitch.dpId, isl.srcPort)

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
