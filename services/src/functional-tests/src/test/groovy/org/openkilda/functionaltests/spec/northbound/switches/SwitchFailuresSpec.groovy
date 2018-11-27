package org.openkilda.functionaltests.spec.northbound.switches

import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.extension.fixture.rule.CleanupSwitches
import org.openkilda.functionaltests.helpers.FlowHelper
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.lockkeeper.LockKeeperService
import org.openkilda.testing.service.lockkeeper.model.ASwitchFlow
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.tools.IslUtils

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import spock.lang.Ignore
import spock.lang.Narrative

import java.util.concurrent.TimeUnit

@Narrative("""
This spec verifies different situations when Kilda switches suddenly disconnect from the controller.
Note: For now it is only runnable on virtual env due to no ability to disconnect hardware switches
""")
@CleanupSwitches
class SwitchFailuresSpec extends BaseSpecification {
    @Value('${spring.profiles.active}')
    String profile
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
    
    def setupOnce() {
        requireProfiles("virtual")        
    }

    def "ISL is still able to properly fail even after switches were reconnected"() {
        given: "A flow"
        def isl = topology.getIslsForActiveSwitches().find { it.aswitch && it.dstSwitch }
        def flow = flowHelper.randomFlow(isl.srcSwitch, isl.dstSwitch)
        northboundService.addFlow(flow)
        Wrappers.wait(WAIT_OFFSET) { assert northboundService.getFlowStatus(flow.id).status == FlowState.UP }

        when: "Two neighbouring switches of the flow go down simultaneously"
        lockKeeperService.knockoutSwitch(isl.srcSwitch.dpId)
        lockKeeperService.knockoutSwitch(isl.dstSwitch.dpId)
        def timeSwitchesBroke = System.currentTimeMillis()
        def untilIslShouldFail = { timeSwitchesBroke + discoveryTimeout * 1000 - System.currentTimeMillis() }

        and: "ISL between those switches looses connection"
        lockKeeperService.removeFlows([isl, islUtils.reverseIsl(isl)]
                .collect { new ASwitchFlow(it.aswitch.inPort, it.aswitch.outPort) })

        and: "Switches go back UP"
        lockKeeperService.reviveSwitch(isl.srcSwitch.dpId)
        lockKeeperService.reviveSwitch(isl.dstSwitch.dpId)

        then: "ISL still remains up right before discovery timeout should end"
        sleep(untilIslShouldFail() - 2000)
        islUtils.getIslInfo(isl).get().state == IslChangeType.DISCOVERED

        and: "ISL fails after discovery timeout"
        //TODO(rtretiak): adding big error of 7 seconds. This is an abnormal behavior, currently investigating
        Wrappers.wait(untilIslShouldFail() / 1000 + 7) {
            assert islUtils.getIslInfo(isl).get().state == IslChangeType.FAILED
        }

        //depends whether there are alt paths available
        and: "The flow goes down OR changes path to avoid failed ISL after reroute timeout"
        TimeUnit.SECONDS.sleep(rerouteDelay - 1)
        Wrappers.wait(WAIT_OFFSET) {
            def currentPath = PathHelper.convert(northboundService.getFlowPath(flow.id))
            assert !pathHelper.getInvolvedIsls(currentPath).contains(isl) ||
                    northboundService.getFlowStatus(flow.id).status == FlowState.DOWN
        }

        and: "Cleanup, restore connection, remove the flow"
        lockKeeperService.addFlows([isl, islUtils.reverseIsl(isl)]
                .collect { new ASwitchFlow(it.aswitch.inPort, it.aswitch.outPort) })
        flowHelper.deleteFlow(flow.id)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northboundService.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
    }


    @Ignore("This is a known Kilda limitation and feature is not implemented yet.")
    def "System can handle situation when switch reconnects while flow is being created"() {
        given: "Source and destination switches"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches

        when: "Start creating a flow between these switches"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        northboundService.addFlow(flow)

        and: "One of the switches goes down without waiting for flow's UP status"
        lockKeeperService.knockoutSwitch(srcSwitch.dpId)

        and: "Goes back up in a second"
        TimeUnit.SECONDS.sleep(1)
        lockKeeperService.reviveSwitch(srcSwitch.dpId)

        then: "The flow is UP and valid"
        Wrappers.wait(WAIT_OFFSET) {
            assert northboundService.getFlowStatus(flow.id).status == FlowState.UP
            northboundService.validateFlow(flow.id).each { direction ->
                assert direction.discrepancies.findAll { it.field != "meterId" }.empty
            }
        }

        and: "Rules are valid on the knocked out switch"
        verifySwitchRules(srcSwitch.dpId)

        and: "Remove the flow"
        flowHelper.deleteFlow(flow.id)
    }
}
