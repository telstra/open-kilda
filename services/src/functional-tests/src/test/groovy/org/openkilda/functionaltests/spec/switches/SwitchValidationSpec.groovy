package org.openkilda.functionaltests.spec.switches

import static org.junit.Assume.assumeTrue
import static org.openkilda.model.MeterId.MAX_SYSTEM_RULE_METER_ID
import static org.openkilda.model.MeterId.MIN_FLOW_METER_ID
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
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
import org.openkilda.model.MeterId
import org.openkilda.model.OutputVlanType
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v1.switches.SwitchValidationResult
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
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelper.addFlow(flowHelper.randomFlow(srcSwitch, dstSwitch))

        then: "One meter is automatically created on the src and dst switches"
        def srcSwitchCreatedMeterIds = getCreatedMeterIds(srcSwitch.dpId)
        def dstSwitchCreatedMeterIds = getCreatedMeterIds(dstSwitch.dpId)
        srcSwitchCreatedMeterIds.size() == 1
        dstSwitchCreatedMeterIds.size() == 1

        and: "The correct info is stored in the validate meter response"
        def srcSwitchValidateInfo = northbound.switchValidate(srcSwitch.dpId)
        def dstSwitchValidateInfo = northbound.switchValidate(dstSwitch.dpId)
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
            verifyMeterSectionsAreEmpty(it, ["missing", "misconfigured", "excess"])
        }

        and: "Created rules are stored in the 'proper' section"
        def createdCookies = srcSwitchCreatedCookies + dstSwitchCreatedCookies
        [srcSwitchValidateInfo, dstSwitchValidateInfo].each {
            it.rules.proper.containsAll(createdCookies)
        }

        and: "The rest fields in the 'rule' section are empty"
        [srcSwitchValidateInfo, dstSwitchValidateInfo].each {
            verifyRuleSectionsAreEmpty(it, ["missing", "excess"])
        }

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "Check that the switch validate request returns empty sections"
        Wrappers.wait(WAIT_OFFSET) {
            def srcSwitchValidateInfoAfterDelete = northbound.switchValidate(srcSwitch.dpId)
            def dstSwitchValidateInfoAfterDelete = northbound.switchValidate(dstSwitch.dpId)
            [srcSwitchValidateInfoAfterDelete, dstSwitchValidateInfoAfterDelete].each {
                verifyRuleSectionsAreEmpty(it)
                verifyMeterSectionsAreEmpty(it)
            }
        }
    }

    def "Switch validation is able to detect meter info into the 'misconfigured' section"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelper.addFlow(flowHelper.randomFlow(srcSwitch, dstSwitch))
        def srcSwitchCreatedMeterIds = getCreatedMeterIds(srcSwitch.dpId)
        def dstSwitchCreatedMeterIds = getCreatedMeterIds(dstSwitch.dpId)

        and: "Change bandwidth for the created flow directly in DB"
        def newBandwidth = flow.maximumBandwidth + 100
        /** at this point meter is set for given flow. Now update flow bandwidth directly via DB,
         it is done just for moving meter from the 'proper' section into the 'misconfigured'*/
        database.updateFlowBandwidth(flow.id, newBandwidth)
        //at this point existing meters do not correspond with the flow

        then: "Meters info is moved into the 'misconfigured' section"
        def srcSwitchValidateInfo = northbound.switchValidate(srcSwitch.dpId)
        def dstSwitchValidateInfo = northbound.switchValidate(dstSwitch.dpId)
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
            verifyMeterSectionsAreEmpty(it, ["missing", "proper", "excess"])
        }

        and: "Created rules are still stored in the 'proper' section"
        def createdCookies = srcSwitchCreatedCookies + dstSwitchCreatedCookies
        [srcSwitchValidateInfo, dstSwitchValidateInfo].each {
            it.rules.proper.containsAll(createdCookies)
            verifyRuleSectionsAreEmpty(it, ["missing", "excess"])
        }

        when: "Restore correct bandwidth via DB"
        database.updateFlowBandwidth(flow.id, flow.maximumBandwidth)

        then: "Misconfigured meters are moved into the 'proper' section"
        def srcSwitchValidateInfoRestored = northbound.switchValidate(srcSwitch.dpId)
        def dstSwitchValidateInfoRestored = northbound.switchValidate(dstSwitch.dpId)

        srcSwitchValidateInfoRestored.meters.proper*.meterId.containsAll(srcSwitchCreatedMeterIds)
        dstSwitchValidateInfoRestored.meters.proper*.meterId.containsAll(dstSwitchCreatedMeterIds)

        [srcSwitchValidateInfoRestored, dstSwitchValidateInfoRestored].each {
            verifyMeterSectionsAreEmpty(it, ["missing", "misconfigured", "excess"])
        }

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "Check that the switch validate request returns empty sections"
        Wrappers.wait(WAIT_OFFSET) {
            def srcSwitchValidateInfoAfterDelete = northbound.switchValidate(srcSwitch.dpId)
            def dstSwitchValidateInfoAfterDelete = northbound.switchValidate(dstSwitch.dpId)
            [srcSwitchValidateInfoAfterDelete, dstSwitchValidateInfoAfterDelete].each {
                verifyRuleSectionsAreEmpty(it)
                verifyMeterSectionsAreEmpty(it)
            }
        }
    }

    def "Switch validation is able to detect meter info into the 'missing' section"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelper.addFlow(flowHelper.randomFlow(srcSwitch, dstSwitch))
        def srcSwitchCreatedMeterIds = getCreatedMeterIds(srcSwitch.dpId)
        def dstSwitchCreatedMeterIds = getCreatedMeterIds(dstSwitch.dpId)

        and: "Remove created meter on the srcSwitch"
        def srcSwitchCreatedCookies = getCookiesWithMeter(srcSwitch.dpId)
        def dstSwitchCreatedCookies = getCookiesWithMeter(dstSwitch.dpId)
        northbound.deleteMeter(srcSwitch.dpId, srcSwitchCreatedMeterIds[0])

        then: "Meters info/rules are moved into the 'missing' section on the srcSwitch only"
        def srcSwitchValidateInfo = northbound.switchValidate(srcSwitch.dpId)
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
        verifyMeterSectionsAreEmpty(srcSwitchValidateInfo, ["misconfigured", "proper", "excess"])
        verifyRuleSectionsAreEmpty(srcSwitchValidateInfo, ["excess"])

        and: "Meters info/rules are NOT moved into the 'missing' section on the dstSwitch"
        def createdCookies = srcSwitchCreatedCookies + dstSwitchCreatedCookies
        def dstSwitchValidateInfo = northbound.switchValidate(dstSwitch.dpId)
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
        verifyMeterSectionsAreEmpty(dstSwitchValidateInfo, ["missing", "misconfigured", "excess"])
        verifyRuleSectionsAreEmpty(dstSwitchValidateInfo, ["missing", "excess"])

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "Check that the switch validate request returns empty sections"
        Wrappers.wait(WAIT_OFFSET) {
            def srcSwitchValidateInfoAfterDelete = northbound.switchValidate(srcSwitch.dpId)
            def dstSwitchValidateInfoAfterDelete = northbound.switchValidate(dstSwitch.dpId)
            [srcSwitchValidateInfoAfterDelete, dstSwitchValidateInfoAfterDelete].each {
                verifyRuleSectionsAreEmpty(it)
                verifyMeterSectionsAreEmpty(it)
            }
        }
    }

    def "Switch validation is able to detect meter info into the 'excess' section"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelper.addFlow(flowHelper.randomFlow(srcSwitch, dstSwitch))
        def srcSwitchCreatedMeterIds = getCreatedMeterIds(srcSwitch.dpId)
        def dstSwitchCreatedMeterIds = getCreatedMeterIds(dstSwitch.dpId)

        and: "Update meterId for created flow directly via db"
        MeterId newMeterId = new MeterId(100)
        database.updateFlowMeterId(flow.id, newMeterId)

        then: "Origin meters are moved into the 'excess' section on the src and dst switches"
        def srcSwitchValidateInfo = northbound.switchValidate(srcSwitch.dpId)
        def dstSwitchValidateInfo = northbound.switchValidate(dstSwitch.dpId)

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
            verifyMeterSectionsAreEmpty(it, ["misconfigured", "proper"])
        }

        and: "Rules still exist in the 'proper' section on the src and dst switches"
        [srcSwitchValidateInfo, dstSwitchValidateInfo].each {
            verifyRuleSectionsAreEmpty(it, ["missing", "excess"])
        }

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        and: "Delete excess meters on the src and dst switches"
        srcSwitchCreatedMeterIds.each { northbound.deleteMeter(srcSwitch.dpId, it) }
        dstSwitchCreatedMeterIds.each { northbound.deleteMeter(dstSwitch.dpId, it) }

        then: "Check that the switch validate request returns empty sections"
        Wrappers.wait(WAIT_OFFSET) {
            def srcSwitchValidateInfoAfterDelete = northbound.switchValidate(srcSwitch.dpId)
            def dstSwitchValidateInfoAfterDelete = northbound.switchValidate(dstSwitch.dpId)
            [srcSwitchValidateInfoAfterDelete, dstSwitchValidateInfoAfterDelete].each {
                verifyRuleSectionsAreEmpty(it)
                verifyMeterSectionsAreEmpty(it)
            }
        }
    }

    def "Able to get empty switch validate information from the intermediate switch(flow contain > 2 switches)"() {
        when: "Create an intermediate-switch flow"
        def flow = intermediateSwitchFlow()
        flowHelper.addFlow(flow)
        def flowPath = PathHelper.convert(northbound.getFlowPath(flow.id))

        then: "The intermediate switch does not contain any information about meter"
        def intermediateSwitchValidateInfo = northbound.switchValidate(flowPath[1].switchId)
        verifyMeterSectionsAreEmpty(intermediateSwitchValidateInfo)

        and: "Rules are stored in the 'proper' section on the transit switch"
        intermediateSwitchValidateInfo.rules.proper.size() == 2
        verifyRuleSectionsAreEmpty(intermediateSwitchValidateInfo, ["missing", "excess"])

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "Check that the switch validate request returns empty sections"
        flowPath.each {
            if (switchHelper.getDescription(it.switchId).contains("OF_13")) {
                def switchValidateInfo = northbound.switchValidate(it.switchId)
                verifyMeterSectionsAreEmpty(switchValidateInfo)
                verifyRuleSectionsAreEmpty(switchValidateInfo)
            }
        }
    }

    def "Switch validate is able to detect rule info into the 'missing' section"() {
        given: "Two active not neighboring switches"
        def switches = topology.getActiveSwitches()
        def allLinks = northbound.getAllLinks()
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            allLinks.every { link -> !(link.source.switchId == src.dpId && link.destination.switchId == dst.dpId) }
        } ?: assumeTrue("No suiting switches found", false)

        and: "Create an intermediate-switch flow"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow)

        when: "Delete created rules on the srcSwitch"
        def createdCookies = northbound.getSwitchRules(srcSwitch.dpId).flowEntries.findAll {
            !Cookie.isDefaultRule(it.cookie)
        }*.cookie
        assert createdCookies.size() == 2

        northbound.deleteSwitchRules(srcSwitch.dpId, DeleteRulesAction.IGNORE_DEFAULTS)

        then: "Rule info is moved into the 'missing' section on the srcSwitch"
        def srcSwitchValidateInfo = northbound.switchValidate(srcSwitch.dpId)
        srcSwitchValidateInfo.rules.missing.containsAll(createdCookies)
        verifyRuleSectionsAreEmpty(srcSwitchValidateInfo, ["proper", "excess"])

        and: "Rule info is NOT moved into the 'missing' section on the dstSwitch and transit switches"
        def dstSwitchValidateInfo = northbound.switchValidate(dstSwitch.dpId)
        dstSwitchValidateInfo.rules.proper.containsAll(createdCookies)
        verifyRuleSectionsAreEmpty(dstSwitchValidateInfo, ["missing", "excess"])
        def involvedSwitches = pathHelper.getInvolvedSwitches(flow.id)*.dpId
        def transitSwitches = involvedSwitches[1..-2]

        transitSwitches.each { switchId ->
            def transitSwitchValidateInfo = northbound.switchValidate(switchId)
            assert transitSwitchValidateInfo.rules.proper.containsAll(createdCookies)
            assert transitSwitchValidateInfo.rules.proper.size() == 2
            verifyRuleSectionsAreEmpty(transitSwitchValidateInfo, ["missing", "excess"])
        }

        when: "Delete rules on the dstSwitch and transit switches"
        involvedSwitches[1..-1].each { switchId ->
            northbound.deleteSwitchRules(switchId, DeleteRulesAction.IGNORE_DEFAULTS)
        }

        then: "Rule info is moved into the 'missing' section on all involved switches"
        Wrappers.wait(WAIT_OFFSET) {
            involvedSwitches.each { switchId ->
                def involvedSwitchValidateInfo = northbound.switchValidate(switchId)
                assert involvedSwitchValidateInfo.rules.missing.containsAll(createdCookies)
                assert involvedSwitchValidateInfo.rules.missing.size() == 2
                verifyRuleSectionsAreEmpty(involvedSwitchValidateInfo, ["proper", "excess"])
            }
        }

        when: "Try to synchronize all involved switches"
        def syncResultsMap = involvedSwitches.collectEntries {
            switchId -> [switchId, northbound.synchronizeSwitch(switchId, false)]
        }

        then: "System detects missing rules, then installs them"
        involvedSwitches.each { switchId ->
            assert syncResultsMap[switchId].rules.missing.size() == 2
            assert syncResultsMap[switchId].rules.missing.containsAll(createdCookies)
            assert syncResultsMap[switchId].rules.installed.size() == 2
            assert syncResultsMap[switchId].rules.installed.containsAll(createdCookies)
        }

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "Check that the switch validate request returns empty sections on all involved switches"
        Wrappers.wait(WAIT_OFFSET) {
            involvedSwitches.each { switchId ->
                def switchValidateInfo = northbound.switchValidate(switchId)
                verifyRuleSectionsAreEmpty(switchValidateInfo)
                verifyMeterSectionsAreEmpty(switchValidateInfo)
            }
        }
    }

    def "Switch validate is able to detect rule info into the 'excess' section"() {
        given: "Two active not neighboring switches"
        def switches = topology.getActiveSwitches()
        def allLinks = northbound.getAllLinks()
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            allLinks.every { link -> !(link.source.switchId == src.dpId && link.destination.switchId == dst.dpId) }
        } ?: assumeTrue("No suiting switches found", false)

        and: "Create an intermediate-switch flow"
        def flow = flowHelper.randomFlow(srcSwitch, dstSwitch)
        flowHelper.addFlow(flow)
        def createdCookies = northbound.getSwitchRules(srcSwitch.dpId).flowEntries.findAll {
            !Cookie.isDefaultRule(it.cookie)
        }*.cookie
        createdCookies.size() == 2

        when: "Reproduce situation when switches have excess rules"
        def involvedSwitches = pathHelper.getInvolvedSwitches(flow.id)*.dpId

        def producer = new KafkaProducer(producerProps)
        //pick a meter id which is not yet used on src switch
        def excessMeterId = ((MIN_FLOW_METER_ID..100) - northbound.getAllMeters(dstSwitch.dpId)
                .meterEntries*.meterId).first()
        producer.send(new ProducerRecord(flowTopic, dstSwitch.dpId.toString(), buildMessage(
                new InstallEgressFlow(UUID.randomUUID(), flow.id, 1L, dstSwitch.dpId, 1, 2, 1, 1,
                        OutputVlanType.REPLACE)).toJson()))
        involvedSwitches[1..-1].each { transitSw ->
            producer.send(new ProducerRecord(flowTopic, transitSw.toString(), buildMessage(
                    new InstallTransitFlow(UUID.randomUUID(), flow.id, 1L, transitSw, 1, 2, 1)).toJson()))
        }
        producer.send(new ProducerRecord(flowTopic, srcSwitch.dpId.toString(), buildMessage(
                new InstallIngressFlow(UUID.randomUUID(), flow.id, 1L, srcSwitch.dpId, 1, 2, 1, 1,
                        OutputVlanType.REPLACE, flow.maximumBandwidth, excessMeterId)).toJson()))

        then: "System detects excess rules and store them in the 'excess' section"
        def newCookiesSize = northbound.getSwitchRules(srcSwitch.dpId).flowEntries.findAll {
            !Cookie.isDefaultRule(it.cookie)
        }.size()
        newCookiesSize == createdCookies.size() + 1

        Wrappers.wait(WAIT_OFFSET) {
            involvedSwitches.each { switchId ->
                def involvedSwitchValidateInfo = northbound.switchValidate(switchId)
                assert involvedSwitchValidateInfo.rules.proper.containsAll(createdCookies)
                assert involvedSwitchValidateInfo.rules.proper.size() == 2
                verifyRuleSectionsAreEmpty(involvedSwitchValidateInfo, ["missing"])

                assert involvedSwitchValidateInfo.rules.excess.size() == 1
                assert involvedSwitchValidateInfo.rules.excess == [1L]
            }
        }

        and: "System detects excess meter on the srcSwitch only"
        Long burstSize = switchHelper.getExpectedBurst(srcSwitch.dpId, flow.maximumBandwidth)
        def validateSwitchInfo = northbound.switchValidate(srcSwitch.dpId)
        assert validateSwitchInfo.meters.excess.size() == 1
        assert validateSwitchInfo.meters.excess.each {
            assert it.rate == flow.maximumBandwidth
            assert it.meterId == excessMeterId
            assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            verifyBurstSizeIsCorrect(burstSize, it.burstSize)
        }
        involvedSwitches[1..-1].each { switchId ->
            assert northbound.switchValidate(switchId).meters.excess.empty
        }

        when: "Try to synchronize the src switch"
        def syncResultsMap = involvedSwitches.collectEntries { switchId ->
            [switchId, northbound.synchronizeSwitch(switchId, true)]
        }

        then: "System detects excess rules and meters, then deletes them"
        involvedSwitches.each { switchId ->
            assert syncResultsMap[switchId].rules.excess.size() == 1
            assert syncResultsMap[switchId].rules.excess[0] == 1L
            assert syncResultsMap[switchId].rules.removed.size() == 1
            assert syncResultsMap[switchId].rules.removed[0] == 1L
        }
        assert syncResultsMap[srcSwitch.dpId].meters.excess.size() == 1
        assert syncResultsMap[srcSwitch.dpId].meters.excess.meterId[0] == excessMeterId
        assert syncResultsMap[srcSwitch.dpId].meters.removed.size() == 1
        assert syncResultsMap[srcSwitch.dpId].meters.removed.meterId[0] == excessMeterId

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "Check that the switch validate request returns empty sections on all involved switches"
        Wrappers.wait(WAIT_OFFSET) {
            involvedSwitches.each { switchId ->
                def switchValidateInfo = northbound.switchValidate(switchId)
                verifyRuleSectionsAreEmpty(switchValidateInfo)
                verifyMeterSectionsAreEmpty(switchValidateInfo)
            }
        }
    }

    def intermediateSwitchFlow(boolean ensureAltPathsExist = false, boolean getAllPaths = false) {
        def flowWithPaths = getFlowWithPaths(3, Integer.MAX_VALUE, ensureAltPathsExist ? 1 : 0)
        return getAllPaths ? flowWithPaths : flowWithPaths[0]
    }

    def getFlowWithPaths(int minSwitchesInPath, int maxSwitchesInPath, int minAltPaths) {
        def allFlowPaths = []
        def switches = topology.getActiveSwitches()
        def switchPair = [switches, switches].combinations().findAll {
            src, dst -> src != dst
        }.unique {
            it.sort()
        }.find { src, dst ->
            allFlowPaths = database.getPaths(src.dpId, dst.dpId)*.path
            allFlowPaths.size() > minAltPaths && allFlowPaths.min {
                it.size()
            }.size() in (minSwitchesInPath..maxSwitchesInPath)
        } ?: assumeTrue("No suiting switches found", false)

        return [flowHelper.randomFlow(switchPair[0], switchPair[1]), allFlowPaths]
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

    void verifyMeterSectionsAreEmpty(SwitchValidationResult switchValidateInfo,
                                     List<String> sections = ["missing", "misconfigured", "proper", "excess"]) {
        sections.each {
            assert switchValidateInfo.meters."$it".empty
        }
    }

    void verifyRuleSectionsAreEmpty(SwitchValidationResult switchValidateInfo,
                                    List<String> sections = ["missing", "proper", "excess"]) {
        sections.each {
            assert switchValidateInfo.rules."$it".empty
        }
    }

    void verifyBurstSizeIsCorrect(Long expected, Long actual) {
        assert Math.abs(expected - actual) <= 1
    }

    private static Message buildMessage(final CommandData data) {
        return new CommandMessage(data, System.currentTimeMillis(), UUID.randomUUID().toString(), null);
    }
}
