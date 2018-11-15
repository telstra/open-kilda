package org.openkilda.functionaltests.spec.northbound.switches

import static com.shazam.shazamcrest.matcher.Matchers.sameBeanAs
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static spock.util.matcher.HamcrestSupport.expect

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.messaging.command.switches.InstallRulesAction
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.Constants.DefaultRule
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
        defaultRules = northboundService.getSwitchRules(srcSwitch.dpId).flowEntries
    }
    
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
        Wrappers.wait(RULES_DELETION_TIME) { assert northboundService.getSwitchRules(sw.dpId).flowEntries.isEmpty() }

        lockKeeperService.knockoutSwitch(sw.dpId)
        Wrappers.wait(WAIT_OFFSET) { assert !(sw.dpId in northboundService.getActiveSwitches()*.switchId) }

        when: "Connect the switch to the controller"
        lockKeeperService.reviveSwitch(sw.dpId)
        Wrappers.wait(WAIT_OFFSET) { assert sw.dpId in northboundService.getActiveSwitches()*.switchId }

        then: "Default rules are installed on the switch"
        def cookies = northboundService.getSwitchRules(sw.dpId).flowEntries*.cookie
        cookies.sort() == expectedRules*.cookie.sort()

        where:
        sw << uniqueSwitches
        expectedRules = sw.ofVersion == "OF_12" ? [DefaultRule.VERIFICATION_BROADCAST_RULE] : DefaultRule.values()
    }

    def "Pre-installed rules are not deleted from a new switch connected to the controller"() {
        requireProfiles("virtual")

        given: "A switch with some rules installed (including default) and not connected to the controller"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        northboundService.addFlow(flow)
        Wrappers.wait(WAIT_OFFSET) { assert northboundService.getFlowStatus(flow.id).status == FlowState.UP }

        def defaultPlusFlowRules = []
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            defaultPlusFlowRules = northboundService.getSwitchRules(srcSwitch.dpId).flowEntries
            assert defaultPlusFlowRules.size() == defaultRules.size() + flowRulesCount
        }

        lockKeeperService.knockoutSwitch(srcSwitch.dpId)
        Wrappers.wait(WAIT_OFFSET) { assert !(srcSwitch.dpId in northboundService.getActiveSwitches()*.switchId) }
        flowHelper.deleteFlow(flow.id)

        when: "Connect the switch to the controller"
        lockKeeperService.reviveSwitch(srcSwitch.dpId)
        Wrappers.wait(WAIT_OFFSET) { assert srcSwitch.dpId in northboundService.getActiveSwitches()*.switchId }

        then: "Previously installed rules are not deleted from the switch"
        compareRules(northboundService.getSwitchRules(srcSwitch.dpId).flowEntries, defaultPlusFlowRules)

        and: "Delete previously installed rules"
        northboundService.deleteSwitchRules(srcSwitch.dpId, DeleteRulesAction.IGNORE_DEFAULTS)
        Wrappers.wait(RULES_DELETION_TIME) {
            assert northboundService.getSwitchRules(srcSwitch.dpId).flowEntries.size() == defaultRules.size()
        }
    }

    @Unroll
    def "Able to delete #data.description rules from a switch"() {
        given: "A switch with some flow rules installed"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        northboundService.addFlow(flow)
        Wrappers.wait(WAIT_OFFSET) {
            assert northboundService.getFlowStatus(flow.id).status == FlowState.UP
            assert northboundService.getSwitchRules(srcSwitch.dpId).flowEntries.size() ==
                    defaultRules.size() + flowRulesCount
        }

        when: "Delete #data.description rules from the switch"
        def deletedRules = northboundService.deleteSwitchRules(srcSwitch.dpId, data.deleteRulesAction)

        then: "#data.description.capitalize() rules are really deleted"
        deletedRules.size() == data.rulesDeleted
        Wrappers.wait(RULES_DELETION_TIME) {
            assert northboundService.getSwitchRules(srcSwitch.dpId).flowEntries.size() == data.rulesRemained
        }

        and: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        and: "Install default rules if necessary"
        if (data.rulesRemained == 0) {
            northboundService.installSwitchRules(srcSwitch.dpId, InstallRulesAction.INSTALL_DEFAULTS)
            Wrappers.wait(RULES_INSTALLATION_TIME) {
                assert northboundService.getSwitchRules(srcSwitch.dpId).flowEntries.size() == defaultRules.size()
            }
        }

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
            assert northboundService.getFlowStatus(flow.id).status == FlowState.UP
            assert northboundService.getSwitchRules(srcSwitch.dpId).flowEntries.size() ==
                    defaultRules.size() + flowRulesCount
        }

        def defaultPlusFlowRules = northboundService.getSwitchRules(srcSwitch.dpId).flowEntries
        northboundService.deleteSwitchRules(srcSwitch.dpId, DeleteRulesAction.IGNORE_DEFAULTS)
        Wrappers.wait(RULES_DELETION_TIME) {
            assert northboundService.getSwitchRules(srcSwitch.dpId).flowEntries.size() == defaultRules.size()
        }
        assert northboundService.validateSwitchRules(srcSwitch.dpId).missingRules.size() == flowRulesCount

        when: "Synchronize rules on the switch"
        def synchronizedRules = northboundService.synchronizeSwitchRules(srcSwitch.dpId)

        then: "The corresponding rules are installed on the switch"
        synchronizedRules.installedRules.size() == flowRulesCount
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            compareRules(northboundService.getSwitchRules(srcSwitch.dpId).flowEntries, defaultPlusFlowRules)
        }

        and: "No missing rules were found after rules validation"
        with(northboundService.validateSwitchRules(srcSwitch.dpId)) {
            verifyAll {
                properRules.size() == flowRulesCount
                missingRules.empty
                excessRules.empty
            }
        }

        and: "Delete the flow"
        flowHelper.deleteFlow(flow.id)
    }

    void compareRules(actualRules, expectedRules) {
        assert expect(actualRules.sort { it.cookie }, sameBeanAs(expectedRules.sort { it.cookie })
                .ignoring("byteCount")
                .ignoring("packetCount")
                .ignoring("durationNanoSeconds")
                .ignoring("durationSeconds"))
    }

    List<Switch> getUniqueSwitches() {
        def nbSwitches = northbound.getAllSwitches()
        topology.getActiveSwitches()
                .unique { sw -> [nbSwitches.find { it.switchId == sw.dpId }.description, sw.ofVersion].sort() }
    }
}
