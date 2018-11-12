package org.openkilda.functionaltests.spec.northbound.switches

import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.Constants.DefaultRule
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.lockkeeper.LockKeeperService
import org.openkilda.testing.service.northbound.NorthboundService

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import spock.lang.Ignore
import spock.lang.Unroll

class SwitchRulesSpec extends BaseSpecification {

    @Autowired
    TopologyDefinition topology
    @Autowired
    NorthboundService northboundService
    @Autowired
    LockKeeperService lockKeeperService

    @Value('${reroute.delay}')
    int rerouteDelay
    @Value('${discovery.timeout}')
    int discoveryTimeout

    @Unroll("Default rules are installed on #sw.ofVersion switch(#sw.dpId)")
    def "Default rules are installed on switches"() {
        expect: "Default rules are installed on the #sw.ofVersion switch"
        def cookies = northboundService.getSwitchRules(sw.dpId).flowEntries*.cookie
        cookies.sort() == expectedRules*.cookie.sort()

        where:
        sw << uniqueSwitches
        expectedRules = sw.ofVersion == "OF_12" ? [DefaultRule.VERIFICATION_BROADCAST_RULE] : DefaultRule.values()
    }

    @Unroll("Default rules are installed on a new #sw.ofVersion switch(#sw.dpId) when connecting it to the controller")
    def "Default rules are installed when a new switch is connected"() {
        requireProfiles("virtual")

        given: "A switch with no rules installed and not connected to the controller"
        northboundService.deleteSwitchRules(sw.dpId, DeleteRulesAction.DROP_ALL)
        Wrappers.wait(WAIT_OFFSET) { assert northboundService.getSwitchRules(sw.dpId).flowEntries.isEmpty() }

        lockKeeperService.knockoutSwitch(sw.dpId)
        Wrappers.wait(WAIT_OFFSET) { assert !(sw.dpId in northboundService.getActiveSwitches()*.switchId) }

        when: "Connect the switch to the controller"
        lockKeeperService.reviveSwitch(sw.dpId)

        then: "The switch is really connected to the controller"
        Wrappers.wait(WAIT_OFFSET) { sw.dpId in northboundService.getActiveSwitches()*.switchId }

        and: "Default rules are installed on the switch"
        def cookies = northboundService.getSwitchRules(sw.dpId).flowEntries*.cookie
        cookies.sort() == expectedRules*.cookie.sort()

        where:
        sw << uniqueSwitches
        expectedRules = sw.ofVersion == "OF_12" ? [DefaultRule.VERIFICATION_BROADCAST_RULE] : DefaultRule.values()
    }

    @Ignore("Test is skipped because of the issue #1464")
    def "Pre-installed rules are not deleted from a new switch when connecting it to the controller"() {
        // TODO(ylobankov): Also the current disconnection of the switch from the controller is not suitable for
        // this scenario. It is needed to add additional functionality to properly disconnect the switch.

        requireProfiles("virtual")

        given: "A switch with some rules installed and not connected to the controller"
        def sw = topology.getActiveSwitches().first()
        def defaultRulesSorted = northboundService.getSwitchRules(sw.dpId).flowEntries.sort { it.cookie }

        def flow = flowHelper.singleSwitchFlow(sw)
        northboundService.addFlow(flow)
        Wrappers.wait(WAIT_OFFSET) { assert northboundService.getFlowStatus(flow.id).status == FlowState.UP }

        def allRulesSorted = northboundService.getSwitchRules(sw.dpId).flowEntries.sort { it.cookie }
        assert allRulesSorted.size() > defaultRulesSorted.size()

        lockKeeperService.knockoutSwitch(sw.dpId)
        Wrappers.wait(discoveryTimeout + rerouteDelay + WAIT_OFFSET * 2) {
            assert northboundService.getFlowStatus(flow.id).status == FlowState.DOWN
        }

        northboundService.deleteFlow(flow.id)
        Wrappers.wait(WAIT_OFFSET) { assert !(flow.id in northboundService.getAllFlows()*.id) }

        when: "Connect the switch to the controller"
        lockKeeperService.reviveSwitch(sw.dpId)

        then: "The switch is really connected to the controller"
        Wrappers.wait(WAIT_OFFSET) { sw.dpId in northboundService.getActiveSwitches()*.switchId }

        and: "Previously installed rules are not deleted from the switch"
        northboundService.getSwitchRules(sw.dpId).flowEntries.sort { it.cookie }.size() == allRulesSorted.size()
    }

    List<Switch> getUniqueSwitches() {
        def nbSwitches = northbound.getAllSwitches()
        topology.getActiveSwitches()
                .unique { sw -> [nbSwitches.find { it.switchId == sw.dpId }.description, sw.ofVersion].sort() }
    }
}
