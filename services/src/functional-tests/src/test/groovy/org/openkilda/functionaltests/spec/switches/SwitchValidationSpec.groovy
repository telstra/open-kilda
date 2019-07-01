package org.openkilda.functionaltests.spec.switches

import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.model.MeterId.MAX_SYSTEM_RULE_METER_ID
import static org.openkilda.model.MeterId.MIN_FLOW_METER_ID
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.SwitchHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.Message
import org.openkilda.messaging.command.CommandData
import org.openkilda.messaging.command.CommandMessage
import org.openkilda.messaging.command.flow.InstallEgressFlow
import org.openkilda.messaging.command.flow.InstallIngressFlow
import org.openkilda.messaging.command.flow.InstallTransitFlow
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.model.Cookie
import org.openkilda.model.FlowEncapsulationType
import org.openkilda.model.MeterId
import org.openkilda.model.OutputVlanType
import org.openkilda.model.SwitchId
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import groovy.transform.Memoized
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import spock.lang.Narrative

@Narrative("""This test suite checks the switch validate feature on a flow that contains 2+ switches.
Description of fields:
- missing - those meters/rules, which are NOT present on a switch, but are present in db
- misconfigured - those meters which have different value (on a switch and in db) for the same parameter
- excess - those meters/rules, which are present on a switch, but are NOT present in db
- proper - meters/rules values are the same on a switch and in db
""")
@Tags(SMOKE)
class SwitchValidationSpec extends BaseSpecification {
    @Value("#{kafkaTopicsConfig.getSpeakerFlowTopic()}")
    String flowTopic
    @Autowired
    @Qualifier("kafkaProducerProperties")
    Properties producerProps
    @Autowired
    SwitchHelper switchHelper

    def "Switch validation is able to store correct information on a switch in the 'proper' section"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches.findAll { it.ofVersion != "OF_12" }
        def flow = flowHelper.addFlow(flowHelper.randomFlow(srcSwitch, dstSwitch))

        then: "One meter is automatically created on the src and dst switches"
        def srcSwitchCreatedMeterIds = getCreatedMeterIds(srcSwitch.dpId)
        def dstSwitchCreatedMeterIds = getCreatedMeterIds(dstSwitch.dpId)
        srcSwitchCreatedMeterIds.size() == 1
        dstSwitchCreatedMeterIds.size() == 1

        and: "Validate switch for src and dst contains expected meters data in 'proper' section"
        def srcSwitchValidateInfo = northbound.validateSwitch(srcSwitch.dpId)
        def dstSwitchValidateInfo = northbound.validateSwitch(dstSwitch.dpId)
        def srcSwitchCreatedCookies = getCookiesWithMeter(srcSwitch.dpId)
        def dstSwitchCreatedCookies = getCookiesWithMeter(dstSwitch.dpId)

        srcSwitchValidateInfo.meters.proper*.meterId.containsAll(srcSwitchCreatedMeterIds)
        dstSwitchValidateInfo.meters.proper*.meterId.containsAll(dstSwitchCreatedMeterIds)

        srcSwitchValidateInfo.meters.proper*.cookie.containsAll(srcSwitchCreatedCookies)
        dstSwitchValidateInfo.meters.proper*.cookie.containsAll(dstSwitchCreatedCookies)

        [srcSwitchValidateInfo, dstSwitchValidateInfo].each {
            it.meters.proper.each {
                assert it.rate == flow.maximumBandwidth
                assert it.flowId == flow.id
                assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            }
        }

        Long srcSwitchBurstSize = switchHelper.getExpectedBurst(srcSwitch.dpId, flow.maximumBandwidth)
        Long dstSwitchBurstSize = switchHelper.getExpectedBurst(dstSwitch.dpId, flow.maximumBandwidth)
        verifyBurstSizeIsCorrect(srcSwitchBurstSize, srcSwitchValidateInfo.meters.proper*.burstSize[0])
        verifyBurstSizeIsCorrect(dstSwitchBurstSize, dstSwitchValidateInfo.meters.proper*.burstSize[0])


        and: "The rest fields in the 'meter' section are empty"
        [srcSwitchValidateInfo, dstSwitchValidateInfo].each {
            switchHelper.verifyMeterSectionsAreEmpty(it, ["missing", "misconfigured", "excess"])
        }

        and: "Created rules are stored in the 'proper' section"
        def createdCookies = srcSwitchCreatedCookies + dstSwitchCreatedCookies
        [srcSwitchValidateInfo, dstSwitchValidateInfo].each {
            it.rules.proper.containsAll(createdCookies)
        }

        and: "The rest fields in the 'rule' section are empty"
        [srcSwitchValidateInfo, dstSwitchValidateInfo].each {
            switchHelper.verifyRuleSectionsAreEmpty(it, ["missing", "excess"])
        }

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "Check that the switch validate request returns empty sections"
        Wrappers.wait(WAIT_OFFSET) {
            def srcSwitchValidateInfoAfterDelete = northbound.validateSwitch(srcSwitch.dpId)
            def dstSwitchValidateInfoAfterDelete = northbound.validateSwitch(dstSwitch.dpId)
            [srcSwitchValidateInfoAfterDelete, dstSwitchValidateInfoAfterDelete].each {
                switchHelper.verifyRuleSectionsAreEmpty(it)
                switchHelper.verifyMeterSectionsAreEmpty(it)
            }
        }
    }

    def "Switch validation is able to detect meter info into the 'misconfigured' section"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches.findAll { it.ofVersion != "OF_12" }
        def flow = flowHelper.addFlow(flowHelper.randomFlow(srcSwitch, dstSwitch))
        def srcSwitchCreatedMeterIds = getCreatedMeterIds(srcSwitch.dpId)
        def dstSwitchCreatedMeterIds = getCreatedMeterIds(dstSwitch.dpId)

        and: "Change bandwidth for the created flow directly in DB"
        def newBandwidth = flow.maximumBandwidth + 100
        /** at this point meter is set for given flow. Now update flow bandwidth directly via DB,
         it is done just for moving meter from the 'proper' section into the 'misconfigured'*/
        database.updateFlowBandwidth(flow.id, newBandwidth)
        //at this point existing meters do not correspond with the flow

        and: "Validate src and dst switches"
        def srcSwitchValidateInfo = northbound.validateSwitch(srcSwitch.dpId)
        def dstSwitchValidateInfo = northbound.validateSwitch(dstSwitch.dpId)

        then: "Meters info is moved into the 'misconfigured' section"
        def srcSwitchCreatedCookies = getCookiesWithMeter(srcSwitch.dpId)
        def dstSwitchCreatedCookies = getCookiesWithMeter(dstSwitch.dpId)

        srcSwitchValidateInfo.meters.misconfigured*.meterId.containsAll(srcSwitchCreatedMeterIds)
        dstSwitchValidateInfo.meters.misconfigured*.meterId.containsAll(dstSwitchCreatedMeterIds)

        srcSwitchValidateInfo.meters.misconfigured*.cookie.containsAll(srcSwitchCreatedCookies)
        dstSwitchValidateInfo.meters.misconfigured*.cookie.containsAll(dstSwitchCreatedCookies)

        [srcSwitchValidateInfo, dstSwitchValidateInfo].each {
            assert it.meters.misconfigured.meterId.size() == 1
            it.meters.misconfigured.each {
                assert it.rate == flow.maximumBandwidth
                assert it.flowId == flow.id
                assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            }
        }

        Long srcSwitchBurstSize = switchHelper.getExpectedBurst(srcSwitch.dpId, flow.maximumBandwidth)
        Long dstSwitchBurstSize = switchHelper.getExpectedBurst(dstSwitch.dpId, flow.maximumBandwidth)
        verifyBurstSizeIsCorrect(srcSwitchBurstSize, srcSwitchValidateInfo.meters.misconfigured*.burstSize[0])
        verifyBurstSizeIsCorrect(dstSwitchBurstSize, dstSwitchValidateInfo.meters.misconfigured*.burstSize[0])

        and: "Reason is specified why meter is misconfigured"
        [srcSwitchValidateInfo, dstSwitchValidateInfo].each {
            it.meters.misconfigured.each {
                assert it.actual.rate == flow.maximumBandwidth
                assert it.expected.rate == newBandwidth
            }
        }

        and: "The rest fields of 'meter' section are empty"
        [srcSwitchValidateInfo, dstSwitchValidateInfo].each {
            switchHelper.verifyMeterSectionsAreEmpty(it, ["missing", "proper", "excess"])
        }

        and: "Created rules are still stored in the 'proper' section"
        def createdCookies = srcSwitchCreatedCookies + dstSwitchCreatedCookies
        [srcSwitchValidateInfo, dstSwitchValidateInfo].each {
            it.rules.proper.containsAll(createdCookies)
            switchHelper.verifyRuleSectionsAreEmpty(it, ["missing", "excess"])
        }

        when: "Restore correct bandwidth via DB"
        database.updateFlowBandwidth(flow.id, flow.maximumBandwidth)

        then: "Misconfigured meters are moved into the 'proper' section"
        def srcSwitchValidateInfoRestored = northbound.validateSwitch(srcSwitch.dpId)
        def dstSwitchValidateInfoRestored = northbound.validateSwitch(dstSwitch.dpId)

        srcSwitchValidateInfoRestored.meters.proper*.meterId.containsAll(srcSwitchCreatedMeterIds)
        dstSwitchValidateInfoRestored.meters.proper*.meterId.containsAll(dstSwitchCreatedMeterIds)

        [srcSwitchValidateInfoRestored, dstSwitchValidateInfoRestored].each {
            switchHelper.verifyMeterSectionsAreEmpty(it, ["missing", "misconfigured", "excess"])
        }

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "Check that the switch validate request returns empty sections"
        Wrappers.wait(WAIT_OFFSET) {
            def srcSwitchValidateInfoAfterDelete = northbound.validateSwitch(srcSwitch.dpId)
            def dstSwitchValidateInfoAfterDelete = northbound.validateSwitch(dstSwitch.dpId)
            [srcSwitchValidateInfoAfterDelete, dstSwitchValidateInfoAfterDelete].each {
                switchHelper.verifyRuleSectionsAreEmpty(it)
                switchHelper.verifyMeterSectionsAreEmpty(it)
            }
        }
    }

    def "Switch validation is able to detect meter info into the 'missing' section"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches.findAll { it.ofVersion != "OF_12" }
        def flow = flowHelper.addFlow(flowHelper.randomFlow(srcSwitch, dstSwitch))
        def srcSwitchCreatedMeterIds = getCreatedMeterIds(srcSwitch.dpId)
        def dstSwitchCreatedMeterIds = getCreatedMeterIds(dstSwitch.dpId)

        and: "Remove created meter on the srcSwitch"
        def srcSwitchCreatedCookies = getCookiesWithMeter(srcSwitch.dpId)
        def dstSwitchCreatedCookies = getCookiesWithMeter(dstSwitch.dpId)
        northbound.deleteMeter(srcSwitch.dpId, srcSwitchCreatedMeterIds[0])

        then: "Meters info/rules are moved into the 'missing' section on the srcSwitch only"
        def srcSwitchValidateInfo = northbound.validateSwitch(srcSwitch.dpId)
        srcSwitchValidateInfo.rules.missing.size() == 1
        srcSwitchValidateInfo.rules.missing == srcSwitchCreatedCookies
        srcSwitchValidateInfo.rules.proper.size() == 1
        srcSwitchValidateInfo.rules.proper == dstSwitchCreatedCookies

        srcSwitchValidateInfo.meters.missing.meterId.size() == 1
        srcSwitchValidateInfo.meters.missing*.meterId == srcSwitchCreatedMeterIds
        srcSwitchValidateInfo.meters.missing.cookie.size() == 1
        srcSwitchValidateInfo.meters.missing*.cookie == srcSwitchCreatedCookies

        Long srcSwitchBurstSize = switchHelper.getExpectedBurst(srcSwitch.dpId, flow.maximumBandwidth)
        srcSwitchValidateInfo.meters.missing.each {
            assert it.rate == flow.maximumBandwidth
            assert it.flowId == flow.id
            assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            verifyBurstSizeIsCorrect(srcSwitchBurstSize, it.burstSize)
        }

        and: "The rest sections are empty on the srcSwitch"
        switchHelper.verifyMeterSectionsAreEmpty(srcSwitchValidateInfo, ["misconfigured", "proper", "excess"])
        switchHelper.verifyRuleSectionsAreEmpty(srcSwitchValidateInfo, ["excess"])

        and: "Meters info/rules are NOT moved into the 'missing' section on the dstSwitch"
        def createdCookies = srcSwitchCreatedCookies + dstSwitchCreatedCookies
        def dstSwitchValidateInfo = northbound.validateSwitch(dstSwitch.dpId)
        dstSwitchValidateInfo.rules.proper.size() == 2
        dstSwitchValidateInfo.rules.proper.containsAll(createdCookies)

        dstSwitchValidateInfo.meters.proper.meterId.size() == 1
        dstSwitchValidateInfo.meters.proper*.meterId == dstSwitchCreatedMeterIds
        dstSwitchValidateInfo.meters.proper.cookie.size() == 1
        dstSwitchValidateInfo.meters.proper*.cookie == dstSwitchCreatedCookies

        Long dstSwitchBurstSize = switchHelper.getExpectedBurst(dstSwitch.dpId, flow.maximumBandwidth)
        dstSwitchValidateInfo.meters.proper.each {
            assert it.rate == flow.maximumBandwidth
            assert it.flowId == flow.id
            assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            verifyBurstSizeIsCorrect(dstSwitchBurstSize, it.burstSize)
        }

        and: "The rest sections are empty on the dstSwitch"
        switchHelper.verifyMeterSectionsAreEmpty(dstSwitchValidateInfo, ["missing", "misconfigured", "excess"])
        switchHelper.verifyRuleSectionsAreEmpty(dstSwitchValidateInfo, ["missing", "excess"])

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "Check that the switch validate request returns empty sections"
        Wrappers.wait(WAIT_OFFSET) {
            def srcSwitchValidateInfoAfterDelete = northbound.validateSwitch(srcSwitch.dpId)
            def dstSwitchValidateInfoAfterDelete = northbound.validateSwitch(dstSwitch.dpId)
            [srcSwitchValidateInfoAfterDelete, dstSwitchValidateInfoAfterDelete].each {
                switchHelper.verifyRuleSectionsAreEmpty(it)
                switchHelper.verifyMeterSectionsAreEmpty(it)
            }
        }
    }

    def "Switch validation is able to detect meter info into the 'excess' section"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches.findAll { it.ofVersion != "OF_12" }
        def flow = flowHelper.addFlow(flowHelper.randomFlow(srcSwitch, dstSwitch))
        def srcSwitchCreatedMeterIds = getCreatedMeterIds(srcSwitch.dpId)
        def dstSwitchCreatedMeterIds = getCreatedMeterIds(dstSwitch.dpId)

        and: "Update meterId for created flow directly via db"
        MeterId newMeterId = new MeterId(100)
        database.updateFlowMeterId(flow.id, newMeterId)

        then: "Origin meters are moved into the 'excess' section on the src and dst switches"
        def srcSwitchValidateInfo = northbound.validateSwitch(srcSwitch.dpId)
        def dstSwitchValidateInfo = northbound.validateSwitch(dstSwitch.dpId)

        [srcSwitchValidateInfo, dstSwitchValidateInfo].each {
            assert it.meters.excess.meterId.size() == 1
            it.meters.excess.each {
                assert it.rate == flow.maximumBandwidth
                assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            }
        }

        srcSwitchValidateInfo.meters.excess*.meterId.containsAll(srcSwitchCreatedMeterIds)
        dstSwitchValidateInfo.meters.excess*.meterId.containsAll(dstSwitchCreatedMeterIds)

        Long srcSwitchBurstSize = switchHelper.getExpectedBurst(srcSwitch.dpId, flow.maximumBandwidth)
        Long dstSwitchBurstSize = switchHelper.getExpectedBurst(dstSwitch.dpId, flow.maximumBandwidth)
        verifyBurstSizeIsCorrect(srcSwitchBurstSize, srcSwitchValidateInfo.meters.excess*.burstSize[0])
        verifyBurstSizeIsCorrect(dstSwitchBurstSize, dstSwitchValidateInfo.meters.excess*.burstSize[0])

        and: "Updated meters are moved in the 'missing' section on the src and dst switches"
        def srcSwitchCreatedCookies = getCookiesWithMeter(srcSwitch.dpId)
        srcSwitchValidateInfo.meters.missing*.meterId[0] == newMeterId.value
        srcSwitchValidateInfo.meters.missing*.cookie.containsAll(srcSwitchCreatedCookies)

        srcSwitchValidateInfo.meters.missing.each {
            assert it.rate == flow.maximumBandwidth
            assert it.flowId == flow.id
            assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            verifyBurstSizeIsCorrect(srcSwitchBurstSize, it.burstSize)
        }

        def dstSwitchCreatedCookies = getCookiesWithMeter(dstSwitch.dpId)
        dstSwitchValidateInfo.meters.missing*.meterId[0] == newMeterId.value
        dstSwitchValidateInfo.meters.missing*.cookie.containsAll(dstSwitchCreatedCookies)

        dstSwitchValidateInfo.meters.missing.each {
            assert it.rate == flow.maximumBandwidth
            assert it.flowId == flow.id
            assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            verifyBurstSizeIsCorrect(dstSwitchBurstSize, it.burstSize)
        }

        and: "The rest fields are empty on the src and dst switches"
        [srcSwitchValidateInfo, dstSwitchValidateInfo].each {
            switchHelper.verifyMeterSectionsAreEmpty(it, ["misconfigured", "proper"])
        }

        and: "Rules still exist in the 'proper' section on the src and dst switches"
        [srcSwitchValidateInfo, dstSwitchValidateInfo].each {
            switchHelper.verifyRuleSectionsAreEmpty(it, ["missing", "excess"])
        }

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        and: "Delete excess meters on the src and dst switches"
        srcSwitchCreatedMeterIds.each { northbound.deleteMeter(srcSwitch.dpId, it) }
        dstSwitchCreatedMeterIds.each { northbound.deleteMeter(dstSwitch.dpId, it) }

        then: "Check that the switch validate request returns empty sections"
        Wrappers.wait(WAIT_OFFSET) {
            def srcSwitchValidateInfoAfterDelete = northbound.validateSwitch(srcSwitch.dpId)
            def dstSwitchValidateInfoAfterDelete = northbound.validateSwitch(dstSwitch.dpId)
            [srcSwitchValidateInfoAfterDelete, dstSwitchValidateInfoAfterDelete].each {
                switchHelper.verifyRuleSectionsAreEmpty(it)
                switchHelper.verifyMeterSectionsAreEmpty(it)
            }
        }
    }

    def "Able to get empty switch validate information from the intermediate switch(flow contains > 2 switches)"() {
        given: "Two active not neighboring switches"
        def switchPair = topologyHelper.getAllNotNeighboringSwitchPairs().find { pair ->
            def possibleDefaultPaths = pair.paths.findAll { it.size() == pair.paths.min { it.size() }.size() }
            //ensure the path won't have only OF_12 intermediate switches
            !possibleDefaultPaths.find { path ->
                path[1..-2].every { it.switchId.description.contains("OF_12") }
            }
        }

        when: "Create an intermediate-switch flow"
        def flow = flowHelper.randomFlow(switchPair)
        flowHelper.addFlow(flow)
        def flowPath = PathHelper.convert(northbound.getFlowPath(flow.id))

        then: "The intermediate switch does not contain any information about meter"
        def switchToValidate = flowPath[1..-2].find { !it.switchId.description.contains("OF_12") }
        def intermediateSwitchValidateInfo = northbound.validateSwitch(switchToValidate.switchId)
        switchHelper.verifyMeterSectionsAreEmpty(intermediateSwitchValidateInfo)

        and: "Rules are stored in the 'proper' section on the transit switch"
        intermediateSwitchValidateInfo.rules.proper.size() == 2
        switchHelper.verifyRuleSectionsAreEmpty(intermediateSwitchValidateInfo, ["missing", "excess"])

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "Check that the switch validate request returns empty sections"
        flowPath.each {
            if (switchHelper.getDescription(it.switchId).contains("OF_13")) {
                def switchValidateInfo = northbound.validateSwitch(it.switchId)
                switchHelper.verifyMeterSectionsAreEmpty(switchValidateInfo)
                switchHelper.verifyRuleSectionsAreEmpty(switchValidateInfo)
            }
        }
    }

    def "Switch validate is able to detect rule info into the 'missing' section"() {
        given: "Two active not neighboring switches"
        def switchPair = topologyHelper.getAllNotNeighboringSwitchPairs().find { pair ->
            def possibleDefaultPaths = pair.paths.findAll { it.size() == pair.paths.min { it.size() }.size() }
            //ensure the path won't have only OF_12 intermediate switches
            def hasOf13Path = !possibleDefaultPaths.find { path ->
                path[1..-2].every { it.switchId.description.contains("OF_12") }
            }
            hasOf13Path && pair.src.ofVersion != "OF_12" && pair.dst.ofVersion != "OF_12"
        }

        and: "Create an intermediate-switch flow"
        def flow = flowHelper.randomFlow(switchPair)
        flowHelper.addFlow(flow)

        when: "Delete created rules on the srcSwitch"
        def createdCookies = northbound.getSwitchRules(switchPair.src.dpId).flowEntries.findAll {
            !Cookie.isDefaultRule(it.cookie)
        }*.cookie
        assert createdCookies.size() == 2

        northbound.deleteSwitchRules(switchPair.src.dpId, DeleteRulesAction.IGNORE_DEFAULTS)

        then: "Rule info is moved into the 'missing' section on the srcSwitch"
        def srcSwitchValidateInfo = northbound.validateSwitch(switchPair.src.dpId)
        srcSwitchValidateInfo.rules.missing.containsAll(createdCookies)
        switchHelper.verifyRuleSectionsAreEmpty(srcSwitchValidateInfo, ["proper", "excess"])

        and: "Rule info is NOT moved into the 'missing' section on the dstSwitch and transit switches"
        def dstSwitchValidateInfo = northbound.validateSwitch(switchPair.dst.dpId)
        dstSwitchValidateInfo.rules.proper.containsAll(createdCookies)
        switchHelper.verifyRuleSectionsAreEmpty(dstSwitchValidateInfo, ["missing", "excess"])
        def involvedSwitches = pathHelper.getInvolvedSwitches(flow.id)*.dpId
        def transitSwitches = involvedSwitches[1..-2].findAll { !it.description.contains("OF_12") }

        transitSwitches.each { switchId ->
            def transitSwitchValidateInfo = northbound.validateSwitch(switchId)
            assert transitSwitchValidateInfo.rules.proper.containsAll(createdCookies)
            assert transitSwitchValidateInfo.rules.proper.size() == 2
            switchHelper.verifyRuleSectionsAreEmpty(transitSwitchValidateInfo, ["missing", "excess"])
        }

        // TODO(andriidovhan) add synchronizeSwitch and check that rule inflo is moved back into the 'proper' section
        when: "Delete rules on the dstSwitch and transit switches"
        involvedSwitches[1..-1].each { switchId ->
            northbound.deleteSwitchRules(switchId, DeleteRulesAction.IGNORE_DEFAULTS)
        }

        then: "Rule info is moved into the 'missing' section on all involved switches"
        Wrappers.wait(WAIT_OFFSET) {
            involvedSwitches.findAll { !it.description.contains("OF_12") }.each { switchId ->
                def involvedSwitchValidateInfo = northbound.validateSwitch(switchId)
                assert involvedSwitchValidateInfo.rules.missing.containsAll(createdCookies)
                assert involvedSwitchValidateInfo.rules.missing.size() == 2
                switchHelper.verifyRuleSectionsAreEmpty(involvedSwitchValidateInfo, ["proper", "excess"])
            }
        }

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "Check that the switch validate request returns empty sections on all involved switches"
        Wrappers.wait(WAIT_OFFSET) {
            involvedSwitches.findAll { !it.description.contains("OF_12") }.each { switchId ->
                def switchValidateInfo = northbound.validateSwitch(switchId)
                switchHelper.verifyRuleSectionsAreEmpty(switchValidateInfo)
                switchHelper.verifyMeterSectionsAreEmpty(switchValidateInfo)
            }
        }
    }

    def "Switch validate is able to detect rule info in the 'excess' section"() {
        given: "Two active not neighboring switches"
        def switchPair = topologyHelper.getAllNotNeighboringSwitchPairs().find { pair ->
            def possibleDefaultPaths = pair.paths.findAll { it.size() == pair.paths.min { it.size() }.size() }
            //ensure the path won't have only OF_12 intermediate switches
            def hasOf13Path = !possibleDefaultPaths.find { path ->
                path[1..-2].every { it.switchId.description.contains("OF_12") }
            }
            hasOf13Path && pair.src.ofVersion != "OF_12" && pair.dst.ofVersion != "OF_12"
        }

        and: "Create an intermediate-switch flow"
        def flow = flowHelper.randomFlow(switchPair)
        flowHelper.addFlow(flow)
        def createdCookies = northbound.getSwitchRules(switchPair.src.dpId).flowEntries.findAll {
            !Cookie.isDefaultRule(it.cookie)
        }*.cookie
        createdCookies.size() == 2

        when: "Reproduce situation when switches have excess rules"
        def involvedSwitches = pathHelper.getInvolvedSwitches(flow.id)*.dpId

        def producer = new KafkaProducer(producerProps)
        //pick a meter id which is not yet used on src switch
        def excessMeterId = ((MIN_FLOW_METER_ID..100) - northbound.getAllMeters(switchPair.dst.dpId)
                .meterEntries*.meterId).first()
        producer.send(new ProducerRecord(flowTopic, switchPair.dst.dpId.toString(), buildMessage(
                new InstallEgressFlow(UUID.randomUUID(), flow.id, 1L, switchPair.dst.dpId, 1, 2, 1,
                        FlowEncapsulationType.TRANSIT_VLAN,1,
                        OutputVlanType.REPLACE, switchPair.dst.dpId)).toJson()))
        involvedSwitches[1..-1].findAll { !it.description.contains("OF_12") }.each { transitSw ->
            producer.send(new ProducerRecord(flowTopic, transitSw.toString(), buildMessage(
                    new InstallTransitFlow(UUID.randomUUID(), flow.id, 1L, transitSw, 1, 2, 1,
                    FlowEncapsulationType.TRANSIT_VLAN, switchPair.dst.dpId)).toJson()))
        }
        producer.send(new ProducerRecord(flowTopic, switchPair.src.dpId.toString(), buildMessage(
                new InstallIngressFlow(UUID.randomUUID(), flow.id, 1L, switchPair.src.dpId, 1, 2, 1, 1,
                        FlowEncapsulationType.TRANSIT_VLAN,
                        OutputVlanType.REPLACE, flow.maximumBandwidth, excessMeterId,
                        switchPair.dst.dpId)).toJson()))
        producer.flush()

        then: "System detects excess rules and store them in the 'excess' section"
        Wrappers.wait(WAIT_OFFSET) {
            def newCookiesSize = northbound.getSwitchRules(switchPair.src.dpId).flowEntries.findAll {
                !Cookie.isDefaultRule(it.cookie)
            }.size()
            newCookiesSize == createdCookies.size() + 1

            involvedSwitches.findAll { !it.description.contains("OF_12") }.each { switchId ->
                def involvedSwitchValidateInfo = northbound.validateSwitch(switchId)
                assert involvedSwitchValidateInfo.rules.proper.containsAll(createdCookies)
                assert involvedSwitchValidateInfo.rules.proper.size() == 2
                switchHelper.verifyRuleSectionsAreEmpty(involvedSwitchValidateInfo, ["missing"])

                assert involvedSwitchValidateInfo.rules.excess.size() == 1
                assert involvedSwitchValidateInfo.rules.excess == [1L]
            }
        }

        and: "System detects excess meter on the srcSwitch only"
        Long burstSize = switchHelper.getExpectedBurst(switchPair.src.dpId, flow.maximumBandwidth)
        def validateSwitchInfo = northbound.validateSwitch(switchPair.src.dpId)
        assert validateSwitchInfo.meters.excess.size() == 1
        assert validateSwitchInfo.meters.excess.each {
            assert it.rate == flow.maximumBandwidth
            assert it.meterId == excessMeterId
            assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            verifyBurstSizeIsCorrect(burstSize, it.burstSize)
        }
        involvedSwitches[1..-1].findAll { !it.description.contains("OF_12") }.each { switchId ->
            assert northbound.validateSwitch(switchId).meters.excess.empty
        }

        // TODO(andriidovhan) add synchronizeSwitch and check that rule inflo is moved back into the 'proper' section
        when: "Delete the flow and excess rules"
        flowHelper.deleteFlow(flow.id)
        northbound.deleteMeter(switchPair.src.dpId, excessMeterId)
        involvedSwitches.each { switchId ->
            northbound.deleteSwitchRules(switchId, DeleteRulesAction.IGNORE_DEFAULTS)
        }

        then: "Check that the switch validate request returns empty sections on all involved switches"
        Wrappers.wait(WAIT_OFFSET) {
            involvedSwitches.findAll { !it.description.contains("OF_12") }.each { switchId ->
                def switchValidateInfo = northbound.validateSwitch(switchId)
                switchHelper.verifyRuleSectionsAreEmpty(switchValidateInfo)
                switchHelper.verifyMeterSectionsAreEmpty(switchValidateInfo)
            }
        }

        cleanup:
        producer && producer.close()
    }

    @Memoized
    List<Switch> getNonCentecSwitches() {
        topology.activeSwitches.findAll { !it.centec && it.ofVersion == "OF_13" }
    }

    @Memoized
    List<Switch> getCentecSwitches() {
        topology.getActiveSwitches().findAll { it.centec }
    }

    List<Integer> getCreatedMeterIds(SwitchId switchId) {
        return northbound.getAllMeters(switchId).meterEntries.findAll { it.meterId > MAX_SYSTEM_RULE_METER_ID }*.meterId
    }

    List<Long> getCookiesWithMeter(SwitchId switchId) {
        return northbound.getSwitchRules(switchId).flowEntries.findAll {
            !Cookie.isDefaultRule(it.cookie) && it.instructions.goToMeter
        }*.cookie
    }

    void verifyBurstSizeIsCorrect(Long expected, Long actual) {
        assert Math.abs(expected - actual) <= 1
    }

    private static Message buildMessage(final CommandData data) {
        return new CommandMessage(data, System.currentTimeMillis(), UUID.randomUUID().toString(), null)
    }
}
