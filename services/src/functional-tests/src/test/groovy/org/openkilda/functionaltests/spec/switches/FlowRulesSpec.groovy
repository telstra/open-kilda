package org.openkilda.functionaltests.spec.switches

import static com.shazam.shazamcrest.matcher.Matchers.sameBeanAs
import static org.junit.Assume.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.extension.tags.Tag.VIRTUAL
import static org.openkilda.model.MeterId.MIN_FLOW_METER_ID
import static org.openkilda.testing.Constants.NON_EXISTENT_SWITCH_ID
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static spock.util.matcher.HamcrestSupport.expect

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.IterationTag
import org.openkilda.functionaltests.extension.tags.Tags
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
import org.openkilda.model.FlowEncapsulationType
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v1.flows.PingInput
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared
import spock.lang.Unroll

@Narrative("""Verify how Kilda behaves with switch rules (either flow rules or default rules) under different 
circumstances: e.g. persisting rules on newly connected switch, installing default rules on new switch etc.""")
class FlowRulesSpec extends HealthCheckSpecification {
    @Shared
    Switch srcSwitch, dstSwitch
    @Shared
    List srcSwDefaultRules
    @Shared
    List dstSwDefaultRules
    @Shared
    int flowRulesCount = 2

    def setupOnce() {
        (srcSwitch, dstSwitch) = topology.getActiveSwitches()[0..1]
        srcSwDefaultRules = northbound.getSwitchRules(srcSwitch.dpId).flowEntries
        dstSwDefaultRules = northbound.getSwitchRules(dstSwitch.dpId).flowEntries
    }

    @Tags([VIRTUAL, SMOKE])
    def "Pre-installed rules are not deleted from a new switch connected to the controller"() {
        given: "A switch with some rules installed (including default) and not connected to the controller"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow)

        def defaultPlusFlowRules = []
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            defaultPlusFlowRules = northbound.getSwitchRules(srcSwitch.dpId).flowEntries
            assert defaultPlusFlowRules.size() == srcSwDefaultRules.size() + flowRulesCount
        }

        lockKeeper.knockoutSwitch(srcSwitch)
        Wrappers.wait(WAIT_OFFSET) { assert !(srcSwitch.dpId in northbound.getActiveSwitches()*.switchId) }
        flowHelper.deleteFlow(flow.id)

        when: "Connect the switch to the controller"
        lockKeeper.reviveSwitch(srcSwitch)
        Wrappers.wait(WAIT_OFFSET) { assert srcSwitch.dpId in northbound.getActiveSwitches()*.switchId }

        then: "Previously installed rules are not deleted from the switch"
        compareRules(northbound.getSwitchRules(srcSwitch.dpId).flowEntries, defaultPlusFlowRules)

        and: "Delete previously installed rules and meters on the srcSwitch"
        northbound.deleteSwitchRules(srcSwitch.dpId, DeleteRulesAction.IGNORE_DEFAULTS)
        northbound.getAllMeters(srcSwitch.dpId).meterEntries*.meterId.findAll { it >= MIN_FLOW_METER_ID }.each {
            northbound.deleteMeter(srcSwitch.dpId, it)
        }
        Wrappers.wait(RULES_DELETION_TIME) {
            assert northbound.getSwitchRules(srcSwitch.dpId).flowEntries.size() == srcSwitch.defaultCookies.size()
        }
    }

    @Unroll
    @Tags([SMOKE])
    @IterationTag(tags = [SMOKE_SWITCHES], iterationNameRegex = /delete-action=DROP_ALL\)/)
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

    @Tags(SMOKE)
    def "Able to validate switch rules in case flow is created with protected path"() {
        given: "A switch and a flow with protected path"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.allocateProtectedPath = true
        flowHelper.addFlow(flow)
        // protected path creates the 'egress' rule only on src and dst switches
        // and creates 2 rules(input/output) on transit switches
        // so, if (switchId == src/dst): 2 rules for main flow path + 1 egress for protected path = 3
        // in case (switchId != src/dst): 2 rules for main flow path + 2 rules for protected path = 4
        def amountOfRules = { SwitchId switchId ->
            (switchId == srcSwitch.dpId || switchId == dstSwitch.dpId) ? 3 : 4
        }
        def flowInfo = northbound.getFlowPath(flow.id)
        assert flowInfo.protectedPath

        when: "Validate rules on the switches"
        def mainFlowPath = flowInfo.forwardPath
        def protectedFlowPath = flowInfo.protectedPath.forwardPath
        def commonNodeIds = mainFlowPath*.switchId.intersect(protectedFlowPath*.switchId)

        then: "Rules are stored in the 'proper' section"
        commonNodeIds.each { switchId ->
            def rules = northbound.validateSwitchRules(switchId)
            assert rules.properRules.findAll { !Cookie.isDefaultRule(it) }.size() == amountOfRules(switchId)
            assert rules.missingRules.empty
            assert rules.excessRules.empty
        }

        def uniqueNodes = protectedFlowPath.findAll { !commonNodeIds.contains(it.switchId) } + mainFlowPath.findAll {
            !commonNodeIds.contains(it.switchId)
        }
        uniqueNodes.each { sw ->
            def rules = northbound.validateSwitchRules(sw.switchId)
            assert rules.properRules.findAll { !Cookie.isDefaultRule(it) }.size() == 2
            assert rules.missingRules.empty
            assert rules.excessRules.empty
        } || true

        when: "Delete rule of protected path on the srcSwitch"
        def protectedPath = northbound.getFlowPath(flow.id).protectedPath.forwardPath
        def srcSwitchRules = northbound.getSwitchRules(commonNodeIds[0]).flowEntries.findAll {
            !Cookie.isDefaultRule(it.cookie)
        }

        def ruleToDelete = srcSwitchRules.find {
            it.instructions?.applyActions?.flowOutput == protectedPath[0].inputPort.toString() &&
                    it.match.inPort == protectedPath[0].outputPort.toString()
        }.cookie

        northbound.deleteSwitchRules(commonNodeIds[0], ruleToDelete)

        then: "Deleted rule is moved to the 'missing' section on the srcSwitch"
        def srcSwitchValidateRules = northbound.validateSwitchRules(commonNodeIds[0])
        srcSwitchValidateRules.properRules.findAll { !Cookie.isDefaultRule(it) }.size() == 2
        srcSwitchValidateRules.missingRules.size() == 1
        srcSwitchValidateRules.missingRules == [ruleToDelete]
        srcSwitchValidateRules.excessRules.empty

        and: "Rest switches are not affected by deleting the rule on the srcSwitch"
        commonNodeIds[1..-1].each { switchId ->
            def rules = northbound.validateSwitchRules(switchId)
            assert rules.properRules.findAll { !Cookie.isDefaultRule(it) }.size() == amountOfRules(switchId)
            assert rules.missingRules.empty
            assert rules.excessRules.empty
        }
        uniqueNodes.each { sw ->
            def rules = northbound.validateSwitchRules(sw.switchId)
            assert rules.properRules.findAll { !Cookie.isDefaultRule(it) }.size() == 2
            assert rules.missingRules.empty
            assert rules.excessRules.empty
        } || true

        and: "Cleanup: delete the flow"
        flowHelper.deleteFlow(flow.id)
    }

    @Unroll("Able to delete switch rules by #data.description")
    @Tags([SMOKE, SMOKE_SWITCHES])
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
    @Tags(SMOKE_SWITCHES)
    @IterationTag(tags = [SMOKE], iterationNameRegex = /inPort/)
    def "Able to delete switch rules by inPort/inVlan/outPort"() {
        given: "A switch with some flow rules installed"
        flowHelper.addFlow(flow)

        when: "Delete switch rules by #data.description"
        def deletedRules = northbound.deleteSwitchRules(data.switch.dpId, data.inPort, data.inVlan,
                data.encapsulationType, data.outPort)

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
        data << [[description      : "inPort",
                  switch           : srcSwitch,
                  defaultRules     : srcSwDefaultRules,
                  inPort           : flow.source.portNumber,
                  inVlan           : null,
                  encapsulationType: null,
                  outPort          : null
                 ],
                 [description      : "inVlan",
                  switch           : srcSwitch,
                  defaultRules     : srcSwDefaultRules,
                  inPort           : null,
                  inVlan           : flow.source.vlanId,
                  encapsulationType: "TRANSIT_VLAN",
                  outPort          : null
                 ],
                 [description      : "inPort and inVlan",
                  switch           : srcSwitch,
                  defaultRules     : srcSwDefaultRules,
                  inPort           : flow.source.portNumber,
                  inVlan           : flow.source.vlanId,
                  encapsulationType: "TRANSIT_VLAN",
                  outPort          : null
                 ],
                 [description      : "outPort",
                  switch           : dstSwitch,
                  defaultRules     : dstSwDefaultRules,
                  inPort           : null,
                  inVlan           : null,
                  encapsulationType: null,
                  outPort          : flow.destination.portNumber
                 ]
        ]
    }

    @Unroll("Attempt to delete switch rules by supplying non-existing #data.description leaves all rules intact")
    @IterationTag(tags = [SMOKE], iterationNameRegex = /inVlan/)
    def "Attempt to delete switch rules by supplying non-existing inPort/inVlan/outPort leaves all rules intact"() {
        given: "A switch with some flow rules installed"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow)

        when: "Delete switch rules by non-existing #data.description"
        def deletedRules = northbound.deleteSwitchRules(data.switch.dpId, data.inPort, data.inVlan,
                data.encapsulationType, data.outPort)

        then: "All rules are kept intact"
        deletedRules.size() == 0
        northbound.getSwitchRules(data.switch.dpId).flowEntries.size() == data.defaultRules.size() + flowRulesCount

        and: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        where:
        data << [[description      : "inPort",
                  switch           : srcSwitch,
                  defaultRules     : srcSwDefaultRules,
                  inPort           : Integer.MAX_VALUE - 1,
                  inVlan           : null,
                  encapsulationType: null,
                  outPort          : null
                 ],
                 [description      : "inVlan",
                  switch           : srcSwitch,
                  defaultRules     : srcSwDefaultRules,
                  inPort           : null,
                  inVlan           : 4095,
                  encapsulationType: "TRANSIT_VLAN",
                  outPort          : null
                 ],
                 [description      : "inPort and inVlan",
                  switch           : srcSwitch,
                  defaultRules     : srcSwDefaultRules,
                  inPort           : Integer.MAX_VALUE - 1,
                  inVlan           : 4095,
                  encapsulationType: "TRANSIT_VLAN",
                  outPort          : null
                 ],
                 [description      : "outPort",
                  switch           : dstSwitch,
                  defaultRules     : dstSwDefaultRules,
                  inPort           : null,
                  inVlan           : null,
                  encapsulationType: null,
                  outPort          : Integer.MAX_VALUE - 1
                 ]
        ]
    }

    @Unroll
    @Tags([TOPOLOGY_DEPENDENT, SMOKE])
    def "Able to synchronize rules for #description on different switches (install missing rules)"() {
        given: "Two active not neighboring switches with the longest available path"
        def switchPair = topologyHelper.getAllNotNeighboringSwitchPairs().max { pair ->
            pair.paths.max { it.size() }.size()
        }
        assumeTrue("Unable to find required switches in topology", switchPair as boolean)
        def longPath = switchPair.paths.max { it.size() }
        switchPair.paths.findAll { it != longPath }.each { pathHelper.makePathMorePreferable(longPath, it) }

        and: "Create a transit-switch flow going through these switches"
        def flow = flowHelper.randomFlow(switchPair)
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
            verifyAll(northbound.validateSwitchRules(switchId)) {
                properRules.findAll { !Cookie.isDefaultRule(it) }.size() == flowRulesCount
                missingRules.empty
                excessRules.empty
            }
        }

        and: "Cleanup: delete the flow and reset costs"
        flowHelper.deleteFlow(flow.id)
        northbound.deleteLinkProps(northbound.getAllLinkProps())

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

    def "Able to synchronize rules for a flow with protected path"() {
        given: "Two active not neighboring switches"
        def switches = topology.getActiveSwitches()
        def allLinks = northbound.getAllLinks()
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            allLinks.every { link -> !(link.source.switchId == src.dpId && link.destination.switchId == dst.dpId) }
        } ?: assumeTrue("No suiting switches found", false)

        and: "Create a flow with protected path"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flow.allocateProtectedPath = true
        flowHelper.addFlow(flow)

        flowHelper.verifyRulesOnProtectedFlow(flow.id)
        def flowPathInfo = northbound.getFlowPath(flow.id)
        def mainFlowPath = flowPathInfo.forwardPath
        def protectedFlowPath = flowPathInfo.protectedPath.forwardPath
        def commonNodeIds = mainFlowPath*.switchId.intersect(protectedFlowPath*.switchId)
        def uniqueNodes = protectedFlowPath.findAll { !commonNodeIds.contains(it.switchId) } + mainFlowPath.findAll {
            !commonNodeIds.contains(it.switchId)
        }

        and: "Delete flow rules(for main and protected paths) on involved switches for creating missing rules"
        def amountOfRules = { SwitchId switchId ->
            (switchId == srcSwitch.dpId || switchId == dstSwitch.dpId) ? 3 : 4
        }
        commonNodeIds.each { northbound.deleteSwitchRules(it, DeleteRulesAction.IGNORE_DEFAULTS) }
        uniqueNodes.each { northbound.deleteSwitchRules(it.switchId, DeleteRulesAction.IGNORE_DEFAULTS) }

        commonNodeIds.each { switchId ->
            assert northbound.validateSwitchRules(switchId).missingRules.size() == amountOfRules(switchId)
        }

        uniqueNodes.each { assert northbound.validateSwitchRules(it.switchId).missingRules.size() == 2 }

        when: "Synchronize rules on switches"
        commonNodeIds.each {
            def response = northbound.synchronizeSwitchRules(it)
            assert response.missingRules.size() == amountOfRules(it)
            assert response.installedRules.sort() == response.missingRules.sort()
            assert response.properRules.findAll { !Cookie.isDefaultRule(it) }.empty
            assert response.excessRules.empty
        }
        uniqueNodes.each {
            def response = northbound.synchronizeSwitchRules(it.switchId)
            assert response.missingRules.size() == 2
            assert response.installedRules.sort() == response.missingRules.sort()
            assert response.properRules.findAll { !Cookie.isDefaultRule(it) }.empty, it
            assert response.excessRules.empty
        }

        then: "No missing rules were found after rules synchronization"
        commonNodeIds.each { switchId ->
            verifyAll(northbound.validateSwitchRules(switchId)) {
                properRules.findAll { !Cookie.isDefaultRule(it) }.size() == amountOfRules(switchId)
                missingRules.empty
                excessRules.empty
            }
        }

        uniqueNodes.each {
            verifyAll(northbound.validateSwitchRules(it.switchId)) {
                properRules.findAll { !Cookie.isDefaultRule(it) }.size() == 2
                missingRules.empty
                excessRules.empty
            }
        }

        and: "Delete the flow"
        flowHelper.deleteFlow(flow.id)
    }

    @Tags([SMOKE, SMOKE_SWITCHES])
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
        antiflap.portDown(islToFail.srcSwitch.dpId, islToFail.srcPort)

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
        antiflap.portUp(islToFail.srcSwitch.dpId, islToFail.srcPort)
        flowHelper.deleteFlow(flow.id)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            northbound.getAllLinks().each { assert it.state != IslChangeType.FAILED }
        }
        database.resetCosts()
    }

    @Tags(HARDWARE)
    def "Able to synchronize rules for a flow with VXLAN encapsulation"() {
        given: "Two active not neighboring Noviflow switches"
        def switchPair = topologyHelper.getAllNotNeighboringSwitchPairs().find { swP ->
            swP.src.noviflow && !swP.src.wb5164 && swP.dst.noviflow && !swP.dst.wb5164 && swP.paths.find { path ->
                pathHelper.getInvolvedSwitches(path).every { it.noviflow && !it.wb5164 }
            }
        } ?: assumeTrue("Unable to find required switches in topology", false)

        and: "Create a flow with vxlan encapsulation"
        def flow = flowHelper.randomFlow(switchPair)
        flow.encapsulationType = FlowEncapsulationType.VXLAN
        flowHelper.addFlow(flow)

        and: "Reproduce situation when switches have missing rules by deleting flow rules from them"
        def flowInfoFromDb = database.getFlow(flow.id)
        def involvedSwitches = pathHelper.getInvolvedSwitches(flow.id)*.dpId
        def transitSwitchIds = involvedSwitches[-1..-2]
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

        and: "Rules are synced correctly"
        // ingressRule should contain "pushVxlan"
        // egressRule should contain "tunnel-id"
        with(northbound.getSwitchRules(switchPair.src.dpId).flowEntries) { rules ->
            assert rules.find {
                it.cookie == flowInfoFromDb.forwardPath.cookie.value
            }.instructions.applyActions.pushVxlan
            assert rules.find {
                it.cookie == flowInfoFromDb.reversePath.cookie.value
            }.match.tunnelId
        }

        with(northbound.getSwitchRules(switchPair.dst.dpId).flowEntries) { rules ->
            assert rules.find {
                it.cookie == flowInfoFromDb.forwardPath.cookie.value
            }.match.tunnelId
            assert rules.find {
                it.cookie == flowInfoFromDb.reversePath.cookie.value
            }.instructions.applyActions.pushVxlan
        }

        transitSwitchIds.each { swId ->
            with(northbound.getSwitchRules(swId).flowEntries) { rules ->
                assert rules.find {
                    it.cookie == flowInfoFromDb.forwardPath.cookie.value
                }.match.tunnelId
                assert rules.find {
                    it.cookie == flowInfoFromDb.reversePath.cookie.value
                }.match.tunnelId
            }
        }

        and: "No missing rules were found after rules validation"
        involvedSwitches.each { switchId ->
            verifyAll(northbound.validateSwitchRules(switchId)) {
                properRules.size().findAll { !Cookie.isDefaultRule(it) }.size() == flowRulesCount
                missingRules.empty
                excessRules.empty
            }
        }

        and: "Cleanup: delete the flow and reset costs"
        flowHelper.deleteFlow(flow.id)
    }

    void compareRules(actualRules, expectedRules) {
        assert expect(actualRules.sort { it.cookie }, sameBeanAs(expectedRules.sort { it.cookie })
                .ignoring("byteCount")
                .ignoring("packetCount")
                .ignoring("durationNanoSeconds")
                .ignoring("durationSeconds"))
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
