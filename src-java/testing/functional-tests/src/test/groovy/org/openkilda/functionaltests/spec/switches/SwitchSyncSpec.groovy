package org.openkilda.functionaltests.spec.switches

import com.google.common.collect.Sets
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.model.FlowEncapsulationType
import org.openkilda.model.MeterId
import org.openkilda.model.SwitchId
import org.openkilda.model.cookie.Cookie
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2
import org.openkilda.rulemanager.FlowSpeakerData
import org.openkilda.rulemanager.Instructions
import org.openkilda.rulemanager.MeterFlag
import org.openkilda.rulemanager.MeterSpeakerData
import org.openkilda.rulemanager.OfVersion
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import spock.lang.See
import spock.lang.Shared

import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.extension.tags.Tag.VIRTUAL
import static org.openkilda.functionaltests.helpers.SwitchHelper.isDefaultMeter
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.SYNCHRONIZE_SWITCH
import static org.openkilda.model.MeterId.MAX_SYSTEM_RULE_METER_ID
import static org.openkilda.model.MeterId.MIN_FLOW_METER_ID
import static org.openkilda.model.cookie.Cookie.VERIFICATION_BROADCAST_RULE_COOKIE
import static org.openkilda.rulemanager.OfTable.EGRESS
import static org.openkilda.rulemanager.OfTable.INPUT
import static org.openkilda.rulemanager.OfTable.TRANSIT
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.tools.KafkaUtils.buildMessage

@See(["https://github.com/telstra/open-kilda/tree/develop/docs/design/hub-and-spoke/switch-sync",
        "https://github.com/telstra/open-kilda/blob/develop/docs/design/network-discovery/switch-FSM.png"])
@Tags([SMOKE_SWITCHES])
class SwitchSyncSpec extends HealthCheckSpecification {

    @Value("#{kafkaTopicsConfig.getSpeakerSwitchManagerTopic()}")
    String speakerTopic

    @Autowired
    @Qualifier("kafkaProducerProperties")
    Properties producerProps
    @Autowired @Shared
    CleanupManager cleanupManager

    def "Able to synchronize switch without any rule and meter discrepancies (removeExcess=#removeExcess)"() {
        given: "An active switch"
        def sw = topology.activeSwitches.first()

        when: "Synchronize the switch that doesn't have any rule and meter discrepancies"
        def syncResult = switchHelper.synchronize(sw.dpId, removeExcess)

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

    def "Able to synchronize switch (install missing rules and meters)"() {
        given: "Two active not neighboring switches"
        def switchPair = switchPairs.all().nonNeighbouring().random()

        and: "Create an intermediate-switch flow"
        def flow = flowHelperV2.randomFlow(switchPair)
        flowHelperV2.addFlow(flow)

        and: "Drop all rules an meters from related switches (both default and non-default)"
        def involvedSwitches = pathHelper.getInvolvedSwitches(flow.flowId)
        def involvedSwitchIds = involvedSwitches*.getDpId()
        def cookiesMap = involvedSwitches.collectEntries { sw ->
            [sw.dpId, northbound.getSwitchRules(sw.dpId).flowEntries.findAll {
                !(it.cookie in sw.defaultCookies)
            }*.cookie]
        }
        Map<SwitchId, List<Long>> metersMap = involvedSwitches.collectEntries { sw ->
            [sw.dpId, northbound.getAllMeters(sw.dpId).meterEntries*.meterId]
        }

        involvedSwitches.each { sw ->
            switchHelper.deleteSwitchRules(sw.dpId, DeleteRulesAction.DROP_ALL)
            northbound.getAllMeters(sw.dpId).meterEntries.each { northbound.deleteMeter(sw.dpId, it.meterId) }
        }
        Wrappers.wait(RULES_DELETION_TIME) {
            def validationResultsMap = switchHelper.validateAndCollectFoundDiscrepancies(involvedSwitchIds)
            involvedSwitches[1..-2].each {
                assert validationResultsMap[it.dpId].rules.missing.size() == 2 + it.defaultCookies.size()
                assert validationResultsMap[it.dpId].meters.missing.meterId.sort() == it.defaultMeters.sort()
            }
            switchPair.toList().each {
                def swProps = switchHelper.getCachedSwProps(it.dpId)
                def amountFlowRules = 2 //INGRESS_REVERSE, INGRESS_FORWARD
                def amountMultiTableSharedRules = 1
                def amountS42Rules = swProps.server42FlowRtt ? 3 : 0
                /**
                 * s42Rules
                 * SERVER_42_FLOW_RTT_INGRESS_REVERSE, SERVER_42_FLOW_RTT_INPUT, SERVER_42_FLOW_RTT_TURNING_COOKIE,
                 * SERVER42_QINQ_OUTER_VLAN (for QinQ flows, should not present in this test)
                 * some rule is in defaultCookies (SERVER_42_FLOW_RTT_OUTPUT_VLAN_COOKIE)
                 */

                def amountRules = amountFlowRules + amountS42Rules + amountMultiTableSharedRules + it.defaultCookies.size()
                assert validationResultsMap[it.dpId].rules.missing.size() == amountRules
                assert validationResultsMap[it.dpId].meters.missing.size() == 1 + it.defaultMeters.size()
            }
        }

        when: "Try to synchronize all switches"
        def syncResultsMap = switchHelper.synchronizeAndCollectFixedDiscrepancies(involvedSwitchIds)

        then: "System detects missing rules and meters (both default and flow-related), then installs them"
        involvedSwitches.each {
            assert syncResultsMap[it.dpId].rules.proper.size() == 0
            assert syncResultsMap[it.dpId].rules.excess.size() == 0
            assert syncResultsMap[it.dpId].rules.missing.containsAll(cookiesMap[it.dpId])
            assert syncResultsMap[it.dpId].rules.removed.size() == 0
            assert syncResultsMap[it.dpId].rules.installed.containsAll(cookiesMap[it.dpId])
        }
        switchPair.toList().each {
            assert syncResultsMap[it.dpId].meters.proper.size() == 0
            assert syncResultsMap[it.dpId].meters.excess.size() == 0
            assert syncResultsMap[it.dpId].meters.missing*.meterId == metersMap[it.dpId].sort()
            assert syncResultsMap[it.dpId].meters.removed.size() == 0
            assert syncResultsMap[it.dpId].meters.installed*.meterId == metersMap[it.dpId].sort()
        }

        and: "Switch validation doesn't complain about any missing rules and meters"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            switchHelper.validateAndCollectFoundDiscrepancies(involvedSwitchIds).isEmpty()
        }
    }

    def "Able to synchronize #switchKind switch (delete excess rules and meters)"() {
        given: "Flow with intermediate switches"
        def switchPair = switchPairs.all().nonNeighbouring().random()
        def flow = flowHelperV2.randomFlow(switchPair)
        flowHelperV2.addFlow(flow)
        def switchId = getSwitch(flow)
        def ofVersion = topology.getActiveSwitches().find{it.getDpId() == switchId}.getOfVersion()
        assert !switchHelper.synchronizeAndCollectFixedDiscrepancies(switchId).isPresent()

        and: "Force install excess rules and meters on switch"
        cleanupManager.addAction(SYNCHRONIZE_SWITCH, {switchHelper.synchronize(switchId)})
        def producer = new KafkaProducer(producerProps)
        def excessRuleCookie = 1234567890L
        def excessMeterId = ((MIN_FLOW_METER_ID..100)
                - northbound.getAllMeters(switchId).meterEntries*.meterId).first() as Long
        producer.send(new ProducerRecord(speakerTopic, switchId.toString(), buildMessage([
                FlowSpeakerData.builder()
                        .switchId(switchId)
                        .ofVersion(OfVersion.of(ofVersion))
                        .cookie(new Cookie(excessRuleCookie))
                        .table(table)
                        .priority(100)
                        .instructions(Instructions.builder().build())
                        .build(),
                MeterSpeakerData.builder()
                        .switchId(switchId)
                        .ofVersion(OfVersion.of(ofVersion))
                        .meterId(new MeterId(excessMeterId))
                        .rate(flow.getMaximumBandwidth())
                        .burst(flow.getMaximumBandwidth())
                        .flags(Sets.newHashSet(MeterFlag.KBPS, MeterFlag.BURST, MeterFlag.STATS))
                        .build()]).toJson())).get()
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            !switchHelper.validate(switchId).getRules().getExcess().isEmpty()
        }

        when: "Try to synchronize the switch"
        def syncResult = switchHelper.synchronize(switchId)

        then: "System detects excess rules and meters"
        verifyAll (syncResult) {
            rules.excess == [excessRuleCookie]
            rules.removed == [excessRuleCookie]
            meters.excess*.meterId == [excessMeterId]
            meters.removed*.meterId == [excessMeterId]
        }

        and: "Switch validation doesn't complain about excess rules and meters"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            switchHelper.validate(switchId).isAsExpected()
        }

        where:
        switchKind | getSwitch | table
        "source"   | { FlowRequestV2 flowRequestV2 -> flowRequestV2.getSource().getSwitchId()} | INPUT
        "destination"| {FlowRequestV2 flowRequestV2 -> flowRequestV2.getDestination().getSwitchId()}| EGRESS
        "transit"| {FlowRequestV2 flowRequestV2 ->
            def allSwitches = pathHelper.getInvolvedSwitches(flowRequestV2.getFlowId()).collect {it.getDpId()}
            def transitSwitches = allSwitches - [flowRequestV2.getDestination().getSwitchId(),
                                                 flowRequestV2.getSource().getSwitchId()]
            return transitSwitches.shuffled().first() }| TRANSIT
    }

    def "Able to synchronize switch with 'vxlan' rule(install missing rules and meters)"() {
        given: "Two active not neighboring switches which support VXLAN encapsulation"
        def switchPair = switchPairs.all().nonNeighbouring().withBothSwitchesVxLanEnabled().random()

        and: "Create a flow with vxlan encapsulation"
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.encapsulationType = FlowEncapsulationType.VXLAN
        flowHelperV2.addFlow(flow)

        and: "Reproduce situation when switches have missing rules and meters"
        def flowInfoFromDb = database.getFlow(flow.flowId)
        def involvedSwitches = pathHelper.getInvolvedSwitches(flow.flowId)
        def involvedSwitchIds = involvedSwitches*.getDpId()
        def transitSwitchIds = involvedSwitches[1..-2]*.dpId
        def cookiesMap = involvedSwitches.collectEntries { sw ->
            [sw.dpId, northbound.getSwitchRules(sw.dpId).flowEntries.findAll {
                !(it.cookie in sw.defaultCookies) && !new Cookie(it.cookie).serviceFlag
            }*.cookie]
        }
        def metersMap = involvedSwitches.collectEntries { sw ->
            [sw.dpId, northbound.getAllMeters(sw.dpId).meterEntries.findAll {
                it.meterId > MAX_SYSTEM_RULE_METER_ID
            }*.meterId]
        }

        involvedSwitches.each { switchHelper.deleteSwitchRules(it.dpId, DeleteRulesAction.IGNORE_DEFAULTS) }
        [switchPair.src, switchPair.dst].each { northbound.deleteMeter(it.dpId, metersMap[it.dpId][0]) }
        Wrappers.wait(RULES_DELETION_TIME) {
            def validationResultsMap = switchHelper.validateAndCollectFoundDiscrepancies(involvedSwitchIds)
            involvedSwitches.each {
                def swProps = switchHelper.getCachedSwProps(it.dpId)
                def switchIdInSrcOrDst = (it.dpId in [switchPair.src.dpId, switchPair.dst.dpId])
                def defaultAmountOfFlowRules = 2 // ingress + egress
                def amountOfServer42Rules = (switchIdInSrcOrDst && swProps.server42FlowRtt ? 1 : 0)
                if (swProps.server42FlowRtt) {
                    if ((flow.destination.getSwitchId() == it.dpId && flow.destination.vlanId) || (
                            flow.source.getSwitchId() == it.dpId && flow.source.vlanId))
                        amountOfServer42Rules += 1
                }
                def rulesCount = defaultAmountOfFlowRules + amountOfServer42Rules +
                        (switchIdInSrcOrDst ? 1 : 0)
                assert validationResultsMap[it.dpId].rules.missing.size() == rulesCount }
            switchPair.toList().each { assert validationResultsMap[it.dpId].meters.missing.size() == 1 }
        }

        when: "Try to synchronize all switches"
        def syncResultsMap = switchHelper.synchronizeAndCollectFixedDiscrepancies(involvedSwitchIds)

        then: "System detects missing rules and meters, then installs them"
        involvedSwitches.each {
            assert syncResultsMap[it.dpId].rules.proper.findAll { !new Cookie(it).serviceFlag }.size() == 0
            assert syncResultsMap[it.dpId].rules.excess.size() == 0
            assert syncResultsMap[it.dpId].rules.missing.containsAll(cookiesMap[it.dpId])
            assert syncResultsMap[it.dpId].rules.removed.size() == 0
            assert syncResultsMap[it.dpId].rules.installed.containsAll(cookiesMap[it.dpId])
        }
        switchPair.toList().each {
            assert syncResultsMap[it.dpId].meters.proper.findAll { !it.defaultMeter }.size() == 0
            assert syncResultsMap[it.dpId].meters.excess.size() == 0
            assert syncResultsMap[it.dpId].meters.missing*.meterId == metersMap[it.dpId]
            assert syncResultsMap[it.dpId].meters.removed.size() == 0
            assert syncResultsMap[it.dpId].meters.installed*.meterId == metersMap[it.dpId]
        }

        and: "Switch validation doesn't complain about missing rules and meters"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            switchHelper.validateAndCollectFoundDiscrepancies(involvedSwitchIds).isEmpty()
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
    }

    @Tags([VIRTUAL, LOW_PRIORITY])
    def "Able to synchronize misconfigured default meter"() {
        given: "An active switch with valid default rules and one misconfigured default meter"
        def sw = topology.activeSwitches.first()
        def broadcastCookieMeterId = MeterId.createMeterIdForDefaultRule(VERIFICATION_BROADCAST_RULE_COOKIE).getValue()
        def meterToManipulate = northbound.getAllMeters(sw.dpId).meterEntries
                .find{ it.meterId == broadcastCookieMeterId }
        def newBurstSize = meterToManipulate.burstSize + 100
        def newRate = meterToManipulate.rate + 100

        cleanupManager.addAction(SYNCHRONIZE_SWITCH, {switchHelper.synchronize(sw.dpId)})
        lockKeeper.updateBurstSizeAndRate(sw, meterToManipulate.meterId, newBurstSize, newRate)
        Wrappers.wait(RULES_DELETION_TIME) {
            def validateInfo = switchHelper.validate(sw.dpId)
            assert validateInfo.rules.missing.empty
            assert validateInfo.rules.misconfigured.empty
            assert validateInfo.meters.misconfigured.size() == 1
            assert validateInfo.meters.misconfigured[0].discrepancies.burstSize == newBurstSize
            assert validateInfo.meters.misconfigured[0].expected.burstSize == meterToManipulate.burstSize
            assert validateInfo.meters.misconfigured[0].discrepancies.rate == newRate
            assert validateInfo.meters.misconfigured[0].expected.rate == meterToManipulate.rate
        }

        when: "Synchronize switch"
        switchHelper.synchronize(sw.dpId, false)

        then: "The misconfigured meter is fixed and moved to the 'proper' section"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            with(switchHelper.validate(sw.dpId)) {
                it.meters.misconfigured.empty
                it.meters.proper.find { it.meterId == broadcastCookieMeterId }.burstSize == meterToManipulate.burstSize
                it.meters.proper.find { it.meterId == broadcastCookieMeterId }.rate == meterToManipulate.rate
            }
        }
    }

    @Tags([VIRTUAL, SMOKE])
    def "Able to synchronize misconfigured flow meter"() {
        given: "An active switch with flow on it"
        def sw = topology.activeSwitches.first()
        def flow = flowHelperV2.singleSwitchFlow(sw)
        flowHelperV2.addFlow(flow)

        when: "Update flow's meter"
        def flowMeterIdToManipulate = northbound.getAllMeters(sw.dpId).meterEntries.find {
            it.meterId >= MIN_FLOW_METER_ID
        }
        def newBurstSize = flowMeterIdToManipulate.burstSize + 100
        def newRate = flowMeterIdToManipulate.rate + 100
        lockKeeper.updateBurstSizeAndRate(sw, flowMeterIdToManipulate.meterId, newBurstSize, newRate)

        then: "Flow is not valid"
        def responseValidateFlow = northbound.validateFlow(flow.flowId).findAll { !it.discrepancies.empty }*.discrepancies
        assert responseValidateFlow.size() == 1
        def meterRateDiscrepancies = responseValidateFlow[0].find { it.field.toString() == "meterRate" }
        def meterBurstSizeDiscrepancies = responseValidateFlow[0].find { it.field.toString() == "meterBurstSize" }
        meterRateDiscrepancies.actualValue == newRate.toString()
        meterRateDiscrepancies.expectedValue == flowMeterIdToManipulate.rate.toString()
        meterBurstSizeDiscrepancies.actualValue == newBurstSize.toString()
        meterBurstSizeDiscrepancies.expectedValue == flowMeterIdToManipulate.burstSize.toString()

        and: "Validate switch endpoint shows the updated meter as misconfigured"
        Wrappers.wait(RULES_DELETION_TIME) {
            def validateInfo = switchHelper.validate(sw.dpId)
            assert validateInfo.meters.misconfigured.size() == 1
            assert validateInfo.meters.misconfigured[0].discrepancies.burstSize == newBurstSize
            assert validateInfo.meters.misconfigured[0].expected.burstSize == flowMeterIdToManipulate.burstSize
            assert validateInfo.meters.misconfigured[0].discrepancies.rate == newRate
            assert validateInfo.meters.misconfigured[0].expected.rate == flowMeterIdToManipulate.rate
        }

        when: "Synchronize switch"
        switchHelper.synchronize(sw.dpId, false)

        then: "The misconfigured meter was fixed and moved to the 'proper' section"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            with(switchHelper.validate(sw.dpId)) {
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
        northbound.validateFlow(flow.flowId).each { direction -> assert direction.asExpected }
    }
}
