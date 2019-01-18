package org.openkilda.functionaltests.spec.northbound.switches

import static com.shazam.shazamcrest.matcher.Matchers.sameBeanAs
import static org.junit.Assume.assumeFalse
import static org.junit.Assume.assumeTrue
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
import org.openkilda.model.SwitchId
import org.openkilda.testing.Constants.DefaultRule
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import spock.lang.Narrative
import spock.lang.Shared
import spock.lang.Unroll

@Narrative("""Verify how Kilda behaves with switch rules (either flow rules or default rules) under different 
circumstances: e.g. persisting rules on newly connected switch, installing default rules on new switch etc.""")
class SwitchRulesSpec extends BaseSpecification {

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
        srcSwDefaultRules = northbound.getSwitchRules(srcSwitch.dpId).flowEntries
        dstSwDefaultRules = northbound.getSwitchRules(dstSwitch.dpId).flowEntries
    }

    @Unroll("Default rules are installed on an #sw.ofVersion switch(#sw.dpId)")
    def "Default rules are installed on switches"() {
        expect: "Default rules are installed on the #sw.ofVersion switch"
        def cookies = northbound.getSwitchRules(sw.dpId).flowEntries*.cookie
        cookies.sort() == expectedRules*.cookie.sort()

        where:
        sw << uniqueSwitches
        expectedRules = sw.ofVersion == "OF_12" ? [DefaultRule.VERIFICATION_BROADCAST_RULE] : DefaultRule.values()
    }

    @Unroll("Default rules are installed on a new #sw.ofVersion switch(#sw.dpId) when connecting it to the controller")
    def "Default rules are installed when a new switch is connected"() {
        requireProfiles("virtual")

        given: "A switch with no rules installed and not connected to the controller"
        northbound.deleteSwitchRules(sw.dpId, DeleteRulesAction.DROP_ALL)
        Wrappers.wait(RULES_DELETION_TIME) { assert northbound.getSwitchRules(sw.dpId).flowEntries.isEmpty() }

        lockKeeper.knockoutSwitch(sw.dpId)
        Wrappers.wait(WAIT_OFFSET) { assert !(sw.dpId in northbound.getActiveSwitches()*.switchId) }

        when: "Connect the switch to the controller"
        lockKeeper.reviveSwitch(sw.dpId)
        Wrappers.wait(WAIT_OFFSET) { assert sw.dpId in northbound.getActiveSwitches()*.switchId }

        then: "Default rules are installed on the switch"
        def cookies = northbound.getSwitchRules(sw.dpId).flowEntries*.cookie
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
            defaultPlusFlowRules = northbound.getSwitchRules(srcSwitch.dpId).flowEntries
            assert defaultPlusFlowRules.size() == srcSwDefaultRules.size() + flowRulesCount
        }

        lockKeeper.knockoutSwitch(srcSwitch.dpId)
        Wrappers.wait(WAIT_OFFSET) { assert !(srcSwitch.dpId in northbound.getActiveSwitches()*.switchId) }
        flowHelper.deleteFlow(flow.id)

        when: "Connect the switch to the controller"
        lockKeeper.reviveSwitch(srcSwitch.dpId)
        Wrappers.wait(WAIT_OFFSET) { assert srcSwitch.dpId in northbound.getActiveSwitches()*.switchId }

        then: "Previously installed rules are not deleted from the switch"
        compareRules(northbound.getSwitchRules(srcSwitch.dpId).flowEntries, defaultPlusFlowRules)

        and: "Delete previously installed rules"
        northbound.deleteSwitchRules(srcSwitch.dpId, DeleteRulesAction.IGNORE_DEFAULTS)
        Wrappers.wait(RULES_DELETION_TIME) {
            assert northbound.getSwitchRules(srcSwitch.dpId).flowEntries.size() == srcSwDefaultRules.size()
        }
    }

    @Unroll
    def "Able to delete rules from a switch (delete-action=#data.deleteRulesAction)"() {
        given: "A switch with some flow rules installed"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow)

        when: "Delete rules from the switch"
        def expectedRules = data.getExpectedRules(srcSwitch.dpId, srcSwDefaultRules)
        def deletedRules = northbound.deleteSwitchRules(srcSwitch.dpId, data.deleteRulesAction)

        then: "The corresponding rules are really deleted"
        deletedRules.size() == data.rulesDeleted
        Wrappers.wait(RULES_DELETION_TIME) {
            compareRules(northbound.getSwitchRules(srcSwitch.dpId).flowEntries, expectedRules)
        }

        and: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        and: "Install default rules if necessary"
        if (data.deleteRulesAction in [DeleteRulesAction.DROP_ALL, DeleteRulesAction.REMOVE_DEFAULTS]) {
            northbound.installSwitchRules(srcSwitch.dpId, InstallRulesAction.INSTALL_DEFAULTS)
            Wrappers.wait(RULES_INSTALLATION_TIME) {
                assert northbound.getSwitchRules(srcSwitch.dpId).flowEntries.size() == srcSwDefaultRules.size()
            }
        }

        where:
        data << [[// Drop all rules
                  deleteRulesAction: DeleteRulesAction.DROP_ALL,
                  rulesDeleted     : srcSwDefaultRules.size() + flowRulesCount,
                  getExpectedRules : { switchId, defaultRules -> [] }
                 ],
                 [// Drop all rules, add back in the base default rules
                  deleteRulesAction: DeleteRulesAction.DROP_ALL_ADD_DEFAULTS,
                  rulesDeleted     : srcSwDefaultRules.size() + flowRulesCount,
                  getExpectedRules : { switchId, defaultRules -> defaultRules }
                 ],
                 [// Don't drop the default rules, but do drop everything else
                  deleteRulesAction: DeleteRulesAction.IGNORE_DEFAULTS,
                  rulesDeleted     : flowRulesCount,
                  getExpectedRules : { switchId, defaultRules -> defaultRules }
                 ],
                 [// Drop all non-base rules (ie IGNORE), and add base rules back (eg overwrite)
                  deleteRulesAction: DeleteRulesAction.OVERWRITE_DEFAULTS,
                  rulesDeleted     : flowRulesCount,
                  getExpectedRules : { switchId, defaultRules -> defaultRules }
                 ],
                 [// Drop all default rules
                  deleteRulesAction: DeleteRulesAction.REMOVE_DEFAULTS,
                  rulesDeleted     : srcSwDefaultRules.size(),
                  getExpectedRules : { switchId, defaultRules -> getFlowRules(switchId) }
                 ],
                 [// Drop the default, add them back
                  deleteRulesAction: DeleteRulesAction.REMOVE_ADD_DEFAULTS,
                  rulesDeleted     : srcSwDefaultRules.size(),
                  getExpectedRules : { switchId, defaultRules -> defaultRules + getFlowRules(switchId) }
                 ]
        ]
    }

    @Unroll
    def "Able to delete default rule from a switch (delete-action=#data.deleteRulesAction)"() {
        assumeFalse("Unable to run the test because an OF_ 12 switch has one broadcast rule as the default",
                dstSwitch.ofVersion == "OF_12" && data.cookie != DefaultRule.VERIFICATION_BROADCAST_RULE.cookie)

        given: "A switch with some flow rules installed"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow)

        when: "Delete rules from the switch"
        def flowRules = getFlowRules(dstSwitch.dpId)
        def deletedRules = northbound.deleteSwitchRules(dstSwitch.dpId, data.deleteRulesAction)

        then: "The corresponding rules are really deleted"
        deletedRules.size() == 1
        Wrappers.wait(RULES_DELETION_TIME) {
            def actualRules = northbound.getSwitchRules(dstSwitch.dpId).flowEntries
            assert actualRules.findAll { it.cookie in deletedRules }.empty
            compareRules(actualRules, dstSwDefaultRules.findAll { it.cookie != data.cookie } + flowRules)
        }

        and: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        and: "Install default rules back"
        northbound.installSwitchRules(dstSwitch.dpId, InstallRulesAction.INSTALL_DEFAULTS)
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            assert northbound.getSwitchRules(dstSwitch.dpId).flowEntries.size() == dstSwDefaultRules.size()
        }

        where:
        data << [[// Drop just the default / base drop rule
                  deleteRulesAction: DeleteRulesAction.REMOVE_DROP,
                  cookie           : DefaultRule.DROP_RULE.cookie
                 ],
                 [// Drop just the verification (broadcast) rule only
                  deleteRulesAction: DeleteRulesAction.REMOVE_BROADCAST,
                  cookie           : DefaultRule.VERIFICATION_BROADCAST_RULE.cookie
                 ],
                 [// Drop just the verification (unicast) rule only
                  deleteRulesAction: DeleteRulesAction.REMOVE_UNICAST,
                  cookie           : DefaultRule.VERIFICATION_UNICAST_RULE.cookie
                 ],
                 [// Remove the verification loop drop rule only
                  deleteRulesAction: DeleteRulesAction.REMOVE_VERIFICATION_LOOP,
                  cookie           : DefaultRule.DROP_LOOP_RULE.cookie
                 ]
        ]
    }

    @Unroll("Able to delete switch rules by #data.description")
    def "Able to delete switch rules by cookie/priority"() {
        given: "A switch with some flow rules installed"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow)

        when: "Delete switch rules by #data.description"
        def deletedRules = northbound.deleteSwitchRules(data.switch.dpId,
                getFlowRules(data.switch.dpId).first()."$data.description")

        then: "The requested rules are really deleted"
        deletedRules.size() == data.rulesDeleted
        Wrappers.wait(RULES_DELETION_TIME) {
            def actualRules = northbound.getSwitchRules(data.switch.dpId).flowEntries
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

    @Unroll("Attempt to delete switch rules by supplying non-existing #data.description leaves all rules intact")
    def "Attempt to delete switch rules by supplying non-existing cookie/priority leaves all rules intact"() {
        given: "A switch with some flow rules installed"
        //TODO(ylobankov): Remove this code once the issue #1701 is resolved.
        assumeTrue("Test is skipped because of the issue #1701", data.description != "priority")

        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow)

        when: "Delete switch rules by non-existing #data.description"
        def deletedRules = northbound.deleteSwitchRules(data.switch.dpId, data.value)

        then: "All rules are kept intact"
        deletedRules.size() == 0
        northbound.getSwitchRules(data.switch.dpId).flowEntries.size() == data.defaultRules.size() + flowRulesCount

        and: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        where:
        data << [[description : "cookie",
                  switch      : srcSwitch,
                  defaultRules: srcSwDefaultRules,
                  value       : 0x8000000000000000L
                 ],
                 [description : "priority",
                  switch      : dstSwitch,
                  defaultRules: dstSwDefaultRules,
                  value       : 0
                 ]
        ]
    }

    @Unroll("Able to delete switch rules by #data.description")
    def "Able to delete switch rules by inPort/inVlan/outPort"() {
        given: "A switch with some flow rules installed"
        flowHelper.addFlow(flow)

        when: "Delete switch rules by #data.description"
        def deletedRules = northbound.deleteSwitchRules(data.switch.dpId, data.inPort, data.inVlan, data.outPort)

        then: "The requested rules are really deleted"
        deletedRules.size() == 1
        Wrappers.wait(RULES_DELETION_TIME) {
            def actualRules = northbound.getSwitchRules(data.switch.dpId).flowEntries
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

    @Unroll("Attempt to delete switch rules by supplying non-existing #data.description leaves all rules intact")
    def "Attempt to delete switch rules by supplying non-existing inPort/inVlan/outPort leaves all rules intact"() {
        given: "A switch with some flow rules installed"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow)

        when: "Delete switch rules by non-existing #data.description"
        def deletedRules = northbound.deleteSwitchRules(data.switch.dpId, data.inPort, data.inVlan, data.outPort)

        then: "All rules are kept intact"
        deletedRules.size() == 0
        northbound.getSwitchRules(data.switch.dpId).flowEntries.size() == data.defaultRules.size() + flowRulesCount

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

    def "Able to synchronize rules on switches (install missing rules)"() {
        given: "Two active not neighboring switches"
        def switches = topology.getActiveSwitches()
        def allLinks = northbound.getAllLinks()
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            allLinks.every { link -> !(link.source.switchId == src.dpId && link.destination.switchId == dst.dpId) }
        } ?: assumeTrue("No suiting switches found", false)

        and: "Create a transit-switch flow going through these switches"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow)

        and: "Reproduce situation when switches have missing rules by deleting flow rules from them"
        def involvedSwitches = pathHelper.getInvolvedSwitches(flow.id)*.dpId
        def defaultPlusFlowRulesMap = involvedSwitches.collectEntries { switchId ->
            [switchId, northbound.getSwitchRules(switchId).flowEntries]
        }

        involvedSwitches.each { switchId ->
            northbound.deleteSwitchRules(switchId, DeleteRulesAction.IGNORE_DEFAULTS)
            Wrappers.wait(RULES_DELETION_TIME) {
                assert northbound.validateSwitchRules(switchId).missingRules.size() == flowRulesCount
            }
        }

        when: "Synchronize rules on switches"
        def synchronizedRulesMap = involvedSwitches.collectEntries { switchId ->
            [switchId, northbound.synchronizeSwitchRules(switchId)]
        }

        then: "The corresponding rules are installed on switches"
        involvedSwitches.each { switchId ->
            assert synchronizedRulesMap[switchId].installedRules.size() == flowRulesCount
            Wrappers.wait(RULES_INSTALLATION_TIME) {
                compareRules(northbound.getSwitchRules(switchId).flowEntries, defaultPlusFlowRulesMap[switchId])
            }
        }

        and: "No missing rules were found after rules validation"
        involvedSwitches.each { switchId ->
            with(northbound.validateSwitchRules(switchId)) {
                verifyAll {
                    properRules.size() == flowRulesCount
                    missingRules.empty
                    excessRules.empty
                }
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

    List<FlowEntry> getFlowRules(SwitchId switchId) {
        def defaultCookies = DefaultRule.values()*.cookie
        northbound.getSwitchRules(switchId).flowEntries.findAll { !(it.cookie in defaultCookies) }.sort()
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
