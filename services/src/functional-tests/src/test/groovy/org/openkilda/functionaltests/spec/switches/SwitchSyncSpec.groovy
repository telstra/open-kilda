package org.openkilda.functionaltests.spec.switches

import static org.junit.Assume.assumeTrue
import static org.openkilda.model.MeterId.MAX_SYSTEM_RULE_METER_ID
import static org.openkilda.model.MeterId.MIN_FLOW_METER_ID
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.Message
import org.openkilda.messaging.command.CommandData
import org.openkilda.messaging.command.CommandMessage
import org.openkilda.messaging.command.flow.InstallIngressFlow
import org.openkilda.messaging.command.flow.InstallTransitFlow
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.model.OutputVlanType
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import spock.lang.Unroll

class SwitchSyncSpec extends BaseSpecification {

    @Value("#{kafkaTopicsConfig.getSpeakerFlowTopic()}")
    String flowTopic

    @Autowired
    @Qualifier("kafkaProducerProperties")
    Properties producerProps

    @Unroll
    def "Able to synchronize switch without any rule and meter discrepancies (removeExcess=#removeExcess)"() {
        given: "An active switch"
        def sw = topology.activeSwitches.first()

        when: "Synchronize the switch that doesn't have any rule and meter discrepancies"
        def syncResult = northbound.synchronizeSwitch(sw.dpId, removeExcess)

        then: "Operation is successful"
        syncResult.rules.proper.size() == 0
        syncResult.rules.excess.size() == 0
        syncResult.rules.missing.size() == 0
        syncResult.rules.removed.size() == 0
        syncResult.rules.installed.size() == 0

        syncResult.meters.proper.size() == 0
        syncResult.meters.excess.size() == 0
        syncResult.meters.missing.size() == 0
        syncResult.meters.removed.size() == 0
        syncResult.meters.installed.size() == 0

        where:
        removeExcess << [false, true]
    }

    def "Able to synchronize switch (install missing rules and meters)"() {
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

        and: "Reproduce situation when switches have missing rules and meters"
        def involvedSwitches = pathHelper.getInvolvedSwitches(flow.id)
        def cookiesMap = involvedSwitches.collectEntries { sw ->
            [sw.dpId, northbound.getSwitchRules(sw.dpId).flowEntries.findAll {
                !(it.cookie in sw.defaultCookies)
            }*.cookie]
        }
        def metersMap = involvedSwitches.collectEntries { sw ->
            [sw.dpId, northbound.getAllMeters(sw.dpId).meterEntries.findAll {
                it.meterId > MAX_SYSTEM_RULE_METER_ID
            }*.meterId]
        }

        involvedSwitches.each { northbound.deleteSwitchRules(it.dpId, DeleteRulesAction.IGNORE_DEFAULTS) }
        [srcSwitch, dstSwitch].each { northbound.deleteMeter(it.dpId, metersMap[it.dpId][0]) }
        Wrappers.wait(RULES_DELETION_TIME) {
            def validationResultsMap = involvedSwitches.collectEntries { [it.dpId, northbound.switchValidate(it.dpId)] }
            involvedSwitches.each { assert validationResultsMap[it.dpId].rules.missing.size() == 2 }
            [srcSwitch, dstSwitch].each { assert validationResultsMap[it.dpId].meters.missing.size() == 1 }
        }

        when: "Try to synchronize all switches"
        def syncResultsMap = involvedSwitches.collectEntries { [it.dpId, northbound.synchronizeSwitch(it.dpId, false)] }

        then: "System detects missing rules and meters, then installs them"
        involvedSwitches.each {
            assert syncResultsMap[it.dpId].rules.proper.size() == 0
            assert syncResultsMap[it.dpId].rules.excess.size() == 0
            assert syncResultsMap[it.dpId].rules.missing.containsAll(cookiesMap[it.dpId])
            assert syncResultsMap[it.dpId].rules.removed.size() == 0
            assert syncResultsMap[it.dpId].rules.installed.containsAll(cookiesMap[it.dpId])
        }
        [srcSwitch, dstSwitch].each {
            assert syncResultsMap[it.dpId].meters.proper.size() == 0
            assert syncResultsMap[it.dpId].meters.excess.size() == 0
            assert syncResultsMap[it.dpId].meters.missing*.meterId == metersMap[it.dpId]
            assert syncResultsMap[it.dpId].meters.removed.size() == 0
            assert syncResultsMap[it.dpId].meters.installed*.meterId == metersMap[it.dpId]
        }

        and: "Switch validation doesn't complain about missing rules and meters"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            involvedSwitches.each {
                def validationResult = northbound.switchValidate(it.dpId)
                assert validationResult.rules.missing.size() == 0
                assert validationResult.meters.missing.size() == 0
            }
        }

        and: "Delete the flow"
        flowHelper.deleteFlow(flow.id)
    }

    def "Able to synchronize switch (delete excess rules and meters)"() {
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

        and: "Reproduce situation when switches have excess rules and meters"
        def involvedSwitches = pathHelper.getInvolvedSwitches(flow.id)
        def cookiesMap = involvedSwitches.collectEntries { sw ->
            [sw.dpId, northbound.getSwitchRules(sw.dpId).flowEntries.findAll {
                !(it.cookie in sw.defaultCookies)
            }*.cookie]
        }
        def metersMap = involvedSwitches.collectEntries { sw ->
            [sw.dpId, northbound.getAllMeters(sw.dpId).meterEntries.findAll {
                it.meterId > MAX_SYSTEM_RULE_METER_ID
            }*.meterId]
        }

        def producer = new KafkaProducer(producerProps)
        def excessRuleCookie = 1234567890L
        def excessMeterId = ((MIN_FLOW_METER_ID..100) -
                northbound.getAllMeters(dstSwitch.dpId).meterEntries*.meterId)[0]

        producer.send(new ProducerRecord(flowTopic, srcSwitch.dpId.toString(), buildMessage(
                new InstallIngressFlow(UUID.randomUUID(), flow.id, excessRuleCookie, srcSwitch.dpId, 1, 2, 1, 1,
                        OutputVlanType.REPLACE, flow.maximumBandwidth, excessMeterId)).toJson()))
        involvedSwitches[1..-2].each { transitSw ->
            producer.send(new ProducerRecord(flowTopic, transitSw.toString(), buildMessage(
                    new InstallTransitFlow(UUID.randomUUID(), flow.id, excessRuleCookie, transitSw.dpId, 1, 2, 1))
                    .toJson()))
        }
        producer.send(new ProducerRecord(flowTopic, dstSwitch.dpId.toString(), buildMessage(
                new InstallIngressFlow(UUID.randomUUID(), flow.id, excessRuleCookie, dstSwitch.dpId, 1, 2, 1, 1,
                        OutputVlanType.REPLACE, flow.maximumBandwidth, excessMeterId)).toJson()))

        Wrappers.wait(RULES_INSTALLATION_TIME) {
            def validationResultsMap = involvedSwitches.collectEntries { [it.dpId, northbound.switchValidate(it.dpId)] }
            involvedSwitches.each { assert validationResultsMap[it.dpId].rules.excess.size() == 1 }
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
            assert syncResultsMap[it.dpId].meters.proper*.meterId == metersMap[it.dpId]
            assert syncResultsMap[it.dpId].meters.excess*.meterId == [excessMeterId]
            assert syncResultsMap[it.dpId].meters.missing.size() == 0
            assert syncResultsMap[it.dpId].meters.removed*.meterId == [excessMeterId]
            assert syncResultsMap[it.dpId].meters.installed.size() == 0
        }

        and: "Switch validation doesn't complain about excess rules and meters"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            involvedSwitches.each {
                def validationResult = northbound.switchValidate(it.dpId)
                assert validationResult.rules.excess.size() == 0
                assert validationResult.meters.excess.size() == 0
            }
        }

        and: "Delete the flow"
        flowHelper.deleteFlow(flow.id)
    }

    private static Message buildMessage(final CommandData data) {
        return new CommandMessage(data, System.currentTimeMillis(), UUID.randomUUID().toString(), null)
    }
}
