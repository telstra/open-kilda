package org.openkilda.functionaltests.spec.northbound.switches

import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.FlowHelper
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.lockkeeper.LockKeeperService
import org.openkilda.testing.service.lockkeeper.model.ASwitchFlow
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.tools.IslUtils

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import spock.lang.Narrative

import java.util.concurrent.TimeUnit

@Narrative("""
This spec verifies different situations when Kilda switches suddenly disconnect from the controller.
Note: For now it is only runnable on virtual env due to no ability to disconnect hardware switches
""")
class SwitchFailuresSpec extends BaseSpecification {
    @Value('${spring.profiles.active}')
    String profile
    @Value('${floodlight.endpoint}')
    String floodlightEndpoint //TODO(rtretiak): For correct hardware impl should point to actual 6653 port instead
    @Value('${discovery.timeout}')
    int discoveryTimeout
    @Value('${reroute.delay}')
    int rerouteDelay
    @Value('${discovery.interval}')
    int discoveryInterval

    @Autowired
    TopologyDefinition topology
    @Autowired
    FlowHelper flowHelper
    @Autowired
    NorthboundService northboundService
    @Autowired
    LockKeeperService lockKeeperService
    @Autowired
    IslUtils islUtils
    @Autowired
    PathHelper pathHelper

    def "ISL is still able to properly fail even after switches where reconnected"() {
        requireProfiles("virtual")

        given: "A flow"
        def isl = topology.getIslsForActiveSwitches().find { it.aswitch && it.dstSwitch }
        def flow = flowHelper.randomFlow(isl.srcSwitch, isl.dstSwitch)
        northboundService.addFlow(flow)
        assert Wrappers.wait(WAIT_OFFSET) { northboundService.getFlowStatus(flow.id).status == FlowState.UP }

        when: "Two neighbouring switches of the flow go down simultaneously"
        lockKeeperService.knockoutSwitch(isl.srcSwitch.dpId.toString())
        lockKeeperService.knockoutSwitch(isl.dstSwitch.dpId.toString())
        def timeSwitchesBroke = System.currentTimeMillis()
        def untilIslShouldFail = { timeSwitchesBroke + discoveryTimeout * 1000 - System.currentTimeMillis() }

        and: "ISL between those switches looses connection"
        lockKeeperService.removeFlows([isl, islUtils.reverseIsl(isl)]
                .collect { new ASwitchFlow(it.aswitch.inPort, it.aswitch.outPort) })

        and: "Switches go back UP"
        lockKeeperService.reviveSwitch(isl.srcSwitch.dpId.toString(), floodlightEndpoint)
        lockKeeperService.reviveSwitch(isl.dstSwitch.dpId.toString(), floodlightEndpoint)

        then: "ISL still remains up right before discovery timeout should end"
        sleep(untilIslShouldFail() - 2000)
        islUtils.getIslInfo(isl).get().state == IslChangeType.DISCOVERED

        and: "ISL fails after discovery timeout"
        //TODO(rtretiak): adding big error of 7 seconds. This is an abnormal behavior, currently investigating
        Wrappers.wait(untilIslShouldFail() / 1000 + 7) {
            islUtils.getIslInfo(isl).get().state == IslChangeType.FAILED
        }

        //depends whether there are alt paths available
        and: "Flow goes down OR changes path to avoid failed ISL after reroute timeout"
        TimeUnit.SECONDS.sleep(rerouteDelay - 1)
        Wrappers.wait(WAIT_OFFSET) {
            def currentPath = PathHelper.convert(northboundService.getFlowPath(flow.id))
            !pathHelper.getInvolvedIsls(currentPath).contains(isl) ||
                    northboundService.getFlowStatus(flow.id).status == FlowState.DOWN
        }

        and: "Cleanup, restore connection, remove flow"
        lockKeeperService.addFlows([isl, islUtils.reverseIsl(isl)]
                .collect { new ASwitchFlow(it.aswitch.inPort, it.aswitch.outPort) })
        northboundService.deleteFlow(flow.id)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northboundService.getAllLinks().every { it.state != IslChangeType.FAILED }
        }
    }
}
