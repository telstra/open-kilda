package org.openkilda.functionaltests.spec.switches

import static org.junit.Assume.assumeTrue
import static org.openkilda.testing.Constants.DEFAULT_COST
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import org.springframework.beans.factory.annotation.Value
import org.springframework.web.client.HttpClientErrorException

class SwitchMaintenance extends BaseSpecification {

    @Value('${isl.cost.when.under.maintenance}')
    int islCostWhenUnderMaintenance

    def setupOnce() {
        database.resetCosts()  // set default cost on all links before tests
    }

    def "Maintenance mode can be set/unset for a particular switch"() {
        given: "An active switch"
        def sw = topology.activeSwitches.first()

        when: "Set maintenance mode for the switch"
        def response = northbound.setSwitchMaintenance(sw.dpId, true, false)

        then: "Maintenance flag for the switch is really set"
        response.underMaintenance
        northbound.getSwitch(sw.dpId).underMaintenance

        and: "Maintenance flag for all ISLs going through the switch is set as well"
        northbound.getAllLinks().findAll { sw.dpId in [it.source, it.destination]*.switchId }.each {
            assert it.underMaintenance
        }

        and: "Cost for ISLs is changed respectively"
        topology.islsForActiveSwitches.findAll { sw.dpId in [it.srcSwitch, it.dstSwitch]*.dpId }.each {
            assert database.getIslCost(it) == islCostWhenUnderMaintenance + DEFAULT_COST
            assert database.getIslCost(it.reversed) == islCostWhenUnderMaintenance + DEFAULT_COST
        }

        when: "Unset maintenance mode from the switch"
        response = northbound.setSwitchMaintenance(sw.dpId, false, false)

        then: "Maintenance flag for the switch is really unset"
        !response.underMaintenance
        !northbound.getSwitch(sw.dpId).underMaintenance

        and: "Maintenance flag for all ISLs going through the switch is unset as well"
        northbound.getAllLinks().findAll { sw.dpId in [it.source, it.destination]*.switchId }.each {
            assert !it.underMaintenance
        }

        and: "Cost for ISLs is changed to the default value"
        topology.islsForActiveSwitches.findAll { sw.dpId in [it.srcSwitch, it.dstSwitch]*.dpId }.each {
            assert database.getIslCost(it) == DEFAULT_COST
            assert database.getIslCost(it.reversed) == DEFAULT_COST
        }
    }

    def "Flows can be evacuated (rerouted) from a particular switch when setting maintenance mode for it"() {
        given: "Two active not neighboring switches with two possible paths at least"
        def switches = topology.getActiveSwitches()
        def allLinks = northbound.getAllLinks()
        List<List<PathNode>> possibleFlowPaths = []
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            possibleFlowPaths = database.getPaths(src.dpId, dst.dpId)*.path.sort { it.size() }
            allLinks.every { link ->
                !(link.source.switchId == src.dpId && link.destination.switchId == dst.dpId)
            } && possibleFlowPaths.size() > 1
        } ?: assumeTrue("No suiting switches found", false)

        and: "Create a couple of flows going through these switches"
        def flow1 = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow1)
        def flow1Path = PathHelper.convert(northbound.getFlowPath(flow1.id))

        def flow2 = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow2)
        def flow2Path = PathHelper.convert(northbound.getFlowPath(flow2.id))

        assert flow1Path == flow2Path

        when: "Set maintenance mode without flows evacuation flag for some intermediate switch involved in flow paths"
        def sw = pathHelper.getInvolvedSwitches(flow1Path)[1]
        northbound.setSwitchMaintenance(sw.dpId, true, false)

        then: "Flows are not evacuated (rerouted) and have the same paths"
        PathHelper.convert(northbound.getFlowPath(flow1.id)) == flow1Path
        PathHelper.convert(northbound.getFlowPath(flow2.id)) == flow2Path

        when: "Set maintenance mode again with flows evacuation flag for the same switch"
        northbound.setSwitchMaintenance(sw.dpId, true, true)

        then: "Flows are evacuated (rerouted)"
        Wrappers.wait(WAIT_OFFSET) {
            [flow1, flow2].each { assert northbound.getFlowStatus(it.id).status == FlowState.UP }
        }

        def flow1PathUpdated = PathHelper.convert(northbound.getFlowPath(flow1.id))
        def flow2PathUpdated = PathHelper.convert(northbound.getFlowPath(flow2.id))

        flow1PathUpdated != flow1Path
        flow2PathUpdated != flow2Path

        and: "Switch under maintenance is not involved in new flow paths"
        !(sw in pathHelper.getInvolvedSwitches(flow1PathUpdated))
        !(sw in pathHelper.getInvolvedSwitches(flow2PathUpdated))

        and: "Delete flows and unset maintenance mode"
        [flow1, flow2].each { flowHelper.deleteFlow(it.id) }
        northbound.setSwitchMaintenance(sw.dpId, false, false)
    }

    def "Link discovered by a switch under maintenance is marked as maintained"() {
        given: "An active link"
        def isl = topology.islsForActiveSwitches.first()

        and: "Bring port down on the switch to fail the link"
        northbound.portDown(isl.srcSwitch.dpId, isl.srcPort)
        Wrappers.wait(WAIT_OFFSET) { assert islUtils.getIslInfo(isl).get().state == IslChangeType.FAILED }

        and: "Delete the link"
        northbound.deleteLink(islUtils.toLinkParameters(isl))
        northbound.deleteLink(islUtils.toLinkParameters(isl.reversed))
        assert !islUtils.getIslInfo(isl)
        assert !islUtils.getIslInfo(isl.reversed)

        when: "Set maintenance mode for the switch"
        northbound.setSwitchMaintenance(isl.srcSwitch.dpId, true, false)

        and: "Bring port up to discover the deleted link"
        northbound.portUp(isl.srcSwitch.dpId, isl.srcPort)

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

        and: "Unset maintenance mode and reset costs"
        northbound.setSwitchMaintenance(isl.srcSwitch.dpId, false, false)
        def links = northbound.getAllLinks()
        !islUtils.getIslInfo(links, isl).get().underMaintenance
        !islUtils.getIslInfo(links, isl.reversed).get().underMaintenance
        database.resetCosts()
    }

    def "System is correctly handling actions performing on a maintained switch disconnected from the controller"() {
        requireProfiles("virtual")

        given: "An active switch under maintenance disconnected from the controller"
        def sw = topology.activeSwitches.first()
        northbound.setSwitchMaintenance(sw.dpId, true, false)
        lockKeeper.knockoutSwitch(sw.dpId)
        Wrappers.wait(discoveryTimeout + WAIT_OFFSET) {
            northbound.getAllLinks().findAll { sw.dpId in [it.source, it.destination]*.switchId }.each {
                assert it.state == IslChangeType.FAILED
            }
        }

        when: "Try to get switch info"
        def response = northbound.getSwitch(sw.dpId)

        then: "Detailed switch info is returned"
        response.switchId == sw.dpId
        response.state == SwitchChangeType.ACTIVATED
        response.underMaintenance

        when: "Try to get switch rules"
        northbound.getSwitchRules(sw.dpId)

        then: "An error is received (404 code)"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        exc.responseBodyAsString.to(MessageError).errorMessage == "Switch $sw.dpId was not found"

        when: "Try to create a flow, using the switch"
        def flow = flowHelper.randomFlow(sw, topology.activeSwitches.last())
        northbound.addFlow(flow)

        then: "An error is received (404 code)"
        exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                "Could not create flow: Not enough bandwidth found or path not found : Failed to find path with " +
                "requested bandwidth=$flow.maximumBandwidth: Switch $sw.dpId doesn't have links with enough bandwidth"

        and: "Connect the switch back to the controller and unset maintenance mode"
        lockKeeper.reviveSwitch(sw.dpId)
        northbound.setSwitchMaintenance(sw.dpId, false, false)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
    }
}
