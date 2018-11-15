package org.openkilda.functionaltests.spec.northbound.switches

import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.lockkeeper.LockKeeperService
import org.openkilda.testing.service.northbound.NorthboundService

import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative

import java.util.concurrent.TimeUnit

@Narrative("""Verify how Kilda behaves with switch rules (either flow rules or default rules) under different 
circumstances: e.g. persisting rules on newly connected switch, installing default rules on new switch etc.""")
class SwitchRulesSpec extends BaseSpecification {

    private static final FLOW_RULES_SIZE = 2

    @Autowired
    TopologyDefinition topology
    @Autowired
    NorthboundService northboundService
    @Autowired
    LockKeeperService lockKeeperService

    def "Default rules are installed on a new switch connected to the controller"() {
        requireProfiles("virtual")

        given: "A switch with no rules installed and not connected to the controller"
        def sw = topology.getActiveSwitches().first()
        def defaultRules = northboundService.getSwitchRules(sw.dpId).flowEntries.sort { it.cookie }
        northboundService.deleteSwitchRules(sw.dpId, DeleteRulesAction.DROP_ALL)
        Wrappers.wait(WAIT_OFFSET) { assert northboundService.getSwitchRules(sw.dpId).flowEntries.isEmpty() }

        lockKeeperService.knockoutSwitch(sw.dpId)
        Wrappers.wait(WAIT_OFFSET) { assert !(sw.dpId in northboundService.getActiveSwitches()*.switchId) }

        when: "Connect the switch to the controller"
        lockKeeperService.reviveSwitch(sw.dpId)
        Wrappers.wait(WAIT_OFFSET) { assert sw.dpId in northboundService.getActiveSwitches()*.switchId }

        then: "Default rules are installed on the switch"
        def actualRules = northboundService.getSwitchRules(sw.dpId).flowEntries.sort { it.cookie }
        actualRules.size() == defaultRules.size()
        [actualRules, defaultRules].transpose().each { actual, expected ->
            verifyAll {
                actual.cookie == expected.cookie
                actual.tableId == expected.tableId
                actual.version == expected.version
                actual.priority == expected.priority
                actual.idleTimeout == expected.idleTimeout
                actual.hardTimeout == expected.hardTimeout
                actual.match == expected.match
                actual.instructions == expected.instructions
                actual.flags == expected.flags
            }
        }
    }

    def "Pre-installed rules are not deleted from a new switch connected to the controller"() {
        requireProfiles("virtual")

        given: "A switch with some rules installed (including default) and not connected to the controller"
        def (Switch srcSwitch, Switch dstSwitch) = topology.getActiveSwitches()[0..1]
        def defaultRules = northboundService.getSwitchRules(srcSwitch.dpId).flowEntries.sort { it.cookie }

        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        northboundService.addFlow(flow)
        Wrappers.wait(WAIT_OFFSET) { assert northboundService.getFlowStatus(flow.id).status == FlowState.UP }

        def defaultPlusFlowRules = []
        Wrappers.wait(WAIT_OFFSET) {
            defaultPlusFlowRules = northboundService.getSwitchRules(srcSwitch.dpId).flowEntries.sort { it.cookie }
            assert defaultPlusFlowRules.size() == defaultRules.size() + FLOW_RULES_SIZE
        }

        lockKeeperService.knockoutSwitch(srcSwitch.dpId)
        Wrappers.wait(WAIT_OFFSET) { assert !(srcSwitch.dpId in northboundService.getActiveSwitches()*.switchId) }

        northboundService.deleteFlow(flow.id)
        Wrappers.wait(WAIT_OFFSET) { assert !(flow.id in northboundService.getAllFlows()*.id) }
        //TODO(ylobankov): Remove this dirty workaround once we add a helper method for flow deletion where we will
        // check the finish of delete operation
        TimeUnit.SECONDS.sleep(1)

        when: "Connect the switch to the controller"
        lockKeeperService.reviveSwitch(srcSwitch.dpId)
        Wrappers.wait(WAIT_OFFSET) { assert srcSwitch.dpId in northboundService.getActiveSwitches()*.switchId }

        then: "Previously installed rules are not deleted from the switch"
        def actualRules = northboundService.getSwitchRules(srcSwitch.dpId).flowEntries.sort { it.cookie }
        actualRules.size() == defaultPlusFlowRules.size()
        [actualRules, defaultPlusFlowRules].transpose().each { actual, expected ->
            verifyAll {
                actual.cookie == expected.cookie
                actual.tableId == expected.tableId
                actual.version == expected.version
                actual.priority == expected.priority
                actual.idleTimeout == expected.idleTimeout
                actual.hardTimeout == expected.hardTimeout
                actual.match == expected.match
                actual.instructions == expected.instructions
                actual.flags == expected.flags
            }
        }

        and: "Delete previously installed rules"
        northboundService.deleteSwitchRules(srcSwitch.dpId, DeleteRulesAction.IGNORE_DEFAULTS)
        Wrappers.wait(WAIT_OFFSET) {
            northboundService.getSwitchRules(srcSwitch.dpId).flowEntries.size() == defaultRules.size()
        }
    }
}
