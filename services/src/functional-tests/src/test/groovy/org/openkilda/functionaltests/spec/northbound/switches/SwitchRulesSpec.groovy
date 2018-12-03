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
import org.openkilda.messaging.info.rule.FlowEntry
import org.openkilda.messaging.payload.flow.FlowPayload
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
    List srcSwDefaultRules
    @Shared
    List dstSwDefaultRules
    @Shared
    int flowRulesCount = 2

    def setup() {
        (srcSwitch, dstSwitch) = topology.getActiveSwitches()[0..1]
        srcSwDefaultRules = northboundService.getSwitchRules(srcSwitch.dpId).flowEntries
        dstSwDefaultRules = northboundService.getSwitchRules(dstSwitch.dpId).flowEntries
    }

    @Unroll("Default rules are installed on an #sw.ofVersion switch(#sw.dpId)")
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
        flowHelper.addFlow(flow)

        def defaultPlusFlowRules = []
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            defaultPlusFlowRules = northboundService.getSwitchRules(srcSwitch.dpId).flowEntries
            assert defaultPlusFlowRules.size() == srcSwDefaultRules.size() + flowRulesCount
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
            assert northboundService.getSwitchRules(srcSwitch.dpId).flowEntries.size() == srcSwDefaultRules.size()
        }
    }

    @Unroll
    def "Able to delete rules from a switch (delete-action=#data.deleteRulesAction)"() {
        given: "A switch with some flow rules installed"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow)

        when: "Delete rules from the switch"
        def flowRules = getFlowRules(srcSwitch)
        def deletedRules = northboundService.deleteSwitchRules(srcSwitch.dpId, data.deleteRulesAction)
        def expectedRules = srcSwDefaultRules
        switch (data.deleteRulesAction) {
            case DeleteRulesAction.REMOVE_DEFAULTS:
                expectedRules = flowRules
                break
            case DeleteRulesAction.REMOVE_ADD_DEFAULTS:
                expectedRules += flowRules
                break
        }

        then: "The corresponding rules are really deleted"
        deletedRules.size() == data.rulesDeleted
        Wrappers.wait(RULES_DELETION_TIME) {
            def actualRules = northboundService.getSwitchRules(srcSwitch.dpId).flowEntries
            assert actualRules.size() == data.rulesRemained
            data.deleteRulesAction == DeleteRulesAction.DROP_ALL ?: compareRules(actualRules, expectedRules)
        }

        and: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        and: "Install default rules if necessary"
        if (data.deleteRulesAction in [DeleteRulesAction.DROP_ALL, DeleteRulesAction.REMOVE_DEFAULTS]) {
            northboundService.installSwitchRules(srcSwitch.dpId, InstallRulesAction.INSTALL_DEFAULTS)
            Wrappers.wait(RULES_INSTALLATION_TIME) {
                assert northboundService.getSwitchRules(srcSwitch.dpId).flowEntries.size() == srcSwDefaultRules.size()
            }
        }

        where:
        data << [[// Drop all rules
                  deleteRulesAction: DeleteRulesAction.DROP_ALL,
                  rulesDeleted     : srcSwDefaultRules.size() + flowRulesCount,
                  rulesRemained    : 0,
                 ],
                 [// Drop all rules, add back in the base default rules
                  deleteRulesAction: DeleteRulesAction.DROP_ALL_ADD_DEFAULTS,
                  rulesDeleted     : srcSwDefaultRules.size() + flowRulesCount,
                  rulesRemained    : srcSwDefaultRules.size(),
                 ],
                 [// Don't drop the default rules, but do drop everything else
                  deleteRulesAction: DeleteRulesAction.IGNORE_DEFAULTS,
                  rulesDeleted     : flowRulesCount,
                  rulesRemained    : srcSwDefaultRules.size(),
                 ],
                 [// Drop all non-base rules (ie IGNORE), and add base rules back (eg overwrite)
                  deleteRulesAction: DeleteRulesAction.OVERWRITE_DEFAULTS,
                  rulesDeleted     : flowRulesCount,
                  rulesRemained    : srcSwDefaultRules.size(),
                 ],
                 [// Drop all default rules
                  deleteRulesAction: DeleteRulesAction.REMOVE_DEFAULTS,
                  rulesDeleted     : srcSwDefaultRules.size(),
                  rulesRemained    : flowRulesCount,
                 ],
                 [// Drop the default, add them back
                  deleteRulesAction: DeleteRulesAction.REMOVE_ADD_DEFAULTS,
                  rulesDeleted     : srcSwDefaultRules.size(),
                  rulesRemained    : srcSwDefaultRules.size() + flowRulesCount,
                 ]
        ]
    }

    @Unroll("Able to delete switch rules by #data.description")
    def "Able to delete switch rules by inPort, inVlan, inPort and inVlan, outPort"() {
        given: "A switch with some flow rules installed"
        flowHelper.addFlow(flow)

        when: "Delete switch rules by #data.description"
        def deletedRules = northboundService.deleteSwitchRules(data.switch.dpId, data.inPort, data.inVlan, data.outPort)

        then: "The requested rules are really deleted"
        deletedRules.size() == 1
        Wrappers.wait(RULES_DELETION_TIME) {
            def actualRules = northboundService.getSwitchRules(data.switch.dpId).flowEntries
            assert actualRules.size() == data.defaultRules.size() + flowRulesCount - 1
            assert actualRules.findAll { it.cookie in deletedRules }.empty
            assert filterRules(actualRules, data.inPort, data.inVlan, data.outPort).empty
        }

        and: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        where:
        flow << [buildFlow()] * 4
        data << [[description : "inPort",
                  switch      : srcSwitch,
                  defaultRules: srcSwDefaultRules,
                  inPort      : flow.source.portNumber,
                  inVlan      : null,
                  outPort     : null
                 ],
                 [description : "inVlan",
                  switch      : srcSwitch,
                  defaultRules: srcSwDefaultRules,
                  inPort      : null,
                  inVlan      : flow.source.vlanId,
                  outPort     : null
                 ],
                 [description : "inPort and inVlan",
                  switch      : srcSwitch,
                  defaultRules: srcSwDefaultRules,
                  inPort      : flow.source.portNumber,
                  inVlan      : flow.source.vlanId,
                  outPort     : null
                 ],
                 [description : "outPort",
                  switch      : dstSwitch,
                  defaultRules: dstSwDefaultRules,
                  inPort      : null,
                  inVlan      : null,
                  outPort     : flow.destination.portNumber
                 ]
        ]
    }

    @Unroll("Able to delete switch rules by #data.description")
    def "Able to delete switch rules by cookie, priority"() {
        given: "A switch with some flow rules installed"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow)

        when: "Delete switch rules by #data.description"
        def deletedRules = northboundService.deleteSwitchRules(data.switch.dpId,
                getFlowRules(data.switch).first()."${data.description}")

        then: "The requested rules are really deleted"
        deletedRules.size() == data.rulesDeleted
        Wrappers.wait(RULES_DELETION_TIME) {
            def actualRules = northboundService.getSwitchRules(data.switch.dpId).flowEntries
            assert actualRules.size() == data.defaultRules.size() + flowRulesCount - data.rulesDeleted
            assert actualRules.findAll { it.cookie in deletedRules }.empty
        }

        and: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        where:
        data << [[description : "cookie",
                  switch      : srcSwitch,
                  defaultRules: srcSwDefaultRules,
                  rulesDeleted: 1
                 ],
                 [description : "priority",
                  switch      : dstSwitch,
                  defaultRules: dstSwDefaultRules,
                  rulesDeleted: 2
                 ]
        ]
    }

    @Unroll
    def "Attempt to delete switch rules by supplying non-existing #data.description leaves all rules intact"() {
        given: "A switch with some flow rules installed"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow)

        when: "Delete switch rules by non-existing #data.description"
        def deletedRules = northboundService.deleteSwitchRules(data.switch.dpId, data.inPort, data.inVlan, data.outPort)

        then: "All rules are kept intact"
        deletedRules.size() == 0
        northboundService.getSwitchRules(data.switch.dpId).flowEntries.size() ==
                data.defaultRules.size() + flowRulesCount

        and: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        where:
        data << [[description : "inPort",
                  switch      : srcSwitch,
                  defaultRules: srcSwDefaultRules,
                  inPort      : Integer.MAX_VALUE - 1,
                  inVlan      : null,
                  outPort     : null
                 ],
                 [description : "inVlan",
                  switch      : srcSwitch,
                  defaultRules: srcSwDefaultRules,
                  inPort      : null,
                  inVlan      : 4095,
                  outPort     : null
                 ],
                 [description : "inPort and inVlan",
                  switch      : srcSwitch,
                  defaultRules: srcSwDefaultRules,
                  inPort      : Integer.MAX_VALUE - 1,
                  inVlan      : 4095,
                  outPort     : null
                 ],
                 [description : "outPort",
                  switch      : dstSwitch,
                  defaultRules: dstSwDefaultRules,
                  inPort      : null,
                  inVlan      : null,
                  outPort     : Integer.MAX_VALUE - 1
                 ]
        ]
    }

    def "Able to synchronize rules on a switch (install missing rules)"() {
        given: "A switch with missing rules"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow)

        def defaultPlusFlowRules = northboundService.getSwitchRules(srcSwitch.dpId).flowEntries
        northboundService.deleteSwitchRules(srcSwitch.dpId, DeleteRulesAction.IGNORE_DEFAULTS)
        Wrappers.wait(RULES_DELETION_TIME) {
            assert northboundService.getSwitchRules(srcSwitch.dpId).flowEntries.size() == srcSwDefaultRules.size()
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

    FlowPayload buildFlow() {
        flowHelper.randomFlow(srcSwitch, dstSwitch)
    }

    List<FlowEntry> getFlowRules(Switch sw) {
        def defaultCookies = (sw.ofVersion == "OF_12" ? [DefaultRule.VERIFICATION_BROADCAST_RULE] :
                DefaultRule.values())*.cookie
        northboundService.getSwitchRules(sw.dpId).flowEntries.findAll { !(it.cookie in defaultCookies) }.sort()
    }

    List<FlowEntry> filterRules(List<FlowEntry> rules, inPort, inVlan, outPort) {
        if (inPort) {
            rules = rules.findAll { it.match.inPort == inPort.toString() }
        }
        if (inVlan) {
            rules = rules.findAll { it.match.vlanVid == inVlan.toString() }
        }
        if (outPort) {
            rules = rules.findAll { it.instructions?.applyActions?.flowOutput == outPort.toString() }
        }

        return rules
    }
}
