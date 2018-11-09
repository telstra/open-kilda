package org.openkilda.functionaltests.spec.northbound.switches

import static org.openkilda.testing.Constants.RULES_DELETION_TIME
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
import spock.lang.Shared
import spock.lang.Unroll

@Narrative("""Verify how Kilda behaves with switch rules (either flow rules or default rules) under different 
circumstances: e.g. persisting rules on newly connected switch, installing default rules on new switch etc.""")
class SwitchRulesSpec extends BaseSpecification {
    @Autowired
    TopologyDefinition topology
    @Autowired
    NorthboundService northboundService
    @Autowired
    LockKeeperService lockKeeperService

    @Shared
    Switch srcSwitch, dstSwitch
    @Shared
    List defaultRules
    @Shared
    int flowRulesCount = 2

    def setup() {
        (srcSwitch, dstSwitch) = topology.getActiveSwitches()[0..1]
        defaultRules = northboundService.getSwitchRules(srcSwitch.dpId).flowEntries.sort { it.cookie }
    }

    def "Default rules are installed on a new switch connected to the controller"() {
        requireProfiles("virtual")

        given: "A switch with no rules installed and not connected to the controller"
        northboundService.deleteSwitchRules(srcSwitch.dpId, DeleteRulesAction.DROP_ALL)
        Wrappers.wait(RULES_DELETION_TIME) {
            assert northboundService.getSwitchRules(srcSwitch.dpId).flowEntries.isEmpty()
        }

        lockKeeperService.knockoutSwitch(srcSwitch.dpId)
        Wrappers.wait(WAIT_OFFSET) { assert !(srcSwitch.dpId in northboundService.getActiveSwitches()*.switchId) }

        when: "Connect the switch to the controller"
        lockKeeperService.reviveSwitch(srcSwitch.dpId)
        Wrappers.wait(WAIT_OFFSET) { assert srcSwitch.dpId in northboundService.getActiveSwitches()*.switchId }

        then: "Default rules are installed on the switch"
        def actualRules = northboundService.getSwitchRules(srcSwitch.dpId).flowEntries.sort { it.cookie }
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
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        northboundService.addFlow(flow)
        Wrappers.wait(WAIT_OFFSET) { assert northboundService.getFlowStatus(flow.id).status == FlowState.UP }

        def defaultPlusFlowRules = []
        Wrappers.wait(WAIT_OFFSET) {
            defaultPlusFlowRules = northboundService.getSwitchRules(srcSwitch.dpId).flowEntries.sort { it.cookie }
            assert defaultPlusFlowRules.size() == defaultRules.size() + flowRulesCount
        }

        lockKeeperService.knockoutSwitch(srcSwitch.dpId)
        Wrappers.wait(WAIT_OFFSET) { assert !(srcSwitch.dpId in northboundService.getActiveSwitches()*.switchId) }
        flowHelper.deleteFlow(flow.id)

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
        Wrappers.wait(RULES_DELETION_TIME) {
            northboundService.getSwitchRules(srcSwitch.dpId).flowEntries.size() == defaultRules.size()
        }
    }

    @Unroll
    def "Able to delete #data.description rules from a switch"() {
        given: "A switch with some flow rules installed"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        northboundService.addFlow(flow)
        Wrappers.wait(WAIT_OFFSET) {
            assert northboundService.getFlowStatus(flow.id).status == FlowState.UP &&
                    northboundService.getSwitchRules(srcSwitch.dpId).flowEntries.size() ==
                    defaultRules.size() + flowRulesCount
        }

        when: "Delete #data.description rules from the switch"
        def deletedRules = northboundService.deleteSwitchRules(srcSwitch.dpId, data.deleteRulesAction)

        then: "#data.description.capitalize() rules are really deleted"
        deletedRules.size() == data.rulesDeleted
        Wrappers.wait(RULES_DELETION_TIME) {
            northboundService.getSwitchRules(srcSwitch.dpId).flowEntries.size() == data.rulesRemained
        }

        and: "Delete the flow"
        northboundService.deleteFlow(flow.id)

        where:
        data << [[description      : "non-default",
                  deleteRulesAction: DeleteRulesAction.IGNORE_DEFAULTS,
                  rulesDeleted     : flowRulesCount,
                  rulesRemained    : defaultRules.size()
                 ],
                 [description      : "all",
                  deleteRulesAction: DeleteRulesAction.DROP_ALL,
                  rulesDeleted     : defaultRules.size() + flowRulesCount,
                  rulesRemained    : 0
                 ]
        ]
    }

    def "Able to synchronize rules on a switch (install missing rules)"() {
        given: "A switch with missing rules"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        northboundService.addFlow(flow)
        Wrappers.wait(WAIT_OFFSET) {
            assert northboundService.getFlowStatus(flow.id).status == FlowState.UP &&
                    northboundService.getSwitchRules(srcSwitch.dpId).flowEntries.size() ==
                    defaultRules.size() + flowRulesCount
        }

        def defaultPlusFlowRules = northboundService.getSwitchRules(srcSwitch.dpId).flowEntries.sort { it.cookie }
        northboundService.deleteSwitchRules(srcSwitch.dpId, DeleteRulesAction.IGNORE_DEFAULTS)
        Wrappers.wait(RULES_DELETION_TIME) {
            assert northboundService.getSwitchRules(srcSwitch.dpId).flowEntries.size() == defaultRules.size()
        }
        assert northboundService.validateSwitchRules(srcSwitch.dpId).missingRules.size() == flowRulesCount

        when: "Synchronize rules on the switch"
        def synchronizedRules = northboundService.synchronizeSwitchRules(srcSwitch.dpId)

        then: "The corresponding rules are installed on the switch"
        synchronizedRules.installedRules.size() == flowRulesCount

        and: "No missing rules were found after rules validation"
        Wrappers.wait(WAIT_OFFSET) {
            with(northboundService.validateSwitchRules(srcSwitch.dpId)) {
                verifyAll {
                    properRules.size() == flowRulesCount
                    missingRules.size() == 0
                    excessRules.size() == 0
                }
            }
        }

        and: "Actual rules are equal to previous ones"
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

        and: "Delete the flow"
        northboundService.deleteFlow(flow.id)
    }
}
