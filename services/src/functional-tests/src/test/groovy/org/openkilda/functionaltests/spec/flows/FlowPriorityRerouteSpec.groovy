package org.openkilda.functionaltests.spec.flows

import static org.junit.Assume.assumeTrue
import static org.openkilda.model.MeterId.MAX_SYSTEM_RULE_METER_ID
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2

import spock.lang.Ignore
import spock.lang.Unroll

class FlowPriorityRerouteSpec extends HealthCheckSpecification {

    def "System is able to reroute(automatically) flow in the correct order based on the priority field"() {
        given: "Three flows on the same path, with alt paths available"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find { it.paths.size() > 1 } ?:
                assumeTrue("No suiting switches found", false)
        List<FlowRequestV2> flows = []

        def newPriority = 300

        3.times {
            def flow = flowHelperV2.randomFlow(switchPair)
            flow.maximumBandwidth = 10000
            flow.allocateProtectedPath = true
            flow.priority = newPriority
            flowHelperV2.addFlow(flow)
            newPriority -= 100
            flows << flow
        }

        def currentPath = pathHelper.convert(northbound.getFlowPath(flows[0].flowId))
        //ensure all flows are on the same path
        assert pathHelper.convert(northbound.getFlowPath(flows[1].flowId)) == currentPath
        assert pathHelper.convert(northbound.getFlowPath(flows[2].flowId)) == currentPath

        def altPath = switchPair.paths.find { it != currentPath }

        when: "Init simultaneous reroute for all flows by bringing current path's ISL down"
        def currentIsls = pathHelper.getInvolvedIsls(currentPath)
        def newIsls = pathHelper.getInvolvedIsls(altPath)
        def islToBreak = currentIsls.find { !newIsls.contains(it) }
        antiflap.portDown(islToBreak.srcSwitch.dpId, islToBreak.srcPort)

        then: "Flows were rerouted"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            flows.each {
                assert northbound.getFlowStatus(it.flowId).status == FlowState.UP
                assert pathHelper.convert(northbound.getFlowPath(it.flowId)) != currentPath
            }
        }

        and: "Reroute procedure was done based on the priority field"
        // for a flow with protected path we use a little bit different logic for rerouting then for simple flow
        // that's why we use WAIT_OFFSET here
        Wrappers.wait(WAIT_OFFSET) {
            assert flows.sort { it.priority }*.flowId == flows.sort {
                def rerouteAction = northbound.getFlowHistory(it.flowId).last()
                assert rerouteAction.action == "Flow rerouting"
                return rerouteAction.timestamp
            }*.flowId
        }

        and: "Cleanup: revert system to original state"
        antiflap.portUp(islToBreak.srcSwitch.dpId, islToBreak.srcPort)
        flows.each { flowHelperV2.deleteFlow(it.flowId) }
        Wrappers.wait(WAIT_OFFSET + discoveryInterval) {
            assert islUtils.getIslInfo(islToBreak).get().state == IslChangeType.DISCOVERED
        }
        database.resetCosts()
    }

    @Ignore("https://github.com/telstra/open-kilda/issues/2211")
    @Unroll
    def "System is able to reroute(intentional) flow in the correct order based on the priority field"() {
        given: "Three flows on the same path, with alt paths available"
        def switchPair = topologyHelper.getAllNeighboringSwitchPairs().find { it.paths.size() > 1 } ?:
                assumeTrue("No suiting switches found", false)
        List<FlowRequestV2> flows = []

        def newPriority = 300

        3.times {
            def flow = flowHelperV2.randomFlow(switchPair)
            flow.maximumBandwidth = 10000
            flow.allocateProtectedPath = protectedPath
            flow.priority = newPriority
            flowHelperV2.addFlow(flow)
            newPriority -= 100
            flows << flow
        }
        def currentPath = pathHelper.convert(northbound.getFlowPath(flows[0].flowId))
        //ensure all flows are on the same path
        assert pathHelper.convert(northbound.getFlowPath(flows[1].flowId)) == currentPath
        assert pathHelper.convert(northbound.getFlowPath(flows[2].flowId)) == currentPath

        when: "Make another path more preferable"
        def newPath = switchPair.paths.find { it != currentPath }
        switchPair.paths.findAll { it != newPath }.each { pathHelper.makePathMorePreferable(newPath, it) }

        and: "Init simultaneous reroute for all flows"
        def isl = pathHelper.getInvolvedIsls(currentPath).first()
        northbound.rerouteLinkFlows(isl.srcSwitch.dpId, isl.srcPort, isl.dstSwitch.dpId, isl.dstPort)

        then: "Flows were rerouted"
        Wrappers.wait(WAIT_OFFSET) {
            flows.each {
                assert northbound.getFlowStatus(it.flowId).status == FlowState.UP
                assert pathHelper.convert(northbound.getFlowPath(it.flowId)) == newPath
            }
        }

        and: "Reroute procedure was done based on the priority field"
        Wrappers.wait(WAIT_OFFSET) {
            assert flows.sort { it.priority }*.flowId == flows.sort {
                def rerouteAction = northbound.getFlowHistory(it.flowId).last()
                assert rerouteAction.action == "Flow reroute"
                return rerouteAction.timestamp
            }*.flowId
        }
        and: "Cleanup: revert system to original state"
        flows.each { flowHelperV2.deleteFlow(it.flowId) }
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
