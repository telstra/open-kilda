package org.openkilda.functionaltests.spec.switches

import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.helpers.model.SwitchExtended.isDefaultMeter
import static org.openkilda.functionaltests.helpers.model.SwitchExtended.verifyHexRuleSectionsAreEmpty
import static org.openkilda.functionaltests.helpers.model.SwitchExtended.verifyMeterSectionsAreEmpty
import static org.openkilda.functionaltests.helpers.model.SwitchExtended.verifyRuleSectionsAreEmpty
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.SYNCHRONIZE_SWITCH
import static org.openkilda.functionaltests.model.switches.Manufacturer.CENTEC
import static org.openkilda.functionaltests.model.switches.Manufacturer.NOVIFLOW
import static org.openkilda.functionaltests.model.switches.Manufacturer.OVS
import static org.openkilda.functionaltests.model.switches.Manufacturer.WB5164
import static org.openkilda.functionaltests.spec.switches.MetersSpec.NOT_OVS_REGEX
import static org.openkilda.model.MeterId.MIN_FLOW_METER_ID
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.tools.KafkaUtils.buildMessage

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.IterationTag
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowDirection
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.messaging.model.FlowDirectionType
import org.openkilda.model.MeterId
import org.openkilda.model.cookie.Cookie
import org.openkilda.rulemanager.FlowSpeakerData
import org.openkilda.rulemanager.Instructions
import org.openkilda.rulemanager.MeterFlag
import org.openkilda.rulemanager.MeterSpeakerData
import org.openkilda.rulemanager.OfTable
import org.openkilda.rulemanager.OfVersion

import com.google.common.collect.Sets
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import spock.lang.Narrative
import spock.lang.See
import spock.lang.Shared

@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/hub-and-spoke/switch-validate")
@Narrative("""This test suite checks the switch validate feature on a single flow switch.
Description of fields:
- missing - those meters/rules, which are NOT present on a switch, but are present in db
- misconfigured - those meters which have different value (on a switch and in db) for the same parameter
- excess - those meters/rules, which are present on a switch, but are NOT present in db
- proper - meters/rules values are the same on a switch and in db
""")
@Tags([SMOKE_SWITCHES, TOPOLOGY_DEPENDENT])
class SwitchValidationSingleSwFlowSpec extends HealthCheckSpecification {
    @Value("#{kafkaTopicsConfig.getSpeakerSwitchManagerTopic()}")
    String speakerTopic
    @Autowired
    @Qualifier("kafkaProducerProperties")
    Properties producerProps
    @Autowired
    @Shared
    CleanupManager cleanupManager
    @Autowired
    @Shared
    FlowFactory flowFactory

    def setupSpec() {
        deleteAnyFlowsLeftoversIssue5480()
    }

    @Tags([SMOKE])
    @IterationTag(tags = [HARDWARE], iterationNameRegex = NOT_OVS_REGEX)
    def "Switch validation is able to store correct information on a #switchType switch in the 'proper' section"() {
        setup: "Select a #switchType switch and retrieve default meters"
        def sw = switches.all().withManufacturer(switchType).first()

        when: "Create a flow"
        def flow = flowFactory.getSingleSwRandom(sw)
        def meterIds = sw.metersManager.getCreatedMeterIds()
        Long burstSize = sw.getExpectedBurst(flow.maximumBandwidth)

        then: "Two meters are automatically created."
        meterIds.size() == 2

        and: "The correct info is stored in the 'proper' section"
        def switchValidateInfo = sw.validateV1()
        switchValidateInfo.meters.proper.collect { it.meterId }.containsAll(meterIds)

        def createdCookies = sw.rulesManager.getRulesWithMeter().cookie
        switchValidateInfo.meters.proper*.cookie.containsAll(createdCookies)

        def properMeters = switchValidateInfo.meters.proper.findAll({ !isDefaultMeter(it) })
        properMeters.each {
            sw.verifyRateSizeIsCorrect(it.rate, flow.maximumBandwidth)
            assert it.flowId == flow.flowId
            assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            sw.verifyBurstSizeIsCorrect(burstSize, it.burstSize)
        }

        verifyMeterSectionsAreEmpty(switchValidateInfo, ["missing", "misconfigured", "excess"])

        and: "Created rules are stored in the 'proper' section"
        switchValidateInfo.rules.proper.containsAll(createdCookies)

        and: "The rest fields in the 'rule' section are empty"
        verifyRuleSectionsAreEmpty(switchValidateInfo, ["missing", "excess"])

        when: "Delete the flow"
        flow.delete()

        then: "Check that the switch validate request returns empty sections"
        Wrappers.wait(WAIT_OFFSET) {
            def switchValidateInfoAfterDelete = sw.validateV1()
            verifyRuleSectionsAreEmpty(switchValidateInfoAfterDelete)
            verifyMeterSectionsAreEmpty(switchValidateInfoAfterDelete)
        }

        where:
        switchType << [CENTEC, NOVIFLOW, WB5164, OVS]
    }

    @IterationTag(tags = [HARDWARE], iterationNameRegex = NOT_OVS_REGEX)
    def "Switch validation is able to detect meter info into the 'misconfigured' section on a #switchType switch"() {
        setup: "Select a #switchType switch and retrieve default meters"
        def sw = switches.all().withManufacturer(switchType).first()

        when: "Create a flow"
        def amountOfMultiTableFlRules = 4 //2 SHARED_OF_FLOW, 2 MULTI_TABLE_INGRESS_RULES
        def amountOfFlowRules = 2 //SERVICE_OR_FLOW_SEGMENT(ingress/egress)
        def amountOfSwRules = sw.rulesManager.getRules().size()
        def amountOfRules = amountOfSwRules + amountOfFlowRules + amountOfMultiTableFlRules
        def amountOfMeters = sw.metersManager.getMeters().size()
        def amountOfFlowMeters = 2
        def flow = flowFactory.getSingleSwBuilder(sw, false).withBandwidth(5000).build().create()
        def meterIds = sw.metersManager.getCreatedMeterIds()
        Long burstSize = sw.getExpectedBurst(flow.maximumBandwidth)

        and: "Change bandwidth for the created flow directly in DB"
        Long newBandwidth = flow.maximumBandwidth + 100

        cleanupManager.addAction(SYNCHRONIZE_SWITCH, {sw.synchronize()})
        /** at this point meters are set for given flow. Now update flow bandwidth directly via DB,
         it is done just for moving meters from the 'proper' section into the 'misconfigured'*/
        flow.updateFlowBandwidthInDB(newBandwidth)
        //at this point existing meters do not correspond with the flow

        then: "Meters info is moved into the 'misconfigured' section"
        def switchValidateInfo = sw.validateV1()
        def createdCookies = sw.rulesManager.getRulesWithMeter().cookie
        switchValidateInfo.meters.misconfigured.meterId.size() == 2
        switchValidateInfo.meters.misconfigured*.meterId.containsAll(meterIds)

        switchValidateInfo.meters.misconfigured*.cookie.containsAll(createdCookies)
        switchValidateInfo.meters.misconfigured.each {
            sw.verifyRateSizeIsCorrect(it.rate, flow.maximumBandwidth)
            assert it.flowId == flow.flowId
            assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            sw.verifyBurstSizeIsCorrect(burstSize, it.burstSize)
        }

        and: "Reason is specified why meters are misconfigured"
        switchValidateInfo.meters.misconfigured.each {
            sw.verifyRateSizeIsCorrect(it.actual.rate, flow.maximumBandwidth)
            assert it.expected.rate == newBandwidth
        }

        and: "The rest fields are empty"
        verifyMeterSectionsAreEmpty(switchValidateInfo, ["proper", "missing", "excess"])

        and: "Created rules are still stored in the 'proper' section"
        switchValidateInfo.rules.proper.containsAll(createdCookies)

        and: "Flow validation shows discrepancies"
        def flowValidateResponse = flow.validate()
        // check isServer42 only for src switch because it is single switch pair, src equal to dst
        def isSwitchServer42 = sw.isS42FlowRttEnabled()
        def expectedRulesCount = [
                flow.getFlowRulesCountBySwitch(FlowDirection.FORWARD, 1, isSwitchServer42),
                flow.getFlowRulesCountBySwitch(FlowDirection.REVERSE, 1, isSwitchServer42)]

        flowValidateResponse.eachWithIndex { direction, i ->
            assert direction.discrepancies.size() == 2

            def rate = direction.discrepancies[0]
            assert rate.field == "meterRate"
            assert rate.expectedValue == newBandwidth.toString()
            sw.verifyRateSizeIsCorrect(rate.actualValue.toLong(), flow.maximumBandwidth)

            def burst = direction.discrepancies[1]
            assert burst.field == "meterBurstSize"
            Long newBurstSize = sw.getExpectedBurst(newBandwidth)
            sw.verifyBurstSizeIsCorrect(newBurstSize, burst.expectedValue.toLong())
            sw.verifyBurstSizeIsCorrect(burstSize, burst.actualValue.toLong())

            assert direction.flowRulesTotal == ((FlowDirectionType.FORWARD.toString() == direction.direction) ?
                    expectedRulesCount[0] : expectedRulesCount[1])
            assert direction.switchRulesTotal == amountOfRules
            assert direction.flowMetersTotal == 1
            assert direction.switchMetersTotal == amountOfMeters + amountOfFlowMeters
        }

        when: "Reset meters for the flow"
        flow.resetMeters()

        then: "Misconfigured meters are reinstalled according to the new bandwidth and moved into the 'proper' section"
        with(sw.validateV1()) {
            it.meters.proper.findAll { it.meterId in meterIds }.each { assert it.rate == newBandwidth }
            verifyMeterSectionsAreEmpty(it, ["missing", "misconfigured", "excess"])
        }

        and: "Flow validation shows no discrepancies"
        flow.validateAndCollectDiscrepancies().isEmpty()

        when: "Delete the flow"
        flow.delete()

        then: "Check that the switch validate request returns empty sections"
        Wrappers.wait(WAIT_OFFSET) {
            def switchValidateInfoAfterDelete = sw.validateV1()
            verifyRuleSectionsAreEmpty(switchValidateInfoAfterDelete)
            verifyMeterSectionsAreEmpty(switchValidateInfoAfterDelete)
        }

        where:
        switchType << [CENTEC, NOVIFLOW, WB5164, OVS]
    }

    @IterationTag(tags = [HARDWARE], iterationNameRegex = NOT_OVS_REGEX)
    def "Switch validation is able to detect meter info into the 'missing' section on a #switchType switch"() {
        setup: "Select a #switchType switch and retrieve default meters"
        def sw = switches.all().withManufacturer(switchType).first()

        when: "Create a flow"
        def flow = flowFactory.getSingleSwRandom(sw)
        def meterIds = sw.metersManager.getCreatedMeterIds()
        assert meterIds.size() == 2
        def createdCookies = sw.rulesManager.getRulesWithMeter().cookie

        and: "Remove created meter"
        cleanupManager.addAction(SYNCHRONIZE_SWITCH, {sw.synchronize()})
        meterIds.each { sw.metersManager.delete(it) }

        then: "Meters info/rules are moved into the 'missing' section"
        Long burstSize = sw.getExpectedBurst(flow.maximumBandwidth)
        def switchValidateInfo = sw.validateV1()
        switchValidateInfo.meters.missing.meterId.size() == meterIds.size()
        switchValidateInfo.rules.missing.containsAll(createdCookies)
        switchValidateInfo.meters.missing*.meterId.containsAll(meterIds)
        switchValidateInfo.meters.missing*.cookie.containsAll(createdCookies)
        switchValidateInfo.meters.missing.each {
            sw.verifyRateSizeIsCorrect(it.rate, flow.maximumBandwidth)
            assert it.flowId == flow.flowId
            assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            sw.verifyBurstSizeIsCorrect(burstSize, it.burstSize)
        }

        and: "The rest fields are empty"
        verifyRuleSectionsAreEmpty(switchValidateInfo, ["proper", "excess"])
        verifyMeterSectionsAreEmpty(switchValidateInfo, ["proper", "misconfigured", "excess"])

        when: "Try to synchronize the switch"
        def syncResponse = sw.synchronize(false)

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
        flow.delete()

        then: "Check that the switch validate request returns empty sections"
        Wrappers.wait(WAIT_OFFSET) {
            def switchValidateInfoAfterDelete = sw.validateV1()
            verifyRuleSectionsAreEmpty(switchValidateInfoAfterDelete)
            verifyMeterSectionsAreEmpty(switchValidateInfoAfterDelete)
        }

        where:
        switchType << [CENTEC, NOVIFLOW, WB5164, OVS]
    }

    @IterationTag(tags = [HARDWARE], iterationNameRegex = NOT_OVS_REGEX)
    def "Switch validation is able to detect meter info into the 'excess' section on a #switchType switch"() {
        setup: "Select a #switchType switch and retrieve default meters"
        def sw = switches.all().withManufacturer(switchType).first()

        when: "Create a flow"
        // No TraffGens because the Single switch flow is created at the same port, and no traffic is checked
        def flow = flowFactory.getSingleSwRandom(sw, false)
        def metersIds = sw.metersManager.getCreatedMeterIds()
        Long burstSize = sw.getExpectedBurst(flow.maximumBandwidth)

        then: "Rules and meters are created"
        def swValidateInfo = sw.validateV1()
        def properMeters = swValidateInfo.meters.proper.findAll({ !isDefaultMeter(it) })
        def amountOfFlowRules = 4
        properMeters.meterId.size() == 2
        swValidateInfo.rules.proper.findAll { !new Cookie(it).serviceFlag }.size() == amountOfFlowRules

        when: "Update meterId for created flow directly via db"
        long newMeterId = 100
        cleanupManager.addAction(SYNCHRONIZE_SWITCH, {sw.synchronize()})
        flow.updateFlowMeterIdInDB(newMeterId)

        then: "Origin meters are moved into the 'excess' section"
        def switchValidateInfo = sw.validateV1()
        switchValidateInfo.meters.excess.meterId.size() == 2
        switchValidateInfo.meters.excess.collect { it.meterId }.containsAll(metersIds)
        switchValidateInfo.meters.excess.each {
            sw.verifyRateSizeIsCorrect(it.rate, flow.maximumBandwidth)
            assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            sw.verifyBurstSizeIsCorrect(burstSize, it.burstSize)
        }

        and: "Updated meters are stored in the 'missing' section"
        def createdCookies = sw.rulesManager.getRulesWithMeter().cookie
        switchValidateInfo.meters.missing.collect { it.cookie }.containsAll(createdCookies)
        switchValidateInfo.meters.missing.each {
            sw.verifyRateSizeIsCorrect(it.rate, flow.maximumBandwidth)
            assert it.flowId == flow.flowId
            assert it.meterId == newMeterId || it.meterId == newMeterId + 1
            assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            sw.verifyBurstSizeIsCorrect(burstSize, it.burstSize)
        }

        and: "Rules still exist in the 'proper' section"
        verifyRuleSectionsAreEmpty(switchValidateInfo, ["missing", "excess"])

        when: "Delete the flow"
        flow.delete()

        and: "Delete excess meters"
        metersIds.each { sw.metersManager.delete(it) }

        then: "Check that the switch validate request returns empty sections"
        Wrappers.wait(WAIT_OFFSET) {
            def switchValidateInfoAfterDelete = sw.validateV1()
            verifyRuleSectionsAreEmpty(switchValidateInfoAfterDelete)
            verifyMeterSectionsAreEmpty(switchValidateInfoAfterDelete)
        }

        where:
        switchType << [CENTEC, NOVIFLOW, WB5164, OVS]
    }

    @IterationTag(tags = [HARDWARE], iterationNameRegex = NOT_OVS_REGEX)
    def "Switch validation is able to detect rule info into the 'missing' section on a #switchType switch"() {
        setup: "Select a #switchType switch and retrieve default meters"
        def sw = switches.all().withManufacturer(switchType).first()

        when: "Create a flow"
        def flow = flowFactory.getSingleSwRandom(sw)
        def createdCookies = sw.rulesManager.getRulesWithMeter().cookie
        def createdHexCookies = createdCookies.collect { Long.toHexString(it) }

        and: "Delete created rules"
        sw.rulesManager.delete(DeleteRulesAction.IGNORE_DEFAULTS)

        then: "Rule info is moved into the 'missing' section"
        def switchValidateInfo = sw.validateV1()
        switchValidateInfo.rules.missing.containsAll(createdCookies)
        switchValidateInfo.rules.missingHex.containsAll(createdHexCookies)

        and: "The rest fields in the 'rule' section are empty"
        verifyRuleSectionsAreEmpty(switchValidateInfo, ["proper", "excess"])
        verifyHexRuleSectionsAreEmpty(switchValidateInfo, ["properHex", "excessHex"])

        when: "Try to synchronize the switch"
        def syncResponse = sw.synchronize(false)

        then: "System detects missing rules, then installs them"
        def amountOfFlowRules = 4
        syncResponse.rules.missing.size() == amountOfFlowRules
        syncResponse.rules.missing.containsAll(createdCookies)
        syncResponse.rules.installed.size() == amountOfFlowRules
        syncResponse.rules.installed.containsAll(createdCookies)

        when: "Delete the flow"
        flow.delete()

        then: "Check that the switch validate request returns empty sections"
        Wrappers.wait(WAIT_OFFSET) {
            def switchValidateInfoAfterDelete = sw.validateV1()
            verifyRuleSectionsAreEmpty(switchValidateInfoAfterDelete)
            verifyMeterSectionsAreEmpty(switchValidateInfoAfterDelete)
        }

        where:
        switchType << [CENTEC, NOVIFLOW, WB5164, OVS]
    }

    @IterationTag(tags = [HARDWARE], iterationNameRegex = NOT_OVS_REGEX)
    def "Switch validation is able to detect rule/meter info into the 'excess' section on a #switchType switch"() {
        setup: "Select a #switchType switch and no meters/rules exist on a switch"
        def sw = switches.all().withManufacturer(switchType).first()
        def switchValidateInfoInitState = sw.validateV1()
        verifyRuleSectionsAreEmpty(switchValidateInfoInitState)
        verifyMeterSectionsAreEmpty(switchValidateInfoInitState)

        when: "Create excess rules/meter directly via kafka"
        Long fakeBandwidth = 333
        Long burstSize = sw.getExpectedBurst(fakeBandwidth)
        def producer = new KafkaProducer(producerProps)
        //pick a meter id which is not yet used on src switch
        def excessMeterId = ((MIN_FLOW_METER_ID..100) - sw.metersManager.getMeters().meterId).first()
        cleanupManager.addAction(SYNCHRONIZE_SWITCH, {sw.synchronize()})
        producer.send(new ProducerRecord(speakerTopic, sw.switchId.toString(), buildMessage(
                FlowSpeakerData.builder()
                        .switchId(sw.switchId)
                        .ofVersion(OfVersion.of(sw.ofVersion))
                        .cookie(new Cookie(1L))
                        .table(OfTable.EGRESS)
                        .priority(100)
                        .instructions(Instructions.builder().build())
                        .build()).toJson())).get()
        producer.send(new ProducerRecord(speakerTopic, sw.switchId.toString(), buildMessage(
                FlowSpeakerData.builder()
                        .switchId(sw.switchId)
                        .ofVersion(OfVersion.of(sw.ofVersion))
                        .cookie(new Cookie(2L))
                        .table(OfTable.TRANSIT)
                        .priority(100)
                        .instructions(Instructions.builder().build())
                        .build()).toJson())).get()
        producer.send(new ProducerRecord(speakerTopic, sw.switchId.toString(), buildMessage([
                FlowSpeakerData.builder()
                        .switchId(sw.switchId)
                        .ofVersion(OfVersion.of(sw.ofVersion))
                        .cookie(new Cookie(3L))
                        .table(OfTable.INPUT)
                        .priority(100)
                        .instructions(Instructions.builder().build())
                        .build(),
                MeterSpeakerData.builder()
                        .switchId(sw.switchId)
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
            switchValidateInfo = sw.validateV1()
            //excess egress/ingress/transit rules are added
            assert switchValidateInfo.rules.excess.size() == 3
            assert switchValidateInfo.rules.excessHex.size() == 3
            assert switchValidateInfo.meters.excess.size() == 1
        }

        switchValidateInfo.meters.excess.each {
            sw.verifyRateSizeIsCorrect(it.rate, fakeBandwidth)
            assert it.meterId == excessMeterId
            assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            sw.verifyBurstSizeIsCorrect(burstSize, it.burstSize)
        }

        when: "Try to synchronize the switch"
        def syncResponse = sw.synchronize(true)

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
            def switchValidateResponse = sw.validateV1()
            verifyRuleSectionsAreEmpty(switchValidateResponse)
            verifyMeterSectionsAreEmpty(switchValidateResponse)
        }

        cleanup:
        producer && producer.close()

        where:
        switchType << [CENTEC, NOVIFLOW, WB5164, OVS]
    }

    @IterationTag(tags = [HARDWARE], iterationNameRegex = NOT_OVS_REGEX)
    def "Able to validate and sync a #switchType switch having missing rules of single-port single-switch flow"() {
        given: "A single-port single-switch flow"
        // No TraffGens because the Single switch flow is created at the same port, and no traffic is checked
        def sw = switches.all().withManufacturer(switchType).first()
        def flow  = flowFactory.getSingleSwRandom(sw,false)

        when: "Remove flow rules from the switch, so that they become missing"
        sw.rulesManager.delete(DeleteRulesAction.IGNORE_DEFAULTS)

        then: "Switch validation shows missing rules"
        def amountOfFlowRules = 4
        sw.validateV1().rules.missing.size() == amountOfFlowRules
        sw.validateV1().rules.missingHex.size() == amountOfFlowRules

        when: "Synchronize switch"
        with(sw.synchronize(false)) {
            it.rules.installed.size() == amountOfFlowRules
        }

        then: "Switch validation shows no discrepancies"
        with(sw.validateV1()) {
            verifyRuleSectionsAreEmpty(it, ["missing", "excess"])
            verifyHexRuleSectionsAreEmpty(it, ["missingHex", "excessHex"])
            it.rules.proper.findAll { !new Cookie(it).serviceFlag }.size() == amountOfFlowRules
            def properMeters = it.meters.proper.findAll({ dto -> !isDefaultMeter(dto) })
            properMeters.size() == 2
        }

        when: "Delete the flow"
        flow.delete()

        then: "Switch validation returns empty sections"
        with(sw.validateV1()) {
            verifyRuleSectionsAreEmpty(it)
            verifyMeterSectionsAreEmpty(it)
        }

        where:
        switchType << [CENTEC, NOVIFLOW, WB5164, OVS]
    }

}
