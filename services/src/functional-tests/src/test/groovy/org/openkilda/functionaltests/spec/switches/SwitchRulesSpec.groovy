package org.openkilda.functionaltests.spec.switches

import static com.shazam.shazamcrest.matcher.Matchers.sameBeanAs
import static org.junit.Assume.assumeFalse
import static org.junit.Assume.assumeTrue
import static org.openkilda.testing.Constants.NON_EXISTENT_SWITCH_ID
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static spock.util.matcher.HamcrestSupport.expect

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.messaging.command.switches.InstallRulesAction
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.info.rule.FlowEntry
import org.openkilda.messaging.payload.flow.FlowEndpointPayload
import org.openkilda.messaging.payload.flow.FlowPayload
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.Cookie
import org.openkilda.northbound.dto.flows.PingInput
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import org.springframework.web.client.HttpClientErrorException
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
        expect: "Default rules are installed on the switch"
        def cookies = northbound.getSwitchRules(sw.dpId).flowEntries*.cookie
        cookies.sort() == sw.defaultCookies.sort()

        where:
        sw << uniqueSwitches
    }

    def "Default rules are installed when a new switch is connected"() {
        requireProfiles("virtual")

        given: "A switch with no rules installed and not connected to the controller"
        northbound.deleteSwitchRules(srcSwitch.dpId, DeleteRulesAction.DROP_ALL)
        Wrappers.wait(RULES_DELETION_TIME) { assert northbound.getSwitchRules(srcSwitch.dpId).flowEntries.isEmpty() }

        lockKeeper.knockoutSwitch(srcSwitch.dpId)
        Wrappers.wait(WAIT_OFFSET) { assert !(srcSwitch.dpId in northbound.getActiveSwitches()*.switchId) }

        when: "Connect the switch to the controller"
        lockKeeper.reviveSwitch(srcSwitch.dpId)
        Wrappers.wait(WAIT_OFFSET) { assert srcSwitch.dpId in northbound.getActiveSwitches()*.switchId }

        then: "Default rules are installed on the switch"
        def cookies = northbound.getSwitchRules(srcSwitch.dpId).flowEntries*.cookie
        cookies.sort() == srcSwitch.defaultCookies.sort()
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
            assert northbound.getSwitchRules(srcSwitch.dpId).flowEntries.size() == srcSwitch.defaultCookies.size()
        }
    }

    @Unroll
    def "Able to install default rule on an #sw.ofVersion switch(#sw.dpId, install-action=#data.installRulesAction)"() {
        assumeFalse("Unable to run the test because an OF_12 switch has one broadcast rule as the default",
                sw.ofVersion == "OF_12" && data.installRulesAction != InstallRulesAction.INSTALL_BROADCAST)
        assumeFalse("Unable to run the test because only NoviFlow switches support installation of BFD catch rule",
                !sw.noviflow && data.installRulesAction == InstallRulesAction.INSTALL_BFD_CATCH)

        given: "A switch without any rules"
        def defaultRules = northbound.getSwitchRules(sw.dpId).flowEntries

        northbound.deleteSwitchRules(sw.dpId, DeleteRulesAction.DROP_ALL)
        Wrappers.wait(RULES_DELETION_TIME) { assert northbound.getSwitchRules(sw.dpId).flowEntries.empty }

        when: "Install rules on the switch"
        def installedRules = northbound.installSwitchRules(sw.dpId, data.installRulesAction)

        then: "The corresponding rules are really installed"
        installedRules.size() == 1

        def expectedRules = defaultRules.findAll { it.cookie == data.cookie }
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            compareRules(northbound.getSwitchRules(sw.dpId).flowEntries, expectedRules)
        }

        and: "Install missing default rules"
        northbound.installSwitchRules(sw.dpId, InstallRulesAction.INSTALL_DEFAULTS)
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            assert northbound.getSwitchRules(sw.dpId).flowEntries.size() == defaultRules.size()
        }

        where:
        [data, sw] << [
                [
                        [
                                installRulesAction: InstallRulesAction.INSTALL_DROP,
                                cookie            : Cookie.DROP_RULE_COOKIE
                        ],
                        [
                                installRulesAction: InstallRulesAction.INSTALL_BROADCAST,
                                cookie            : Cookie.VERIFICATION_BROADCAST_RULE_COOKIE
                        ],
                        [
                                installRulesAction: InstallRulesAction.INSTALL_UNICAST,
                                cookie            : Cookie.VERIFICATION_UNICAST_RULE_COOKIE
                        ],
                        [
                                installRulesAction: InstallRulesAction.INSTALL_BFD_CATCH,
                                cookie            : Cookie.CATCH_BFD_RULE_COOKIE
                        ]
                ],
                uniqueSwitches
        ].combinations()
    }

    @Unroll
    def "Able to install default rules on an #sw.ofVersion switch(#sw.dpId, install-action=INSTALL_DEFAULTS)"() {
        given: "A switch without any rules"
        def defaultRules = northbound.getSwitchRules(sw.dpId).flowEntries

        northbound.deleteSwitchRules(sw.dpId, DeleteRulesAction.DROP_ALL)
        Wrappers.wait(RULES_DELETION_TIME) { assert northbound.getSwitchRules(sw.dpId).flowEntries.empty }

        when: "Install rules on the switch"
        def installedRules = northbound.installSwitchRules(sw.dpId, InstallRulesAction.INSTALL_DEFAULTS)

        then: "The corresponding rules are really installed"
        // TODO(ylobankov): For now BFD catch rule is returned in the list of installed rules even though the switch
        // doesn't have BFD support. Uncomment the check when the issue is resolved.
        //installedRules.size() == defaultRules.size()
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            compareRules(northbound.getSwitchRules(sw.dpId).flowEntries, defaultRules)
        }

        where:
        sw << uniqueSwitches
    }

    @Unroll
    def "Able to delete rules from a switch (delete-action=#data.deleteRulesAction)"() {
        given: "A switch with some flow rules installed"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow)

        when: "Delete rules from the switch"
        def expectedRules = data.getExpectedRules(srcSwitch, srcSwDefaultRules)
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
        data << [
                [// Drop all rules
                 deleteRulesAction: DeleteRulesAction.DROP_ALL,
                 rulesDeleted     : srcSwDefaultRules.size() + flowRulesCount,
                 getExpectedRules : { sw, defaultRules -> [] }
                ],
                [// Drop all rules, add back in the base default rules
                 deleteRulesAction: DeleteRulesAction.DROP_ALL_ADD_DEFAULTS,
                 rulesDeleted     : srcSwDefaultRules.size() + flowRulesCount,
                 getExpectedRules : { sw, defaultRules -> defaultRules }
                ],
                [// Don't drop the default rules, but do drop everything else
                 deleteRulesAction: DeleteRulesAction.IGNORE_DEFAULTS,
                 rulesDeleted     : flowRulesCount,
                 getExpectedRules : { sw, defaultRules -> defaultRules }
                ],
                [// Drop all non-base rules (ie IGNORE), and add base rules back (eg overwrite)
                 deleteRulesAction: DeleteRulesAction.OVERWRITE_DEFAULTS,
                 rulesDeleted     : flowRulesCount,
                 getExpectedRules : { sw, defaultRules -> defaultRules }
                ],
                [// Drop all default rules
                 deleteRulesAction: DeleteRulesAction.REMOVE_DEFAULTS,
                 rulesDeleted     : srcSwDefaultRules.size(),
                 getExpectedRules : { sw, defaultRules -> getFlowRules(sw) }
                ],
                [// Drop the default, add them back
                 deleteRulesAction: DeleteRulesAction.REMOVE_ADD_DEFAULTS,
                 rulesDeleted     : srcSwDefaultRules.size(),
                 getExpectedRules : { sw, defaultRules -> defaultRules + getFlowRules(sw) }
                ]
        ]
    }

    @Unroll
    def "Able to delete default rule from an #sw.ofVersion switch (#sw.dpId, delete-action=#data.deleteRulesAction)"() {
        assumeFalse("Unable to run the test because an OF_12 switch has one broadcast rule as the default",
                sw.ofVersion == "OF_12" && data.cookie != Cookie.VERIFICATION_BROADCAST_RULE_COOKIE)

        when: "Delete rules from the switch"
        def defaultRules = northbound.getSwitchRules(sw.dpId).flowEntries
        def deletedRules = northbound.deleteSwitchRules(sw.dpId, data.deleteRulesAction)

        then: "The corresponding rules are really deleted"
        deletedRules.size() == 1
        Wrappers.wait(RULES_DELETION_TIME) {
            def actualRules = northbound.getSwitchRules(sw.dpId).flowEntries
            assert actualRules.findAll { it.cookie in deletedRules }.empty
            compareRules(actualRules, defaultRules.findAll { it.cookie != data.cookie })
        }

        and: "Install default rules back"
        northbound.installSwitchRules(sw.dpId, InstallRulesAction.INSTALL_DEFAULTS)
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            assert northbound.getSwitchRules(sw.dpId).flowEntries.size() == defaultRules.size()
        }

        where:
        [data, sw] << [
                [
                        [// Drop just the default / base drop rule
                         deleteRulesAction: DeleteRulesAction.REMOVE_DROP,
                         cookie           : Cookie.DROP_RULE_COOKIE
                        ],
                        [// Drop just the verification (broadcast) rule only
                         deleteRulesAction: DeleteRulesAction.REMOVE_BROADCAST,
                         cookie           : Cookie.VERIFICATION_BROADCAST_RULE_COOKIE
                        ],
                        [// Drop just the verification (unicast) rule only
                         deleteRulesAction: DeleteRulesAction.REMOVE_UNICAST,
                         cookie           : Cookie.VERIFICATION_UNICAST_RULE_COOKIE
                        ],
                        [// Remove the verification loop drop rule only
                         deleteRulesAction: DeleteRulesAction.REMOVE_VERIFICATION_LOOP,
                         cookie           : Cookie.DROP_VERIFICATION_LOOP_RULE_COOKIE
                        ]
                ],
                uniqueSwitches
        ].combinations()
    }

    @Unroll("Able to delete switch rules by #data.description")
    def "Able to delete switch rules by cookie/priority"() {
        given: "A switch with some flow rules installed"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow)

        when: "Delete switch rules by #data.description"
        def deletedRules = northbound.deleteSwitchRules(
                data.switch.dpId, getFlowRules(data.switch).first()."$data.description")

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

    @Unroll
    def "Able to synchronize rules for #description on a switch (install missing rules)"() {
        given: "Two active not neighboring switches"
        def switches = topology.getActiveSwitches()
        def allLinks = northbound.getAllLinks()
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            allLinks.every { link -> !(link.source.switchId == src.dpId && link.destination.switchId == dst.dpId) }
        } ?: assumeTrue("No suiting switches found", false)

        and: "Create a transit-switch flow going through these switches"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.maximumBandwidth = maximumBandwidth
        flow.ignoreBandwidth = maximumBandwidth ? false : true
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

        where:
        description         | maximumBandwidth
        "a flow"            | 1000
        "an unmetered flow" | 0
    }

    @Unroll
    def "Unable to #action rules on a non-existent switch"() {
        when: "Try to #action rules on a non-existent switch"
        northbound."$method"(NON_EXISTENT_SWITCH_ID)

        then: "An error is received (404 code)"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 404
        exc.responseBodyAsString.to(MessageError).errorMessage == "Switch $NON_EXISTENT_SWITCH_ID was not found"

        where:
        action        | method
        "synchronize" | "synchronizeSwitchRules"
        "validate"    | "validateSwitchRules"
    }

    def "Traffic counters in ingress rule are reset on flow rerouting"() {
        given: "Two active neighboring switches and two possible flow paths at least"
        List<List<PathNode>> possibleFlowPaths = []
        def isl = topology.getIslsForActiveSwitches().find {
            possibleFlowPaths = database.getPaths(it.srcSwitch.dpId, it.dstSwitch.dpId)*.path.sort { it.size() }
            possibleFlowPaths.size() > 1
        } ?: assumeTrue("No suiting switches found", false)
        def (srcSwitch, dstSwitch) = [isl.srcSwitch, isl.dstSwitch]

        and: "Create a flow going through these switches"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow)
        def flowCookiesMap = [srcSwitch, dstSwitch].collectEntries { [it, getFlowRules(it)*.cookie] }

        when: "Ping the flow"
        def response = northbound.pingFlow(flow.id, new PingInput())

        then: "Operation is successful"
        response.forward.pingSuccess
        response.reverse.pingSuccess

        and: "Traffic counters in ingress rule on source and destination switches represent packets movement"
        checkTrafficCountersInRules(flow.source, true)
        checkTrafficCountersInRules(flow.destination, true)

        when: "Break the flow ISL (bring switch port down) to cause flow rerouting"
        def flowPath = PathHelper.convert(northbound.getFlowPath(flow.id))
        // Switches may have parallel links, so we need to get involved ISLs.
        def islToFail = pathHelper.getInvolvedIsls(flowPath).first()
        northbound.portDown(islToFail.srcSwitch.dpId, islToFail.srcPort)

        then: "The flow was rerouted after reroute timeout"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            assert northbound.getFlowStatus(flow.id).status == FlowState.UP
            assert PathHelper.convert(northbound.getFlowPath(flow.id)) != flowPath
            [srcSwitch, dstSwitch].each { sw -> getFlowRules(sw).each { assert !(it.cookie in flowCookiesMap[sw]) } }
        }

        and: "Traffic counters in ingress rule on source and destination switches are reset"
        checkTrafficCountersInRules(flow.source, false)
        checkTrafficCountersInRules(flow.destination, false)

        and: "Revive the ISL back (bring switch port up), delete the flow and reset costs"
        northbound.portUp(islToFail.srcSwitch.dpId, islToFail.srcPort)
        flowHelper.deleteFlow(flow.id)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
        database.resetCosts()
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

    void checkTrafficCountersInRules(FlowEndpointPayload flowEndpoint, isTrafficThroughIngressRuleExpected) {
        def rules = northbound.getSwitchRules(flowEndpoint.datapath).flowEntries
        def ingressRule = filterRules(rules, flowEndpoint.portNumber, flowEndpoint.vlanId, null)[0]
        def egressRule = filterRules(rules, null, null, flowEndpoint.portNumber).find {
            it.instructions.applyActions.fieldAction.fieldValue == flowEndpoint.vlanId.toString()
        }

        assert ingressRule.flags.contains("RESET_COUNTS")
        assert isTrafficThroughIngressRuleExpected == (ingressRule.packetCount > 0)
        assert isTrafficThroughIngressRuleExpected == (ingressRule.byteCount > 0)

        assert !egressRule.flags.contains("RESET_COUNTS")
        assert egressRule.packetCount == 0
        assert egressRule.byteCount == 0
    }

    List<FlowEntry> getFlowRules(Switch sw) {
        northbound.getSwitchRules(sw.dpId).flowEntries.findAll { !(it.cookie in sw.defaultCookies) }.sort()
    }
}
