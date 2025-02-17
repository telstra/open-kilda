package org.openkilda.functionaltests.spec.switches

import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.extension.tags.Tag.VIRTUAL
import static org.openkilda.functionaltests.helpers.SwitchHelper.isDefaultMeter
import static org.openkilda.functionaltests.helpers.model.Switches.validateAndCollectFoundDiscrepancies
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.SYNCHRONIZE_SWITCH
import static org.openkilda.model.MeterId.MIN_FLOW_METER_ID
import static org.openkilda.model.cookie.Cookie.VERIFICATION_BROADCAST_RULE_COOKIE
import static org.openkilda.rulemanager.OfTable.EGRESS
import static org.openkilda.rulemanager.OfTable.INPUT
import static org.openkilda.rulemanager.OfTable.TRANSIT
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.tools.KafkaUtils.buildMessage

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowExtended
import org.openkilda.functionaltests.helpers.model.SwitchExtended
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.functionaltests.helpers.model.FlowEncapsulationType
import org.openkilda.model.MeterId
import org.openkilda.model.SwitchId
import org.openkilda.model.cookie.Cookie
import org.openkilda.rulemanager.FlowSpeakerData
import org.openkilda.rulemanager.Instructions
import org.openkilda.rulemanager.MeterFlag
import org.openkilda.rulemanager.MeterSpeakerData
import org.openkilda.rulemanager.OfVersion

import com.google.common.collect.Sets
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import spock.lang.Issue
import spock.lang.See
import spock.lang.Shared


@See(["https://github.com/telstra/open-kilda/tree/develop/docs/design/hub-and-spoke/switch-sync",
        "https://github.com/telstra/open-kilda/blob/develop/docs/design/network-discovery/switch-FSM.png"])
@Tags([SMOKE_SWITCHES])
class SwitchSyncSpec extends HealthCheckSpecification {

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

    def "Able to synchronize switch without any rule and meter discrepancies (removeExcess=#removeExcess)"() {
        given: "An active switch"
        def sw = switches.all().first()

        when: "Synchronize the switch that doesn't have any rule and meter discrepancies"
        def syncResult = sw.synchronize(removeExcess)

        then: "Operation is successful"
        syncResult.rules.proper.findAll { !new Cookie(it).serviceFlag }.size() == 0
        syncResult.rules.excess.size() == 0
        syncResult.rules.missing.size() == 0
        syncResult.rules.removed.size() == 0
        syncResult.rules.installed.size() == 0

        def properMeters = syncResult.meters.proper.findAll({ !isDefaultMeter(it) })
        properMeters.size() == 0
        syncResult.meters.excess.size() == 0
        syncResult.meters.missing.size() == 0
        syncResult.meters.removed.size() == 0
        syncResult.meters.installed.size() == 0

        where:
        removeExcess << [false, true]
    }

    @Issue("Noviflow WB5164 Only: https://github.com/telstra/open-kilda/issues/5638")
    def "Able to synchronize switch (install missing rules and meters)"() {
        given: "Two active not neighboring switches"
        def switchPair = switchPairs.all().nonNeighbouring().random()

        and: "Create an intermediate-switch flow"
        def flow = flowFactory.getRandom(switchPair)

        and: "Drop all rules an meters from related switches (both default and non-default)"
        List<SwitchExtended> involvedSwitches =  switches.all().findSwitchesInPath(flow.retrieveAllEntityPaths())

        def cookiesMap = involvedSwitches.collectEntries { sw ->
            def defaultCookies = sw.collectDefaultCookies()
            [sw.switchId, sw.rulesManager.getRules().findAll { !(it.cookie in defaultCookies) }*.cookie]
        }
        Map<SwitchId, List<Long>> metersMap = involvedSwitches.collectEntries { sw ->
            [sw.switchId, sw.metersManager.getMeters()*.meterId]
        }

        involvedSwitches.each { sw ->
            sw.rulesManager.delete(DeleteRulesAction.DROP_ALL)
            metersMap[sw.switchId].each { sw.metersManager.delete(it) }
        }

        Wrappers.wait(RULES_DELETION_TIME) {
            involvedSwitches.each { sw ->
                def validationResponse = sw.validate()
                if (sw.switchId in switchPair.toList().dpId) {
                    /**
                     * s42Rules: SERVER_42_FLOW_RTT_INGRESS_REVERSE, SERVER_42_FLOW_RTT_INPUT, SERVER_42_FLOW_RTT_TURNING_COOKIE,
                     * SERVER42_QINQ_OUTER_VLAN (for QinQ flows, should not present in this test)
                     * some rule is in defaultCookies (SERVER_42_FLOW_RTT_OUTPUT_VLAN_COOKIE)
                     */
                    def defaultS42Rules = sw.getProps().server42FlowRtt ? 1 : 0 // DROP_ALL rules were called
                    def amountRules = sw.collectFlowRelatedRulesAmount(flow) + sw.collectDefaultCookies().size() + defaultS42Rules

                    assert validationResponse.rules.missing.size() == amountRules
                    assert validationResponse.meters.missing.size() == 1 + sw.collectDefaultMeters().size()

                } else {
                    assert validationResponse.rules.missing.size() == 2 + sw.collectDefaultCookies().size()
                    assert validationResponse.meters.missing.meterId.sort() == sw.collectDefaultMeters().sort()
                }
            }
        }

        when: "Try to synchronize all switches"
        then: "System detects missing rules and meters (both default and flow-related), then installs them"
        involvedSwitches.each { sw ->
            def syncResponse = sw.synchronize()
            assert syncResponse.rules.proper.size() == 0
            assert syncResponse.rules.excess.size() == 0
            assert syncResponse.rules.missing.containsAll(cookiesMap[sw.switchId])
            assert syncResponse.rules.removed.size() == 0
            assert syncResponse.rules.installed.containsAll(cookiesMap[sw.switchId])

            if (sw.switchId in switchPair.toList().dpId) {
                assert syncResponse.meters.proper.size() == 0
                assert syncResponse.meters.excess.size() == 0
                assert syncResponse.meters.missing*.meterId == metersMap[sw.switchId].sort()
                assert syncResponse.meters.removed.size() == 0
                assert syncResponse.meters.installed*.meterId == metersMap[sw.switchId].sort()
            }
        }

        and: "Switch validation doesn't complain about any missing rules and meters"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
           assert validateAndCollectFoundDiscrepancies(involvedSwitches).isEmpty()
        }
    }

    def "Able to synchronize #switchKind switch (delete excess rules and meters)"() {
        given: "Flow with intermediate switches"
        def switchPair = switchPairs.all().nonNeighbouring().random()
        def flow = flowFactory.getRandom(switchPair)
        def sw = switches.all().findSpecific(getSwitchId(flow) as SwitchId)
        def ofVersion = sw.getOfVersion()
        assert !sw.synchronizeAndCollectFixedDiscrepancies().isPresent()

        and: "Force install excess rules and meters on switch"
        cleanupManager.addAction(SYNCHRONIZE_SWITCH, {sw.synchronize()})
        def producer = new KafkaProducer(producerProps)
        def excessRuleCookie = 1234567890L
        def excessMeterId = ((MIN_FLOW_METER_ID..100) - sw.metersManager.getMeters().meterId).first() as Long
        producer.send(new ProducerRecord(speakerTopic, sw.switchId.toString(), buildMessage([
                FlowSpeakerData.builder()
                        .switchId(sw.switchId)
                        .ofVersion(OfVersion.of(ofVersion))
                        .cookie(new Cookie(excessRuleCookie))
                        .table(table)
                        .priority(100)
                        .instructions(Instructions.builder().build())
                        .build(),
                MeterSpeakerData.builder()
                        .switchId(sw.switchId)
                        .ofVersion(OfVersion.of(ofVersion))
                        .meterId(new MeterId(excessMeterId))
                        .rate(flow.maximumBandwidth)
                        .burst(flow.maximumBandwidth)
                        .flags(Sets.newHashSet(MeterFlag.KBPS, MeterFlag.BURST, MeterFlag.STATS))
                        .build()]).toJson())).get()

        Wrappers.wait(RULES_INSTALLATION_TIME) { !sw.validate().getRules().getExcess().isEmpty() }

        when: "Try to synchronize the switch"
        def syncResult = sw.synchronize()

        then: "System detects excess rules and meters"
        verifyAll (syncResult) {
            rules.excess == [excessRuleCookie]
            rules.removed == [excessRuleCookie]
            meters.excess*.meterId == [excessMeterId]
            meters.removed*.meterId == [excessMeterId]
        }

        and: "Switch validation doesn't complain about excess rules and meters"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            sw.validate().isAsExpected()
        }

        where:
        switchKind    | getSwitchId                                                            | table
        "source"      | { FlowExtended flowExtended -> flowExtended.source.switchId }          | INPUT
        "destination" | { FlowExtended flowExtended -> flowExtended.destination.switchId }     | EGRESS
        "transit"     | { FlowExtended flowExtended ->
            return flowExtended.retrieveAllEntityPaths()
                    .flowPath.path.forward.getTransitInvolvedSwitches().shuffled().first()
        }                                                                                      | TRANSIT
    }

    def "Able to synchronize switch with 'vxlan' rule(install missing rules and meters)"() {
        given: "Two active not neighboring switches which support VXLAN encapsulation"
        def switchPair = switchPairs.all().nonNeighbouring().withBothSwitchesVxLanEnabled().random()

        and: "Create a flow with vxlan encapsulation"
        def flow = flowFactory.getBuilder(switchPair)
                .withEncapsulationType(FlowEncapsulationType.VXLAN)
                .build().create()

        and: "Reproduce situation when switches have missing rules and meters"
        def flowInfoFromDb = flow.retrieveDetailsFromDB()
        List<SwitchExtended> involvedSwitches = switches.all().findSwitchesInPath(flow.retrieveAllEntityPaths())

        def transitSwitches = involvedSwitches.findAll { !(it.switchId in switchPair.toList().dpId) }
        def terminalSwitches = involvedSwitches.findAll { it.switchId in switchPair.toList().dpId }

        def cookiesMap = involvedSwitches.collectEntries { sw ->
            def defaultCookies = sw.collectDefaultCookies()
            [sw.switchId, sw.rulesManager.getRules().findAll {
                !(it.cookie in defaultCookies) && !new Cookie(it.cookie).serviceFlag
            }*.cookie]
        }
        def metersMap = involvedSwitches.collectEntries { sw ->
            [sw.switchId, sw.metersManager.getCreatedMeterIds()]
        }

        involvedSwitches.each { sw ->  sw.rulesManager.delete(DeleteRulesAction.IGNORE_DEFAULTS) }
        terminalSwitches.each { sw -> sw.metersManager.delete(metersMap[sw.switchId][0]) }

        Wrappers.wait(RULES_DELETION_TIME) {
            involvedSwitches.each { sw ->
                def validationResponse = sw.validate()
                def rulesCount = sw.collectFlowRelatedRulesAmount(flow)
                assert validationResponse.rules.missing.size() == rulesCount
                if(sw in terminalSwitches) {
                    assert validationResponse.meters.missing.size() == 1
                }
            }
        }

        when: "Try to synchronize all switches"
        then: "System detects missing rules and meters, then installs them"
        involvedSwitches.each { sw ->
            def syncResponse = sw.synchronize()
            assert syncResponse.rules.proper.findAll { !new Cookie(it).serviceFlag }.size() == 0
            assert syncResponse.rules.excess.size() == 0
            assert syncResponse.rules.missing.containsAll(cookiesMap[sw.switchId])
            assert syncResponse.rules.removed.size() == 0
            assert syncResponse.rules.installed.containsAll(cookiesMap[sw.switchId])
            if(sw in terminalSwitches) {
                assert syncResponse.meters.proper.findAll { !it.defaultMeter }.size() == 0
                assert syncResponse.meters.excess.size() == 0
                assert syncResponse.meters.missing*.meterId == metersMap[sw.switchId]
                assert syncResponse.meters.removed.size() == 0
                assert syncResponse.meters.installed*.meterId == metersMap[sw.switchId]
            }
        }

        and: "Switch validation doesn't complain about missing rules and meters"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            assert validateAndCollectFoundDiscrepancies(involvedSwitches).isEmpty()

        }

        and: "Rules are synced correctly"
        // ingressRule should contain "pushVxlan"
        // egressRule should contain "tunnel-id"
        with(terminalSwitches.find { it.switchId == switchPair.src.dpId}.rulesManager.getRules()) { rules ->
            assert rules.find {
                it.cookie == flowInfoFromDb.forwardPath.cookie.value
            }.instructions.applyActions.pushVxlan
            assert rules.find {
                it.cookie == flowInfoFromDb.reversePath.cookie.value
            }.match.tunnelId
        }

        with(terminalSwitches.find { it.switchId == switchPair.dst.dpId}.rulesManager.getRules()) { rules ->
            assert rules.find {
                it.cookie == flowInfoFromDb.forwardPath.cookie.value
            }.match.tunnelId
            assert rules.find {
                it.cookie == flowInfoFromDb.reversePath.cookie.value
            }.instructions.applyActions.pushVxlan
        }

        transitSwitches.each { sw ->
            with(sw.rulesManager.getRules()) { rules ->
                assert rules.find {
                    it.cookie == flowInfoFromDb.forwardPath.cookie.value
                }.match.tunnelId
                assert rules.find {
                    it.cookie == flowInfoFromDb.reversePath.cookie.value
                }.match.tunnelId
            }
        }
    }

    @Tags([VIRTUAL, LOW_PRIORITY])
    def "Able to synchronize misconfigured default meter"() {
        given: "An active switch with valid default rules and one misconfigured default meter"
        def sw = switches.all().random()
        def broadcastCookieMeterId = MeterId.createMeterIdForDefaultRule(VERIFICATION_BROADCAST_RULE_COOKIE).getValue()
        def meterToManipulate = sw.metersManager.getMeters().find{ it.meterId == broadcastCookieMeterId }
        def newBurstSize = meterToManipulate.burstSize + 100
        def newRate = meterToManipulate.rate + 100

        sw.updateBurstSizeAndRate(meterToManipulate.meterId, newBurstSize, newRate)
        Wrappers.wait(RULES_DELETION_TIME) {
            def validateInfo = sw.validate()
            assert validateInfo.rules.missing.empty
            assert validateInfo.rules.misconfigured.empty
            assert validateInfo.meters.misconfigured.size() == 1
            assert validateInfo.meters.misconfigured[0].discrepancies.burstSize == newBurstSize
            assert validateInfo.meters.misconfigured[0].expected.burstSize == meterToManipulate.burstSize
            assert validateInfo.meters.misconfigured[0].discrepancies.rate == newRate
            assert validateInfo.meters.misconfigured[0].expected.rate == meterToManipulate.rate
        }

        when: "Synchronize switch"
        sw.synchronize(false)

        then: "The misconfigured meter is fixed and moved to the 'proper' section"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            with(sw.validate()) {
                it.meters.misconfigured.empty
                it.meters.proper.find { it.meterId == broadcastCookieMeterId }.burstSize == meterToManipulate.burstSize
                it.meters.proper.find { it.meterId == broadcastCookieMeterId }.rate == meterToManipulate.rate
            }
        }
    }

    @Tags([VIRTUAL, SMOKE])
    def "Able to synchronize misconfigured flow meter"() {
        given: "An active switch with flow on it"
        def sw = switches.all().random()
        def flow = flowFactory.getRandom(sw, sw)

        when: "Update flow's meter"
        def flowMeterIdToManipulate = sw.metersManager.getMeters().find { it.meterId >= MIN_FLOW_METER_ID }
        def newBurstSize = flowMeterIdToManipulate.burstSize + 100
        def newRate = flowMeterIdToManipulate.rate + 100
        sw.updateBurstSizeAndRate(flowMeterIdToManipulate.meterId, newBurstSize, newRate)

        then: "Flow is not valid"
        def flowDiscrepancies = flow.validateAndCollectDiscrepancies().values()
        assert flowDiscrepancies.size() == 1
        def meterRateDiscrepancies = flowDiscrepancies[0].find { it.field.toString() == "meterRate" }
        def meterBurstSizeDiscrepancies = flowDiscrepancies[0].find { it.field.toString() == "meterBurstSize" }
        meterRateDiscrepancies.actualValue == newRate.toString()
        meterRateDiscrepancies.expectedValue == flowMeterIdToManipulate.rate.toString()
        meterBurstSizeDiscrepancies.actualValue == newBurstSize.toString()
        meterBurstSizeDiscrepancies.expectedValue == flowMeterIdToManipulate.burstSize.toString()

        and: "Validate switch endpoint shows the updated meter as misconfigured"
        Wrappers.wait(RULES_DELETION_TIME) {
            def validateInfo = sw.validate()
            assert validateInfo.meters.misconfigured.size() == 1
            assert validateInfo.meters.misconfigured[0].discrepancies.burstSize == newBurstSize
            assert validateInfo.meters.misconfigured[0].expected.burstSize == flowMeterIdToManipulate.burstSize
            assert validateInfo.meters.misconfigured[0].discrepancies.rate == newRate
            assert validateInfo.meters.misconfigured[0].expected.rate == flowMeterIdToManipulate.rate
        }

        when: "Synchronize switch"
        sw.synchronize(false)

        then: "The misconfigured meter was fixed and moved to the 'proper' section"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            with(sw.validate()) {
                it.meters.misconfigured.empty
                it.meters.proper.find {
                    it.meterId == flowMeterIdToManipulate.meterId
                }.burstSize == flowMeterIdToManipulate.burstSize
                it.meters.proper.find {
                    it.meterId == flowMeterIdToManipulate.meterId
                }.rate == flowMeterIdToManipulate.rate
            }
        }

        and: "Flow is valid"
        flow.validateAndCollectDiscrepancies().isEmpty()
    }
}
