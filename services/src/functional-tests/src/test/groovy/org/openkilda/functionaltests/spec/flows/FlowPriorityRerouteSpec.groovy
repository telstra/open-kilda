package org.openkilda.functionaltests.spec.flows

import static org.junit.Assume.assumeTrue
import static org.openkilda.model.MeterId.MAX_SYSTEM_RULE_METER_ID
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.payload.flow.FlowPayload
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchId

import spock.lang.Ignore
import spock.lang.Unroll

class FlowPriorityRerouteSpec extends BaseSpecification {
    @Unroll
    def "System is able to reroute(automatically) flow in the correct order based on the priority field"() {
        given: "Three flows on the same path, with alt paths available"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find { it.paths.size() > 1 } ?:
                assumeTrue("No suiting switches found", false)
        List<FlowPayload> flows = []

        def newPriority = 300

        3.times {
            def flow = flowHelper.randomFlow(switchPair)
            flow.maximumBandwidth = 10000
            flow.allocateProtectedPath = protectedPath
            flow.priority = newPriority
            flowHelper.addFlow(flow)
            newPriority -= 100
            flows << flow
        }

        def srcSwitchCreatedMetersIds = getCreatedMeterIds(switchPair.src.dpId)
        def dstSwitchCreatedMetersIds = getCreatedMeterIds(switchPair.dst.dpId)

        def currentPath = pathHelper.convert(northbound.getFlowPath(flows[0].id))
        //ensure all flows are on the same path
        assert pathHelper.convert(northbound.getFlowPath(flows[1].id)) == currentPath
        assert pathHelper.convert(northbound.getFlowPath(flows[2].id)) == currentPath

        def altPath = switchPair.paths.find { it != currentPath }

        when: "Init simultaneous reroute for all flows by bringing current path's ISL down"
        def currentIsls = pathHelper.getInvolvedIsls(currentPath)
        def newIsls = pathHelper.getInvolvedIsls(altPath)
        def islToBreak = currentIsls.find { !newIsls.contains(it) }
        northbound.portDown(islToBreak.srcSwitch.dpId, islToBreak.srcPort)

        then: "Flows were rerouted"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            flows.each {
                assert northbound.getFlowStatus(it.id).status == FlowState.UP
                assert pathHelper.convert(northbound.getFlowPath(it.id)) != currentPath
            }
        }

        and: "Reroute procedure was done based on the priority field"
        // for a flow with protected path we use a little bit different logic for rerouting then for simple flow
        // that's why we use WAIT_OFFSET here
        Wrappers.wait(WAIT_OFFSET) {
            flows.sort { it.priority }*.id == northbound.getAllFlows().sort { it.lastUpdated }*.id
        }

        and: "Cleanup: revert system to original state"
        northbound.portUp(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        flows.each { flowHelper.deleteFlow(it.id) }
        srcSwitchCreatedMetersIds.each { northbound.deleteMeter(switchPair.src.dpId, it) }
        dstSwitchCreatedMetersIds.each { northbound.deleteMeter(switchPair.dst.dpId, it) }
        database.resetCosts()
        Wrappers.wait(WAIT_OFFSET + discoveryInterval) {
            assert islUtils.getIslInfo(islToBreak).get().state == IslChangeType.DISCOVERED
        }
        database.resetCosts()

        where:
        info                     | protectedPath
        "without protected path" | false
        "with protected path"    | true
    }

    @Ignore("https://github.com/telstra/open-kilda/issues/2211")
    @Unroll
    def "System is able to reroute(intentional) flow in the correct order based on the priority field"() {
        given: "Three flows on the same path, with alt paths available"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find { it.paths.size() > 1 } ?:
                assumeTrue("No suiting switches found", false)
        List<FlowPayload> flows = []

        def newPriority = 300

        3.times {
            def flow = flowHelper.randomFlow(switchPair)
            flow.maximumBandwidth = 10000
            flow.allocateProtectedPath = protectedPath
            flow.priority = newPriority
            flowHelper.addFlow(flow)
            newPriority -= 100
            flows << flow
        }
        def currentPath = pathHelper.convert(northbound.getFlowPath(flows[0].id))
        //ensure all flows are on the same path
        assert pathHelper.convert(northbound.getFlowPath(flows[1].id)) == currentPath
        assert pathHelper.convert(northbound.getFlowPath(flows[2].id)) == currentPath

        when: "Make another path more preferable"
        def newPath = switchPair.paths.find { it != currentPath }
        switchPair.paths.findAll { it != newPath }.each { pathHelper.makePathMorePreferable(newPath, it) }

        and: "Init simultaneous reroute for all flows"
        def isl = pathHelper.getInvolvedIsls(currentPath).first()
        northbound.rerouteLinkFlows(isl.srcSwitch.dpId, isl.srcPort, isl.dstSwitch.dpId, isl.dstPort)

        then: "Flows were rerouted"
        Wrappers.wait(WAIT_OFFSET) {
            flows.each {
                assert northbound.getFlowStatus(it.id).status == FlowState.UP
                assert pathHelper.convert(northbound.getFlowPath(it.id)) == newPath
            }
        }

        and: "Reroute procedure was done based on the priority field"
        Wrappers.wait(WAIT_OFFSET) {
            flows.sort { it.priority }*.id == northbound.getAllFlows().sort { it.lastUpdated }*.id
        }
        and: "Cleanup: revert system to original state"
        flows.each { flowHelper.deleteFlow(it.id) }
        northbound.deleteLinkProps(northbound.getAllLinkProps())
        database.resetCosts()

        where:
        info                     | protectedPath
        "without protected path" | false
        "with protected path"    | true
    }

    List<Integer> getCreatedMeterIds(SwitchId switchId) {
        return northbound.getAllMeters(switchId).meterEntries.findAll { it.meterId > MAX_SYSTEM_RULE_METER_ID }*.meterId
    }
}
