package org.openkilda.functionaltests.spec.switches

import static org.openkilda.functionaltests.extension.tags.Tag.LOCKKEEPER
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.extension.tags.Tag.SWITCH_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESTORE_SWITCH_PROPERTIES
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.SYNCHRONIZE_SWITCH
import static org.openkilda.messaging.info.event.IslChangeType.*
import static org.openkilda.messaging.info.event.SwitchChangeType.ACTIVATED
import static org.openkilda.model.MeterId.MAX_SYSTEM_RULE_METER_ID
import static org.openkilda.model.MeterId.MIN_FLOW_METER_ID
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW
import static org.openkilda.testing.tools.KafkaUtils.buildCookie
import static org.openkilda.testing.tools.KafkaUtils.buildMessage

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.messaging.command.switches.DeleteRulesAction
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
import spock.lang.Shared


class SwitchActivationSpec extends HealthCheckSpecification {
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

    @Tags([SMOKE, SMOKE_SWITCHES, LOCKKEEPER, SWITCH_RECOVER_ON_FAIL])
    def "Missing flow rules/meters are installed on a new switch before connecting to the controller"() {
        given: "A switch with missing flow rules/meters and not connected to the controller"
        def switchPair = switchPairs.all().neighbouring().random()
        def flow = flowFactory.getRandom(switchPair)

        def originalMeterIds = switchPair.src.metersManager.getMeters().meterId
        assert originalMeterIds.size() == 1 + switchPair.src.collectDefaultMeters().size()

        def createdCookies = switchPair.src.rulesManager.getNotDefaultRules().cookie
        def amountOfFlowRules = switchPair.src.collectFlowRelatedRulesAmount(flow)
        assert createdCookies.size() == amountOfFlowRules

        def nonDefaultMeterIds = originalMeterIds.findAll({it > MAX_SYSTEM_RULE_METER_ID})
        switchPair.src.metersManager.delete(nonDefaultMeterIds[0])
        switchPair.src.rulesManager.delete(DeleteRulesAction.IGNORE_DEFAULTS)

        Wrappers.wait(WAIT_OFFSET) {
            with(switchPair.src.validate()) {
                it.rules.missing*.cookie.containsAll(createdCookies)
                it.rules.excess.empty
                it.rules.misconfigured.empty
                it.meters.missing.size() == 1
                it.meters.excess.empty
                it.meters.misconfigured.empty
            }
        }

        def blockData = switchPair.src.knockout(RW)

        when: "Connect the switch to the controller"
        switchPair.src.revive(blockData)

        then: "Missing flow rules/meters were synced during switch activation"
        !switchPair.src.validateAndCollectFoundDiscrepancies().isPresent()
    }

    @Tags([SMOKE_SWITCHES, SWITCH_RECOVER_ON_FAIL])
    def "Excess transitVlanRules/meters are synced from a new switch before connecting to the controller"() {
        given: "A switch with excess rules/meters and not connected to the controller"
        def sw = switches.all().first()

        def producer = new KafkaProducer(producerProps)
        //pick a meter id which is not yet used on src switch
        def excessMeterId = ((MIN_FLOW_METER_ID..100) - sw.metersManager.getMeters().meterId).first()
        cleanupManager.addAction(SYNCHRONIZE_SWITCH, { sw.synchronizeAndCollectFixedDiscrepancies() })

        producer.send(new ProducerRecord(speakerTopic, sw.switchId.toString(), buildMessage(
                FlowSpeakerData.builder()
                        .switchId(sw.switchId)
                        .ofVersion(OfVersion.of(sw.ofVersion))
                        .cookie(new Cookie(1))
                        .table(OfTable.EGRESS)
                        .priority(100)
                        .instructions(Instructions.builder().build())
                        .build()).toJson())).get()
        producer.send(new ProducerRecord(speakerTopic, sw.switchId.toString(), buildMessage(
                FlowSpeakerData.builder()
                        .switchId(sw.switchId)
                        .ofVersion(OfVersion.of(sw.ofVersion))
                        .cookie(new Cookie(2))
                        .table(OfTable.TRANSIT)
                        .priority(100)
                        .instructions(Instructions.builder().build())
                        .build()).toJson())).get()
        producer.send(new ProducerRecord(speakerTopic, sw.switchId.toString(), buildMessage([
                FlowSpeakerData.builder()
                        .switchId(sw.switchId)
                        .ofVersion(OfVersion.of(sw.ofVersion))
                        .cookie(new Cookie(3))
                        .table(OfTable.INPUT)
                        .priority(100)
                        .instructions(Instructions.builder().build())
                        .build(),
                MeterSpeakerData.builder()
                        .switchId(sw.switchId)
                        .ofVersion(OfVersion.of(sw.ofVersion))
                        .meterId(new MeterId(excessMeterId))
                        .rate(300)
                        .burst(300)
                        .flags(Sets.newHashSet(MeterFlag.PKTPS, MeterFlag.BURST, MeterFlag.STATS))
                        .build()]).toJson())).get()

        producer.flush()

        Wrappers.wait(WAIT_OFFSET) {
            verifyAll(sw.validate()) {
                it.rules.excess.size() == 3
                it.rules.misconfigured.empty
                it.rules.missing.empty
                it.meters.excess.size() == 1
                it.meters.misconfigured.empty
                it.meters.missing.empty
            }
        }

        def blockData = sw.knockout(RW)

        when: "Connect the switch to the controller"
        sw.revive(blockData)

        then: "Excess meters/rules were synced during switch activation"
        !sw.synchronizeAndCollectFixedDiscrepancies().isPresent()
    }

    @Tags([SMOKE_SWITCHES, SWITCH_RECOVER_ON_FAIL])
    def "Excess vxlanRules/meters are synced from a new switch before connecting to the controller"() {
        given: "A switch with excess rules/meters and not connected to the controller"
        def sw = switches.all().withVxlanEnabled().first()
        def producer = new KafkaProducer(producerProps)
        //pick a meter id which is not yet used on src switch
        def excessMeterId = ((MIN_FLOW_METER_ID..100) - sw.metersManager.getMeters().meterId).first()
        cleanupManager.addAction(SYNCHRONIZE_SWITCH, { sw.synchronize()} )
        producer.send(new ProducerRecord(speakerTopic, sw.switchId.toString(), buildMessage(
                FlowSpeakerData.builder()
                        .switchId(sw.switchId)
                        .ofVersion(OfVersion.of(sw.ofVersion))
                        .cookie(buildCookie(1L))
                        .table(OfTable.EGRESS)
                        .priority(100)
                        .instructions(Instructions.builder().build())
                        .build()).toJson())).get()
        producer.send(new ProducerRecord(speakerTopic, sw.switchId.toString(), buildMessage(
                FlowSpeakerData.builder()
                        .switchId(sw.switchId)
                        .ofVersion(OfVersion.of(sw.ofVersion))
                        .cookie(buildCookie(2L))
                        .table(OfTable.TRANSIT)
                        .priority(100)
                        .instructions(Instructions.builder().build())
                        .build()).toJson())).get()
        producer.send(new ProducerRecord(speakerTopic, sw.switchId.toString(), buildMessage([
                FlowSpeakerData.builder()
                        .switchId(sw.switchId)
                        .ofVersion(OfVersion.of(sw.ofVersion))
                        .cookie(buildCookie(3L))
                        .table(OfTable.INPUT)
                        .priority(100)
                        .instructions(Instructions.builder().build())
                        .build(),
                MeterSpeakerData.builder()
                        .switchId(sw.switchId)
                        .ofVersion(OfVersion.of(sw.ofVersion))
                        .meterId(new MeterId(excessMeterId))
                        .rate(300)
                        .burst(300)
                        .flags(Sets.newHashSet(MeterFlag.PKTPS, MeterFlag.BURST, MeterFlag.STATS))
                        .build()]).toJson())).get()

        producer.flush()

        Wrappers.wait(WAIT_OFFSET) {
            verifyAll(sw.validate()) {
                it.rules.excess.size() == 3
                it.rules.missing.empty
                it.rules.misconfigured.empty
                it.meters.excess.size() == 1
                it.meters.misconfigured.empty
                it.meters.missing.empty
            }
        }

        def blockData = sw.knockout(RW)

        when: "Connect the switch to the controller"
        sw.revive(blockData)

        then: "Excess meters/rules were synced during switch activation"
        !sw.synchronizeAndCollectFixedDiscrepancies().isPresent()
    }

    @Tags([SMOKE, SMOKE_SWITCHES, LOCKKEEPER, SWITCH_RECOVER_ON_FAIL])
    def "New connected switch is properly discovered with related ISLs in a reasonable time"() {
        setup: "Disconnect one of the switches and remove it from DB. Pretend this switch never existed"
        def sw = switches.all().first()
        def isls = isls.all().relatedTo(sw).getListOfIsls()
        /*in case supportedTransitEncapsulation == ["transit_vlan", "vxlan"]
        then after removing/adding the same switch this fields will be changed (["transit_vlan"])
        vxlan encapsulation is not set by default*/
        def initSwProps = sw.getProps()
        def blockData = sw.knockout(RW)
        //Verifying links actualState (for HW link state isn't changed) as lockKeeper imitates switch off event
        Wrappers.wait(WAIT_OFFSET + discoveryTimeout) {
            sw.collectForwardAndReverseRelatedLinks().each { assert it.actualState == FAILED }
        }

        isls.each { it.delete(true) }
        cleanupManager.addAction(RESTORE_SWITCH_PROPERTIES, { northbound.updateSwitchProperties(sw.switchId, initSwProps) })
        Wrappers.retry(2) { sw.delete() }

        when: "New switch connects"
        sw.revive(blockData, false)

        then: "Switch is activated"
        sw.getDetails().state == ACTIVATED

        and: "Related ISLs are discovered"
        Wrappers.wait(discoveryExhaustedInterval + WAIT_OFFSET / 2 + antiflapCooldown) {
            sw.collectForwardAndReverseRelatedLinks().each { assert it.actualState == DISCOVERED }
        }
    }
}
