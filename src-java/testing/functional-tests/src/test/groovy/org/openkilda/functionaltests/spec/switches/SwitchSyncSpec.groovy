package org.openkilda.functionaltests.spec.switches

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.extension.tags.Tag.VIRTUAL
import static org.openkilda.functionaltests.helpers.SwitchHelper.isDefaultMeter
import static org.openkilda.model.MeterId.MAX_SYSTEM_RULE_METER_ID
import static org.openkilda.model.MeterId.MIN_FLOW_METER_ID
import static org.openkilda.model.cookie.Cookie.VERIFICATION_BROADCAST_RULE_COOKIE
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.tools.KafkaUtils.buildMessage

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.model.FlowEncapsulationType
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
import spock.lang.See

@See(["https://github.com/telstra/open-kilda/tree/develop/docs/design/hub-and-spoke/switch-sync",
"https://github.com/telstra/open-kilda/blob/develop/docs/design/network-discovery/switch-FSM.png"])
@Tags([SMOKE_SWITCHES])
class SwitchSyncSpec extends BaseSpecification {

    @Value("#{kafkaTopicsConfig.getSpeakerSwitchManagerTopic()}")
    String speakerTopic

    @Autowired
    @Qualifier("kafkaProducerProperties")
    Properties producerProps

    @Tidy
    def "Able to synchronize switch without any rule and meter discrepancies (removeExcess=#removeExcess)"() {
        given: "An active switch"
        def sw = topology.activeSwitches.first()

        when: "Synchronize the switch that doesn't have any rule and meter discrepancies"
        def syncResult = northbound.synchronizeSwitch(sw.dpId, removeExcess)

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

    @Tidy
    def "Able to synchronize switch (install missing rules and meters)"() {
        given: "Two active not neighboring switches"
        def switchPair = topologyHelper.allNotNeighboringSwitchPairs.find { it.src.ofVersion != "OF_12" &&
                it.dst.ofVersion != "OF_12" } ?: assumeTrue(false, "No suiting switches found")

        and: "Create an intermediate-switch flow"
        def flow = flowHelperV2.randomFlow(switchPair)
        flowHelperV2.addFlow(flow)

        and: "Drop all rules an meters from related switches (both default and non-default)"
        def involvedSwitches = pathHelper.getInvolvedSwitches(flow.flowId)
        def cookiesMap = involvedSwitches.collectEntries { sw ->
            [sw.dpId, northbound.getSwitchRules(sw.dpId).flowEntries.findAll {
                !(it.cookie in sw.defaultCookies)
            }*.cookie]
        }
        Map<SwitchId, List<Long>> metersMap = involvedSwitches.collectEntries { sw ->
            [sw.dpId, northbound.getAllMeters(sw.dpId).meterEntries*.meterId]
        }

        involvedSwitches.each { sw ->
            northbound.deleteSwitchRules(sw.dpId, DeleteRulesAction.DROP_ALL)
            northbound.getAllMeters(sw.dpId).meterEntries.each { northbound.deleteMeter(sw.dpId, it.meterId) }
        }
        Wrappers.wait(RULES_DELETION_TIME) {
            def validationResultsMap = involvedSwitches.collectEntries { [it.dpId, northbound.validateSwitch(it.dpId)] }
            involvedSwitches[1..-2].each {
                assert validationResultsMap[it.dpId].rules.missing.size() == 2 + it.defaultCookies.size()
                assert validationResultsMap[it.dpId].rules.missingHex.size() == 2 + it.defaultCookies.size()
                assert validationResultsMap[it.dpId].meters.missing.meterId.sort() == it.defaultMeters.sort()
            }
            [switchPair.src, switchPair.dst].each {
                def swProps = switchHelper.getCachedSwProps(it.dpId)
                def amountFlowRules = 2 //INGRESS_REVERSE, INGRESS_FORWARD
                def amountS42Rules = swProps.server42FlowRtt ? 1 : 0
                def amountMultiTableSharedRules = 0
                if (swProps.multiTable) {
                    amountS42Rules = swProps.server42FlowRtt ? amountS42Rules + 2 : amountS42Rules
                    amountMultiTableSharedRules += 1
                }
                /**
                 * s42Rules
                 * multi/single table: SERVER_42_FLOW_RTT_INGRESS_REVERSE
                 * multiTable: SERVER_42_FLOW_RTT_INPUT, SERVER_42_FLOW_RTT_TURNING_COOKIE, SERVER42_QINQ_OUTER_VLAN
                 * some rule is in defaultCookies (SERVER_42_FLOW_RTT_OUTPUT_VLAN_COOKIE)
                 */

                def amountRules = amountFlowRules + amountS42Rules + amountMultiTableSharedRules + it.defaultCookies.size()
                assert validationResultsMap[it.dpId].rules.missing.size() == amountRules
                assert validationResultsMap[it.dpId].rules.missingHex.size() == amountRules
                assert validationResultsMap[it.dpId].meters.missing.size() == 1 + it.defaultMeters.size()
            }
        }

        when: "Try to synchronize all switches"
        def syncResultsMap = involvedSwitches.collectEntries { [it.dpId, northbound.synchronizeSwitch(it.dpId, false)] }

        then: "System detects missing rules and meters (both default and flow-related), then installs them"
        involvedSwitches.each {
            assert syncResultsMap[it.dpId].rules.proper.size() == 0
            assert syncResultsMap[it.dpId].rules.excess.size() == 0
            assert syncResultsMap[it.dpId].rules.missing.containsAll(cookiesMap[it.dpId])
            assert syncResultsMap[it.dpId].rules.removed.size() == 0
            assert syncResultsMap[it.dpId].rules.installed.containsAll(cookiesMap[it.dpId])
        }
        [switchPair.src, switchPair.dst].each {
            assert syncResultsMap[it.dpId].meters.proper.size() == 0
            assert syncResultsMap[it.dpId].meters.excess.size() == 0
            assert syncResultsMap[it.dpId].meters.missing*.meterId == metersMap[it.dpId].sort()
            assert syncResultsMap[it.dpId].meters.removed.size() == 0
            assert syncResultsMap[it.dpId].meters.installed*.meterId == metersMap[it.dpId].sort()
        }

        and: "Switch validation doesn't complain about any missing rules and meters"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            involvedSwitches.each {
                def validationResult = northbound.validateSwitch(it.dpId)
                assert validationResult.rules.missing.size() == 0
                if(!it.dpId.description.contains("OF_12")) {
                    assert validationResult.meters.missing.size() == 0
                }
            }
        }

        cleanup: "Delete the flow"
        flowHelperV2.deleteFlow(flow.flowId)
    }

    def "Able to synchronize switch (delete excess rules and meters)"() {
        given: "Two active not neighboring switches"
        def switches = topology.getActiveSwitches()
        def allLinks = northbound.getAllLinks()
        def (Switch srcSwitch, Switch dstSwitch) = [switches, switches].combinations()
                .findAll { src, dst -> src != dst }.find { Switch src, Switch dst ->
            allLinks.every { link -> !(link.source.switchId == src.dpId && link.destination.switchId == dst.dpId) }
        } ?: assumeTrue(false, "No suiting switches found")

        and: "Create an intermediate-switch flow"
        def flow = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        flowHelperV2.addFlow(flow)

        and: "Reproduce situation when switches have excess rules and meters"
        def involvedSwitches = pathHelper.getInvolvedSwitches(flow.flowId)
        def cookiesMap = involvedSwitches.collectEntries { sw ->
            [sw.dpId, northbound.getSwitchRules(sw.dpId).flowEntries.findAll {
                !(it.cookie in sw.defaultCookies)
            }*.cookie]
        }
        Map<SwitchId, List<Long>> metersMap = involvedSwitches.collectEntries { sw ->
            [sw.dpId, northbound.getAllMeters(sw.dpId).meterEntries*.meterId]
        }

        def producer = new KafkaProducer(producerProps)
        def excessRuleCookie = 1234567890L
        def excessMeterId = ((MIN_FLOW_METER_ID..100)
                - northbound.getAllMeters(srcSwitch.dpId).meterEntries*.meterId
                - northbound.getAllMeters(dstSwitch.dpId).meterEntries*.meterId)[0]

        producer.send(new ProducerRecord(speakerTopic, srcSwitch.dpId.toString(), buildMessage([
                FlowSpeakerData.builder()
                        .switchId(srcSwitch.dpId)
                        .ofVersion(OfVersion.of(srcSwitch.ofVersion))
                        .cookie(new Cookie(excessRuleCookie))
                        .table(OfTable.INPUT)
                        .priority(100)
                        .instructions(Instructions.builder().build())
                        .build(),
                MeterSpeakerData.builder()
                        .switchId(srcSwitch.dpId)
                        .ofVersion(OfVersion.of(srcSwitch.ofVersion))
                        .meterId(new MeterId(excessMeterId))
                        .rate(flow.getMaximumBandwidth())
                        .burst(flow.getMaximumBandwidth())
                        .flags(Sets.newHashSet(MeterFlag.PKTPS, MeterFlag.BURST, MeterFlag.STATS))
                        .build()]).toJson())).get()
        involvedSwitches[1..-2].each { transitSw ->
            producer.send(new ProducerRecord(speakerTopic, transitSw.toString(), buildMessage(
                    FlowSpeakerData.builder()
                            .switchId(transitSw.dpId)
                            .ofVersion(OfVersion.of(transitSw.ofVersion))
                            .cookie(new Cookie(excessRuleCookie))
                            .table(OfTable.TRANSIT)
                            .priority(100)
                            .instructions(Instructions.builder().build())
                            .build()).toJson())).get()
        }
        producer.send(new ProducerRecord(speakerTopic, dstSwitch.dpId.toString(), buildMessage([
                FlowSpeakerData.builder()
                        .switchId(dstSwitch.dpId)
                        .ofVersion(OfVersion.of(dstSwitch.ofVersion))
                        .cookie(new Cookie(excessRuleCookie))
                        .table(OfTable.INPUT)
                        .priority(100)
                        .instructions(Instructions.builder().build())
                        .build(),
                MeterSpeakerData.builder()
                        .switchId(dstSwitch.dpId)
                        .ofVersion(OfVersion.of(dstSwitch.ofVersion))
                        .meterId(new MeterId(excessMeterId))
                        .rate(flow.getMaximumBandwidth())
                        .burst(flow.getMaximumBandwidth())
                        .flags(Sets.newHashSet(MeterFlag.PKTPS, MeterFlag.BURST, MeterFlag.STATS))
                        .build()]).toJson())).get()

        Wrappers.wait(RULES_INSTALLATION_TIME) {
            def validationResultsMap = involvedSwitches.collectEntries { [it.dpId, northbound.validateSwitch(it.dpId)] }
            involvedSwitches.each {
                assert validationResultsMap[it.dpId].rules.excess.size() == 1
                assert validationResultsMap[it.dpId].rules.excessHex.size() == 1
            }
            [srcSwitch, dstSwitch].each { assert validationResultsMap[it.dpId].meters.excess.size() == 1 }
        }

        when: "Try to synchronize all switches"
        def syncResultsMap = involvedSwitches.collectEntries { [it.dpId, northbound.synchronizeSwitch(it.dpId, true)] }

        then: "System detects excess rules and meters, then deletes them"
        involvedSwitches.each {
            assert syncResultsMap[it.dpId].rules.proper.containsAll(cookiesMap[it.dpId])
            assert syncResultsMap[it.dpId].rules.excess == [excessRuleCookie]
            assert syncResultsMap[it.dpId].rules.missing.size() == 0
            assert syncResultsMap[it.dpId].rules.removed == [excessRuleCookie]
            assert syncResultsMap[it.dpId].rules.installed.size() == 0
        }
        [srcSwitch, dstSwitch].each {
            assert syncResultsMap[it.dpId].meters.proper*.meterId == metersMap[it.dpId].sort()
            assert syncResultsMap[it.dpId].meters.excess*.meterId == [excessMeterId]
            assert syncResultsMap[it.dpId].meters.missing.size() == 0
            assert syncResultsMap[it.dpId].meters.removed*.meterId == [excessMeterId]
            assert syncResultsMap[it.dpId].meters.installed.size() == 0
        }

        and: "Switch validation doesn't complain about excess rules and meters"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            involvedSwitches.each {
                def validationResult = northbound.validateSwitch(it.dpId)
                assert validationResult.rules.excess.size() == 0
                assert validationResult.rules.excessHex.size() == 0
                if(!it.dpId.description.contains("OF_12")) {
                    assert validationResult.meters.missing.size() == 0
                }
            }
        }

        and: "Delete the flow"
        flowHelperV2.deleteFlow(flow.flowId)
    }

    @Tidy
    def "Able to synchronize switch with 'vxlan' rule(install missing rules and meters)"() {
        given: "Two active not neighboring Noviflow switches"
        def switchPair = topologyHelper.getAllNotNeighboringSwitchPairs().find { swP ->
            swP.paths.find { path ->
                pathHelper.getInvolvedSwitches(path).every { switchHelper.isVxlanEnabled(it.dpId) }
            }
        } ?: assumeTrue(false, "Unable to find required switches in topology")

        and: "Create a flow with vxlan encapsulation"
        def flow = flowHelperV2.randomFlow(switchPair)
        flow.encapsulationType = FlowEncapsulationType.VXLAN
        flowHelperV2.addFlow(flow)

        and: "Reproduce situation when switches have missing rules and meters"
        def flowInfoFromDb = database.getFlow(flow.flowId)
        def involvedSwitches = pathHelper.getInvolvedSwitches(flow.flowId)
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

        involvedSwitches.each { northbound.deleteSwitchRules(it.dpId, DeleteRulesAction.IGNORE_DEFAULTS) }
        [switchPair.src, switchPair.dst].each { northbound.deleteMeter(it.dpId, metersMap[it.dpId][0]) }
        Wrappers.wait(RULES_DELETION_TIME) {
            def validationResultsMap = involvedSwitches.collectEntries { [it.dpId, northbound.validateSwitch(it.dpId)] }
            involvedSwitches.each {
                def swProps = switchHelper.getCachedSwProps(it.dpId)
                def switchIdInSrcOrDst = (it.dpId in [switchPair.src.dpId, switchPair.dst.dpId])
                def defaultAmountOfFlowRules = 2 // ingress + egress
                def amountOfServer42Rules = (switchIdInSrcOrDst && swProps.server42FlowRtt ? 1 : 0)
                if (swProps.multiTable && swProps.server42FlowRtt) {
                    if ((flow.destination.getSwitchId() == it.dpId && flow.destination.vlanId) || (
                            flow.source.getSwitchId() == it.dpId && flow.source.vlanId))
                        amountOfServer42Rules += 1
                }
                def rulesCount = defaultAmountOfFlowRules + amountOfServer42Rules +
                        (switchIdInSrcOrDst && swProps.multiTable ? 1 : 0)
                assert validationResultsMap[it.dpId].rules.missing.size() == rulesCount }
            [switchPair.src, switchPair.dst].each { assert validationResultsMap[it.dpId].meters.missing.size() == 1 }
        }

        when: "Try to synchronize all switches"
        def syncResultsMap = involvedSwitches.collectEntries { [it.dpId, northbound.synchronizeSwitch(it.dpId, false)] }

        then: "System detects missing rules and meters, then installs them"
        involvedSwitches.each {
            assert syncResultsMap[it.dpId].rules.proper.findAll { !new Cookie(it).serviceFlag }.size() == 0
            assert syncResultsMap[it.dpId].rules.excess.size() == 0
            assert syncResultsMap[it.dpId].rules.missing.containsAll(cookiesMap[it.dpId])
            assert syncResultsMap[it.dpId].rules.removed.size() == 0
            assert syncResultsMap[it.dpId].rules.installed.containsAll(cookiesMap[it.dpId])
        }
        [switchPair.src, switchPair.dst].each {
            assert syncResultsMap[it.dpId].meters.proper.findAll { !it.defaultMeter }.size() == 0
            assert syncResultsMap[it.dpId].meters.excess.size() == 0
            assert syncResultsMap[it.dpId].meters.missing*.meterId == metersMap[it.dpId]
            assert syncResultsMap[it.dpId].meters.removed.size() == 0
            assert syncResultsMap[it.dpId].meters.installed*.meterId == metersMap[it.dpId]
        }

        and: "Switch validation doesn't complain about missing rules and meters"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            involvedSwitches.each {
                def validationResult = northbound.validateSwitch(it.dpId)
                assert validationResult.rules.missing.size() == 0
                assert validationResult.meters.missing.size() == 0
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

        cleanup: "Delete the flow"
        flow && flowHelperV2.deleteFlow(flow.flowId)
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

        lockKeeper.updateBurstSizeAndRate(sw, meterToManipulate.meterId, newBurstSize, newRate)
        Wrappers.wait(RULES_DELETION_TIME) {
            def validateInfo = northbound.validateSwitch(sw.dpId)
            assert validateInfo.rules.missing.empty
            assert validateInfo.rules.misconfigured.empty
            assert validateInfo.meters.misconfigured.size() == 1
            assert validateInfo.meters.misconfigured[0].actual.burstSize == newBurstSize
            assert validateInfo.meters.misconfigured[0].expected.burstSize == meterToManipulate.burstSize
            assert validateInfo.meters.misconfigured[0].actual.rate == newRate
            assert validateInfo.meters.misconfigured[0].expected.rate == meterToManipulate.rate
        }

        when: "Synchronize switch"
        northbound.synchronizeSwitch(sw.dpId, false)

        then: "The misconfigured meter is fixed and moved to the 'proper' section"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            with(northbound.validateSwitch(sw.dpId)) {
                it.meters.misconfigured.empty
                it.meters.proper.find { it.meterId == broadcastCookieMeterId }.burstSize == meterToManipulate.burstSize
                it.meters.proper.find { it.meterId == broadcastCookieMeterId }.rate == meterToManipulate.rate
            }
        }
    }

    @Tidy
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
            def validateInfo = northbound.validateSwitch(sw.dpId)
            assert validateInfo.meters.misconfigured.size() == 1
            assert validateInfo.meters.misconfigured[0].actual.burstSize == newBurstSize
            assert validateInfo.meters.misconfigured[0].expected.burstSize == flowMeterIdToManipulate.burstSize
            assert validateInfo.meters.misconfigured[0].actual.rate == newRate
            assert validateInfo.meters.misconfigured[0].expected.rate == flowMeterIdToManipulate.rate
        }

        when: "Synchronize switch"
        northbound.synchronizeSwitch(sw.dpId, false)

        then: "The misconfigured meter was fixed and moved to the 'proper' section"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            with(northbound.validateSwitch(sw.dpId)) {
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

        cleanup:
        flow && flowHelperV2.deleteFlow(flow.flowId)
    }

    @Memoized
    def isVxlanEnabled(SwitchId switchId) {
        return northbound.getSwitchProperties(switchId).supportedTransitEncapsulation
                .contains(FlowEncapsulationType.VXLAN.toString().toLowerCase())
    }
}
