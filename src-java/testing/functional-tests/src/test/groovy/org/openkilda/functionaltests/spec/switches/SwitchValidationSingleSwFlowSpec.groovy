package org.openkilda.functionaltests.spec.switches

import org.openkilda.messaging.model.FlowDirectionType

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.helpers.SwitchHelper.isDefaultMeter
import static org.openkilda.model.MeterId.MAX_SYSTEM_RULE_METER_ID
import static org.openkilda.model.MeterId.MIN_FLOW_METER_ID
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.tools.KafkaUtils.buildMessage

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.model.MeterId
import org.openkilda.model.SwitchId
import org.openkilda.model.cookie.Cookie
import org.openkilda.rulemanager.FlowSpeakerData
import org.openkilda.rulemanager.Instructions
import org.openkilda.rulemanager.MeterFlag
import org.openkilda.rulemanager.MeterSpeakerData
import org.openkilda.rulemanager.OfTable
import org.openkilda.rulemanager.OfVersion
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import com.google.common.collect.Sets
import groovy.transform.Memoized
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import spock.lang.Narrative
import spock.lang.See

@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/hub-and-spoke/switch-validate")
@Narrative("""This test suite checks the switch validate feature on a single flow switch.
Description of fields:
- missing - those meters/rules, which are NOT present on a switch, but are present in db
- misconfigured - those meters which have different value (on a switch and in db) for the same parameter
- excess - those meters/rules, which are present on a switch, but are NOT present in db
- proper - meters/rules values are the same on a switch and in db
""")
@Tags([SMOKE_SWITCHES])
class SwitchValidationSingleSwFlowSpec extends HealthCheckSpecification {
    @Value("#{kafkaTopicsConfig.getSpeakerSwitchManagerTopic()}")
    String speakerTopic
    @Autowired
    @Qualifier("kafkaProducerProperties")
    Properties producerProps

    @Tidy
    @Tags([TOPOLOGY_DEPENDENT, SMOKE])
    def "Switch validation is able to store correct information on a #switchType switch in the 'proper' section"() {
        assumeTrue(switches as boolean, "Unable to find required switches in topology")

        setup: "Select a #switchType switch and retrieve default meters"
        def sw = switches.first()

        when: "Create a flow"
        def flow = flowHelperV2.addFlow(flowHelperV2.singleSwitchFlow(sw))
        def meterIds = getCreatedMeterIds(sw.dpId)
        Long burstSize = switchHelper.getExpectedBurst(sw.dpId, flow.maximumBandwidth)

        then: "Two meters are automatically created."
        meterIds.size() == 2

        and: "The correct info is stored in the 'proper' section"
        def switchValidateInfo = northbound.validateSwitch(sw.dpId)
        switchValidateInfo.meters.proper.collect { it.meterId }.containsAll(meterIds)

        def createdCookies = getCookiesWithMeter(sw.dpId)
        switchValidateInfo.meters.proper*.cookie.containsAll(createdCookies)

        def properMeters = switchValidateInfo.meters.proper.findAll({ !isDefaultMeter(it) })
        properMeters.each {
            verifyRateIsCorrect(sw, it.rate, flow.maximumBandwidth)
            assert it.flowId == flow.flowId
            assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            switchHelper.verifyBurstSizeIsCorrect(sw, burstSize, it.burstSize)
        }

        switchValidateInfo.verifyMeterSectionsAreEmpty(["missing", "misconfigured", "excess"])

        and: "Created rules are stored in the 'proper' section"
        switchValidateInfo.rules.proper.containsAll(createdCookies)

        and: "The rest fields in the 'rule' section are empty"
        switchValidateInfo.verifyRuleSectionsAreEmpty(["missing", "excess"])

        when: "Delete the flow"
        def deleteFlow = flowHelperV2.deleteFlow(flow.flowId)

        then: "Check that the switch validate request returns empty sections"
        Wrappers.wait(WAIT_OFFSET) {
            def switchValidateInfoAfterDelete = northbound.validateSwitch(sw.dpId)
            switchValidateInfoAfterDelete.verifyRuleSectionsAreEmpty()
            switchValidateInfoAfterDelete.verifyMeterSectionsAreEmpty()
        }

        cleanup:
        flow && !deleteFlow && flowHelperV2.deleteFlow(flow.flowId)

        where:
        switchType         | switches
        "Centec"           | getCentecSwitches()
        "Noviflow"         | getNoviflowSwitches()
        "Noviflow(Wb5164)" | getNoviflowWb5164()
        "OVS"              | getVirtualSwitches()
    }

    @Tags([TOPOLOGY_DEPENDENT])
    def "Switch validation is able to detect meter info into the 'misconfigured' section on a #switchType switch"() {
        assumeTrue(switches as boolean, "Unable to find required switches in topology")

        setup: "Select a #switchType switch and retrieve default meters"
        def sw = switches.first()

        when: "Create a flow"
        def amountOfMultiTableFlRules = switchHelper.getCachedSwProps(sw.dpId).multiTable ? 4 : 0 //2 SHARED_OF_FLOW, 2 MULTI_TABLE_INGRESS_RULES
        def amountOfFlowRules = 2 //SERVICE_OR_FLOW_SEGMENT(ingress/egress)
        def amountOfSwRules = northbound.getSwitchRules(sw.dpId).flowEntries.size()
        def amountOfRules = amountOfSwRules + amountOfFlowRules + amountOfMultiTableFlRules
        def amountOfMeters = northbound.getAllMeters(sw.dpId).meterEntries.size()
        def amountOfFlowMeters = 2
        def flow = flowHelperV2.addFlow(flowHelperV2.singleSwitchFlow(sw).tap { it.maximumBandwidth = 5000 })
        def meterIds = getCreatedMeterIds(sw.dpId)
        Long burstSize = switchHelper.getExpectedBurst(sw.dpId, flow.maximumBandwidth)

        and: "Change bandwidth for the created flow directly in DB"
        Long newBandwidth = flow.maximumBandwidth + 100
        /** at this point meters are set for given flow. Now update flow bandwidth directly via DB,
         it is done just for moving meters from the 'proper' section into the 'misconfigured'*/
        database.updateFlowBandwidth(flow.flowId, newBandwidth)
        //at this point existing meters do not correspond with the flow

        then: "Meters info is moved into the 'misconfigured' section"
        def switchValidateInfo = northbound.validateSwitch(sw.dpId)
        def createdCookies = getCookiesWithMeter(sw.dpId)
        switchValidateInfo.meters.misconfigured.meterId.size() == 2
        switchValidateInfo.meters.misconfigured*.meterId.containsAll(meterIds)

        switchValidateInfo.meters.misconfigured*.cookie.containsAll(createdCookies)
        switchValidateInfo.meters.misconfigured.each {
            verifyRateIsCorrect(sw, it.rate, flow.maximumBandwidth)
            assert it.flowId == flow.flowId
            assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            switchHelper.verifyBurstSizeIsCorrect(sw, burstSize, it.burstSize)
        }

        and: "Reason is specified why meters are misconfigured"
        switchValidateInfo.meters.misconfigured.each {
            verifyRateIsCorrect(sw, it.actual.rate, flow.maximumBandwidth)
            assert it.expected.rate == newBandwidth
        }

        and: "The rest fields are empty"
        switchValidateInfo.verifyMeterSectionsAreEmpty(["proper", "missing", "excess"])

        and: "Created rules are still stored in the 'proper' section"
        switchValidateInfo.rules.proper.containsAll(createdCookies)

        and: "Flow validation shows discrepancies"
        def flowValidateResponse = northbound.validateFlow(flow.flowId)
        def expectedRulesCount = [
                flowHelperV2.getFlowRulesCountBySwitch(flow, true, 1),
                flowHelperV2.getFlowRulesCountBySwitch(flow, false, 1)]
        flowValidateResponse.eachWithIndex { direction, i ->
            assert direction.discrepancies.size() == 2

            def rate = direction.discrepancies[0]
            assert rate.field == "meterRate"
            assert rate.expectedValue == newBandwidth.toString()
            verifyRateIsCorrect(sw, rate.actualValue.toLong(), flow.maximumBandwidth)

            def burst = direction.discrepancies[1]
            assert burst.field == "meterBurstSize"
            Long newBurstSize = switchHelper.getExpectedBurst(sw.dpId, newBandwidth)
            switchHelper.verifyBurstSizeIsCorrect(sw, newBurstSize, burst.expectedValue.toLong())
            switchHelper.verifyBurstSizeIsCorrect(sw, burstSize, burst.actualValue.toLong())

            assert direction.flowRulesTotal == ((FlowDirectionType.FORWARD.toString() == direction.direction) ?
                    expectedRulesCount[0] : expectedRulesCount[1])
            assert direction.switchRulesTotal == amountOfRules
            assert direction.flowMetersTotal == 1
            assert direction.switchMetersTotal == amountOfMeters + amountOfFlowMeters
        }

        when: "Reset meters for the flow"
        northbound.resetMeters(flow.flowId)

        then: "Misconfigured meters are reinstalled according to the new bandwidth and moved into the 'proper' section"
        with(northbound.validateSwitch(sw.dpId)) {
            it.meters.proper.findAll { it.meterId in meterIds }.each { assert it.rate == newBandwidth }
            it.verifyMeterSectionsAreEmpty(["missing", "misconfigured", "excess"])
        }

        and: "Flow validation shows no discrepancies"
        northbound.validateFlow(flow.flowId).each { direction ->
            assert direction.discrepancies.empty
            assert direction.asExpected
        }

        when: "Delete the flow"
        flowHelperV2.deleteFlow(flow.flowId)

        then: "Check that the switch validate request returns empty sections"
        Wrappers.wait(WAIT_OFFSET) {
            def switchValidateInfoAfterDelete = northbound.validateSwitch(sw.dpId)
            switchValidateInfoAfterDelete.verifyRuleSectionsAreEmpty()
            switchValidateInfoAfterDelete.verifyMeterSectionsAreEmpty()
        }

        where:
        switchType         | switches
        "Centec"           | getCentecSwitches()
        "Noviflow"         | getNoviflowSwitches()
        "Noviflow(Wb5164)" | getNoviflowWb5164()
        "OVS"              | getVirtualSwitches()
    }

    @Tags([TOPOLOGY_DEPENDENT])
    def "Switch validation is able to detect meter info into the 'missing' section on a #switchType switch"() {
        assumeTrue(switches as boolean, "Unable to find required switches in topology")

        setup: "Select a #switchType switch and retrieve default meters"
        def sw = switches.first()

        when: "Create a flow"
        def flow = flowHelperV2.addFlow(flowHelperV2.singleSwitchFlow(sw))
        def meterIds = getCreatedMeterIds(sw.dpId)

        and: "Remove created meter"
        northbound.deleteMeter(sw.dpId, meterIds[0])
        northbound.deleteMeter(sw.dpId, meterIds[1])

        then: "Meters info/rules are moved into the 'missing' section"
        Long burstSize = switchHelper.getExpectedBurst(sw.dpId, flow.maximumBandwidth)
        def switchValidateInfo = northbound.validateSwitch(sw.dpId)
        def createdCookies = getCookiesWithMeter(sw.dpId)
        switchValidateInfo.meters.missing.meterId.size() == 2
        switchValidateInfo.rules.missing.containsAll(createdCookies)
        switchValidateInfo.meters.missing*.meterId.containsAll(meterIds)
        switchValidateInfo.meters.missing*.cookie.containsAll(createdCookies)
        switchValidateInfo.meters.missing.each {
            verifyRateIsCorrect(sw, it.rate, flow.maximumBandwidth)
            assert it.flowId == flow.flowId
            assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            switchHelper.verifyBurstSizeIsCorrect(sw, burstSize, it.burstSize)
        }

        and: "The rest fields are empty"
        switchValidateInfo.verifyRuleSectionsAreEmpty(["proper", "excess"])
        switchValidateInfo.verifyMeterSectionsAreEmpty(["proper", "misconfigured", "excess"])

        when: "Try to synchronize the switch"
        def syncResponse = northbound.synchronizeSwitch(sw.dpId, false)

        then: "System detects missing rules and meters, then installs them"
        syncResponse.rules.missing.size() == 2
        syncResponse.rules.missing.containsAll(createdCookies)
        syncResponse.rules.installed.size() == 2
        syncResponse.rules.installed.containsAll(createdCookies)
        syncResponse.meters.missing.size() == 2
        syncResponse.meters.missing*.meterId.containsAll(meterIds)
        syncResponse.meters.installed.size() == 2
        syncResponse.meters.installed*.meterId.containsAll(meterIds)

        when: "Delete the flow"
        def deleteFlow = flowHelperV2.deleteFlow(flow.flowId)

        then: "Check that the switch validate request returns empty sections"
        Wrappers.wait(WAIT_OFFSET) {
            def switchValidateInfoAfterDelete = northbound.validateSwitch(sw.dpId)
            switchValidateInfoAfterDelete.verifyRuleSectionsAreEmpty()
            switchValidateInfoAfterDelete.verifyMeterSectionsAreEmpty()
        }
        def testIsCompleted = true

        cleanup:
        flow && !deleteFlow && flowHelperV2.deleteFlow(flow.flowId)
        if (!testIsCompleted) {
            northbound.synchronizeSwitch(sw.dpId, true)
            Wrappers.wait(RULES_INSTALLATION_TIME) {
                assert northbound.getSwitchRules(sw.dpId).flowEntries*.cookie.sort() == sw.defaultCookies.sort()
            }
        }

        where:
        switchType         | switches
        "Centec"           | getCentecSwitches()
        "Noviflow"         | getNoviflowSwitches()
        "Noviflow(Wb5164)" | getNoviflowWb5164()
        "OVS"              | getVirtualSwitches()
    }

    @Tags([TOPOLOGY_DEPENDENT])
    def "Switch validation is able to detect meter info into the 'excess' section on a #switchType switch"() {
        assumeTrue(switches as boolean, "Unable to find required switches in topology")

        setup: "Select a #switchType switch and retrieve default meters"
        def sw = switches.first()

        when: "Create a flow"
        def flow = flowHelperV2.addFlow(flowHelperV2.singleSwitchFlow(sw))
        def metersIds = getCreatedMeterIds(sw.dpId)
        Long burstSize = switchHelper.getExpectedBurst(sw.dpId, flow.maximumBandwidth)

        then: "Rules and meters are created"
        def swValidateInfo = northbound.validateSwitch(sw.dpId)
        def properMeters = swValidateInfo.meters.proper.findAll({ !isDefaultMeter(it) })
        def amountOfFlowRules = switchHelper.getCachedSwProps(sw.dpId).multiTable ? 4 : 2
        properMeters.meterId.size() == 2
        swValidateInfo.rules.proper.findAll { !new Cookie(it).serviceFlag }.size() == amountOfFlowRules

        when: "Update meterId for created flow directly via db"
        long newMeterId = 100;
        database.updateFlowMeterId(flow.flowId, newMeterId)

        then: "Origin meters are moved into the 'excess' section"
        def switchValidateInfo = northbound.validateSwitch(sw.dpId)
        switchValidateInfo.meters.excess.meterId.size() == 2
        switchValidateInfo.meters.excess.collect { it.meterId }.containsAll(metersIds)
        switchValidateInfo.meters.excess.each {
            verifyRateIsCorrect(sw, it.rate, flow.maximumBandwidth)
            assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            switchHelper.verifyBurstSizeIsCorrect(sw, burstSize, it.burstSize)
        }

        and: "Updated meters are stored in the 'missing' section"
        def createdCookies = getCookiesWithMeter(sw.dpId)
        switchValidateInfo.meters.missing.collect { it.cookie }.containsAll(createdCookies)
        switchValidateInfo.meters.missing.each {
            verifyRateIsCorrect(sw, it.rate, flow.maximumBandwidth)
            assert it.flowId == flow.flowId
            assert it.meterId == newMeterId || it.meterId == newMeterId + 1
            assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            switchHelper.verifyBurstSizeIsCorrect(sw, burstSize, it.burstSize)
        }

        and: "Rules still exist in the 'proper' section"
        switchValidateInfo.verifyRuleSectionsAreEmpty(["missing", "excess"])

        when: "Delete the flow"
        flowHelperV2.deleteFlow(flow.flowId)

        and: "Delete excess meters"
        metersIds.each { northbound.deleteMeter(sw.dpId, it) }

        then: "Check that the switch validate request returns empty sections"
        Wrappers.wait(WAIT_OFFSET) {
            def switchValidateInfoAfterDelete = northbound.validateSwitch(sw.dpId)
            switchValidateInfoAfterDelete.verifyRuleSectionsAreEmpty()
            switchValidateInfoAfterDelete.verifyMeterSectionsAreEmpty()
        }

        where:
        switchType         | switches
        "Centec"           | getCentecSwitches()
        "Noviflow"         | getNoviflowSwitches()
        "Noviflow(Wb5164)" | getNoviflowWb5164()
        "OVS"              | getVirtualSwitches()
    }

    @Tidy
    @Tags([TOPOLOGY_DEPENDENT])
    def "Switch validation is able to detect rule info into the 'missing' section on a #switchType switch"() {
        assumeTrue(switches as boolean, "Unable to find required switches in topology")

        setup: "Select a #switchType switch and retrieve default meters"
        def sw = switches.first()

        when: "Create a flow"
        def flow = flowHelperV2.addFlow(flowHelperV2.singleSwitchFlow(sw))
        def createdCookies = getCookiesWithMeter(sw.dpId)
        def createdHexCookies = createdCookies.collect { Long.toHexString(it) }

        and: "Delete created rules"
        northbound.deleteSwitchRules(sw.dpId, DeleteRulesAction.IGNORE_DEFAULTS)

        then: "Rule info is moved into the 'missing' section"
        def switchValidateInfo = northbound.validateSwitch(sw.dpId)
        switchValidateInfo.rules.missing.containsAll(createdCookies)
        switchValidateInfo.rules.missingHex.containsAll(createdHexCookies)

        and: "The rest fields in the 'rule' section are empty"
        switchValidateInfo.verifyRuleSectionsAreEmpty(["proper", "excess"])
        switchValidateInfo.verifyHexRuleSectionsAreEmpty(["properHex", "excessHex"])

        when: "Try to synchronize the switch"
        def syncResponse = northbound.synchronizeSwitch(sw.dpId, false)

        then: "System detects missing rules, then installs them"
        def amountOfFlowRules = switchHelper.getCachedSwProps(sw.dpId).multiTable ? 4 : 2
        syncResponse.rules.missing.size() == amountOfFlowRules
        syncResponse.rules.missing.containsAll(createdCookies)
        syncResponse.rules.installed.size() == amountOfFlowRules
        syncResponse.rules.installed.containsAll(createdCookies)

        when: "Delete the flow"
        def deleteFlow = flowHelperV2.deleteFlow(flow.flowId)

        then: "Check that the switch validate request returns empty sections"
        Wrappers.wait(WAIT_OFFSET) {
            def switchValidateInfoAfterDelete = northbound.validateSwitch(sw.dpId)
            switchValidateInfoAfterDelete.verifyRuleSectionsAreEmpty()
            switchValidateInfoAfterDelete.verifyMeterSectionsAreEmpty()
        }
        def testIsCompleted = true

        cleanup:
        flow && !deleteFlow && flowHelperV2.deleteFlow(flow.flowId)
        if (!testIsCompleted) {
            northbound.synchronizeSwitch(sw.dpId, true)
            Wrappers.wait(RULES_INSTALLATION_TIME) {
                assert northbound.getSwitchRules(sw.dpId).flowEntries*.cookie.sort() == sw.defaultCookies.sort()
            }
        }

        where:
        switchType         | switches
        "Centec"           | getCentecSwitches()
        "Noviflow"         | getNoviflowSwitches()
        "Noviflow(Wb5164)" | getNoviflowWb5164()
        "OVS"              | getVirtualSwitches()
    }

    @Tags([TOPOLOGY_DEPENDENT])
    def "Switch validation is able to detect rule/meter info into the 'excess' section on a #switchType switch"() {
        assumeTrue(switches as boolean, "Unable to find required switches in topology")

        setup: "Select a #switchType switch and no meters/rules exist on a switch"
        def sw = switches.first()
        def switchValidateInfoInitState = northbound.validateSwitch(sw.dpId)
        switchValidateInfoInitState.verifyRuleSectionsAreEmpty()
        switchValidateInfoInitState.verifyMeterSectionsAreEmpty()

        when: "Create excess rules/meter directly via kafka"
        Long fakeBandwidth = 333
        Long burstSize = switchHelper.getExpectedBurst(sw.dpId, fakeBandwidth)
        def producer = new KafkaProducer(producerProps)
        //pick a meter id which is not yet used on src switch
        def excessMeterId = ((MIN_FLOW_METER_ID..100) - northbound.getAllMeters(sw.dpId)
                .meterEntries*.meterId).first()
        producer.send(new ProducerRecord(speakerTopic, sw.dpId.toString(), buildMessage(
                FlowSpeakerData.builder()
                        .switchId(sw.dpId)
                        .ofVersion(OfVersion.of(sw.ofVersion))
                        .cookie(new Cookie(1L))
                        .table(OfTable.EGRESS)
                        .priority(100)
                        .instructions(Instructions.builder().build())
                        .build()).toJson())).get()
        producer.send(new ProducerRecord(speakerTopic, sw.dpId.toString(), buildMessage(
                FlowSpeakerData.builder()
                        .switchId(sw.dpId)
                        .ofVersion(OfVersion.of(sw.ofVersion))
                        .cookie(new Cookie(2L))
                        .table(OfTable.TRANSIT)
                        .priority(100)
                        .instructions(Instructions.builder().build())
                        .build()).toJson())).get()
        producer.send(new ProducerRecord(speakerTopic, sw.dpId.toString(), buildMessage([
                FlowSpeakerData.builder()
                        .switchId(sw.dpId)
                        .ofVersion(OfVersion.of(sw.ofVersion))
                        .cookie(new Cookie(3L))
                        .table(OfTable.INPUT)
                        .priority(100)
                        .instructions(Instructions.builder().build())
                        .build(),
                MeterSpeakerData.builder()
                        .switchId(sw.dpId)
                        .ofVersion(OfVersion.of(sw.ofVersion))
                        .meterId(new MeterId(excessMeterId))
                        .rate(fakeBandwidth)
                        .burst(burstSize)
                        .flags(Sets.newHashSet(MeterFlag.KBPS, MeterFlag.BURST, MeterFlag.STATS))
                        .build()]).toJson())).get()

        then: "System detects created rules/meter as excess rules"
        //excess egress/ingress/transit rules are not added yet
        //they will be added after the next line
        def switchValidateInfo
        Wrappers.wait(WAIT_OFFSET) {
            switchValidateInfo = northbound.validateSwitch(sw.dpId)
            //excess egress/ingress/transit rules are added
            switchValidateInfo.rules.excess.size() == 3
            switchValidateInfo.rules.excessHex.size() == 3
            switchValidateInfo.meters.excess.size() == 1
        }

        switchValidateInfo.meters.excess.each {
            verifyRateIsCorrect(sw, it.rate, fakeBandwidth)
            assert it.meterId == excessMeterId
            assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            switchHelper.verifyBurstSizeIsCorrect(sw, burstSize, it.burstSize)
        }

        when: "Try to synchronize the switch"
        def syncResponse = northbound.synchronizeSwitch(sw.dpId, true)

        then: "System detects excess rules and meters, then deletes them"
        syncResponse.rules.excess.size() == 3
        syncResponse.rules.excess.containsAll([1L, 2L, 3L])
        syncResponse.rules.removed.size() == 3
        syncResponse.rules.removed.containsAll([1L, 2L, 3L])
        syncResponse.meters.excess.size() == 1
        syncResponse.meters.removed.size() == 1
        syncResponse.meters.removed.meterId[0] == excessMeterId

        and: "Switch validation doesn't complain about excess rules and meters"
        Wrappers.wait(WAIT_OFFSET) {
            def switchValidateResponse = northbound.validateSwitch(sw.dpId)
            switchValidateResponse.verifyRuleSectionsAreEmpty()
            switchValidateResponse.verifyMeterSectionsAreEmpty()
        }

        cleanup:
        producer && producer.close()

        where:
        switchType         | switches
        "Centec"           | getCentecSwitches()
        "Noviflow"         | getNoviflowSwitches()
        "Noviflow(Wb5164)" | getNoviflowWb5164()
        "OVS"              | getVirtualSwitches()
    }

    @Tidy
    def "Able to get the switch validate info on a NOT supported switch"() {
        given: "Not supported switch"
        def sw = topology.activeSwitches.find { it.ofVersion == "OF_12" }
        assumeTrue(sw as boolean, "Unable to find required switches in topology")

        when: "Try to invoke the switch validate request"
        def response = northbound.validateSwitch(sw.dpId)

        then: "Response without meter section is returned"
        response.rules.proper.findAll { !new Cookie(it).serviceFlag }.empty
        response.rules.missing.empty
        response.rules.excess.empty
        !response.meters
    }

    @Tidy
    @Tags([TOPOLOGY_DEPENDENT])
    def "Able to validate and sync a #switchType switch having missing rules of single-port single-switch flow"() {
        assumeTrue(sw as boolean, "Unable to find $switchType switch in topology")
        given: "A single-port single-switch flow"
        def flow = flowHelperV2.addFlow(flowHelperV2.singleSwitchSinglePortFlow(sw))

        when: "Remove flow rules from the switch, so that they become missing"
        northbound.deleteSwitchRules(sw.dpId, DeleteRulesAction.IGNORE_DEFAULTS)

        then: "Switch validation shows missing rules"
        def amountOfFlowRules = switchHelper.getCachedSwProps(sw.dpId).multiTable ? 4 : 2
        northbound.validateSwitch(sw.dpId).rules.missing.size() == amountOfFlowRules
        northbound.validateSwitch(sw.dpId).rules.missingHex.size() == amountOfFlowRules

        when: "Synchronize switch"
        with(northbound.synchronizeSwitch(sw.dpId, false)) {
            it.rules.installed.size() == amountOfFlowRules
        }

        then: "Switch validation shows no discrepancies"
        with(northbound.validateSwitch(sw.dpId)) {
            it.verifyRuleSectionsAreEmpty(["missing", "excess"])
            it.verifyHexRuleSectionsAreEmpty(["missingHex", "excessHex"])
            it.rules.proper.findAll { !new Cookie(it).serviceFlag }.size() == amountOfFlowRules
            def properMeters = it.meters.proper.findAll({ dto -> !isDefaultMeter(dto) })
            properMeters.size() == 2
        }

        when: "Delete the flow"
        def deleteFlow = flowHelperV2.deleteFlow(flow.flowId)

        then: "Switch validation returns empty sections"
        with(northbound.validateSwitch(sw.dpId)) {
            it.verifyRuleSectionsAreEmpty()
            it.verifyMeterSectionsAreEmpty()
        }
        def testIsCompleted = true

        cleanup:
        flow && !deleteFlow && flowHelperV2.deleteFlow(flow.flowId)
        if (!testIsCompleted) {
            northbound.synchronizeSwitch(sw.dpId, true)
            Wrappers.wait(RULES_INSTALLATION_TIME) {
                assert northbound.getSwitchRules(sw.dpId).flowEntries*.cookie.sort() == sw.defaultCookies.sort()
            }
        }

        where:
        switchType         | sw
        "Centec"           | getCentecSwitches()[0]
        "Noviflow"         | getNoviflowSwitches()[0]
        "Noviflow(Wb5164)" | getNoviflowWb5164()[0]
        "OVS"              | getVirtualSwitches()[0]

    }

    @Memoized
    List<Switch> getNoviflowSwitches() {
        topology.activeSwitches.findAll { it.noviflow && it.ofVersion == "OF_13" && !it.wb5164 }
    }

    @Memoized
    List<Switch> getCentecSwitches() {
        topology.getActiveSwitches().findAll { it.centec }
    }

    @Memoized
    List<Switch> getNoviflowWb5164() {
        topology.getActiveSwitches().findAll { it.wb5164 }
    }

    @Memoized
    List<Switch> getVirtualSwitches() {
        topology.getActiveSwitches().findAll { it.virtual }
    }

    List<Integer> getCreatedMeterIds(SwitchId switchId) {
        return northbound.getAllMeters(switchId).meterEntries.findAll {
            it.meterId > MAX_SYSTEM_RULE_METER_ID
        }*.meterId
    }

    List<Long> getCookiesWithMeter(SwitchId switchId) {
        return northbound.getSwitchRules(switchId).flowEntries.findAll {
            !new Cookie(it.cookie).serviceFlag && it.instructions.goToMeter
        }*.cookie
    }

    void verifyRateIsCorrect(Switch sw, Long expected, Long actual) {
        if (sw.isWb5164()) {
            assert Math.abs(expected - actual) <= expected * 0.01
        } else {
            assert expected == actual
        }
    }
}
