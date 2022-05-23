package org.openkilda.functionaltests.spec.switches

import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.LOCKKEEPER
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.messaging.info.event.SwitchChangeType.ACTIVATED
import static org.openkilda.messaging.info.event.SwitchChangeType.DEACTIVATED
import static org.openkilda.model.MeterId.MAX_SYSTEM_RULE_METER_ID
import static org.openkilda.model.MeterId.MIN_FLOW_METER_ID
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW
import static org.openkilda.testing.tools.KafkaUtils.buildCookie
import static org.openkilda.testing.tools.KafkaUtils.buildMessage

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.messaging.info.event.IslChangeType
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

class SwitchActivationSpec extends HealthCheckSpecification {
    @Value("#{kafkaTopicsConfig.getSpeakerSwitchManagerTopic()}")
    String speakerTopic
    @Autowired
    @Qualifier("kafkaProducerProperties")
    Properties producerProps

    @Tags([SMOKE, SMOKE_SWITCHES, LOCKKEEPER])
    def "Missing flow rules/meters are installed on a new switch before connecting to the controller"() {
        given: "A switch with missing flow rules/meters and not connected to the controller"
        def switchPair = topologyHelper.getNeighboringSwitchPair()
        def flow = flowHelperV2.randomFlow(switchPair)
        flowHelperV2.addFlow(flow)

        def originalMeterIds = northbound.getAllMeters(switchPair.src.dpId).meterEntries*.meterId
        assert originalMeterIds.size() == 1 + switchPair.src.defaultMeters.size()
        def createdCookies = northbound.getSwitchRules(switchPair.src.dpId).flowEntries.findAll {
            !new Cookie(it.cookie).serviceFlag
        }*.cookie
        def srcSwProps = northbound.getSwitchProperties(switchPair.src.dpId)
        def amountOfMultiTableRules = srcSwProps.multiTable ? 1 : 0
        def amountOfServer42IngressRules = srcSwProps.server42FlowRtt ? 1 : 0
        def amountOfServer42SharedRules = srcSwProps.multiTable && srcSwProps.server42FlowRtt
                && flow.source.vlanId ? 1 : 0
        def amountOfFlowRules = 2 + amountOfMultiTableRules + amountOfServer42IngressRules + amountOfServer42SharedRules
        def createdHexCookies = createdCookies.collect { Long.toHexString(it) }
        assert createdCookies.size() == amountOfFlowRules

        def nonDefaultMeterIds = originalMeterIds.findAll({it > MAX_SYSTEM_RULE_METER_ID})
        northbound.deleteMeter(switchPair.src.dpId, nonDefaultMeterIds[0])
        northbound.deleteSwitchRules(switchPair.src.dpId, DeleteRulesAction.IGNORE_DEFAULTS)
        Wrappers.wait(WAIT_OFFSET) {
            verifyAll(northbound.validateSwitch(switchPair.src.dpId)) {
                it.rules.missing.containsAll(createdCookies)
                it.rules.missingHex.containsAll(createdHexCookies)
                it.verifyRuleSectionsAreEmpty(["proper", "excess"])
                it.verifyHexRuleSectionsAreEmpty(["properHex", "excessHex"])
                it.meters.missing.size() == 1
                it.verifyMeterSectionsAreEmpty(["proper", "misconfigured", "excess"])
            }
        }

        def blockData = switchHelper.knockoutSwitch(switchPair.src, RW)

        when: "Connect the switch to the controller"
        switchHelper.reviveSwitch(switchPair.src, blockData)

        then: "Missing flow rules/meters were synced during switch activation"
        verifyAll(northbound.validateSwitch(switchPair.src.dpId)) {
            it.rules.proper.containsAll(createdCookies)
            it.rules.properHex.containsAll(createdHexCookies)
            it.verifyRuleSectionsAreEmpty(["missing", "excess"])
            it.verifyHexRuleSectionsAreEmpty(["missingHex", "excessHex"])
            it.meters.proper*.meterId == originalMeterIds.sort()
            it.verifyMeterSectionsAreEmpty(["missing", "excess", "misconfigured"])
        }

        and: "Cleanup: Delete the flow"
        flowHelperV2.deleteFlow(flow.flowId)
    }

    def "Excess transitVlanRules/meters are synced from a new switch before connecting to the controller"() {
        given: "A switch with excess rules/meters and not connected to the controller"
        def sw = topology.getActiveSwitches().first()

        def producer = new KafkaProducer(producerProps)
        //pick a meter id which is not yet used on src switch
        def excessMeterId = ((MIN_FLOW_METER_ID..100) - northbound.getAllMeters(sw.dpId)
                                                                  .meterEntries*.meterId).first()
        producer.send(new ProducerRecord(speakerTopic, sw.dpId.toString(), buildMessage(
                FlowSpeakerData.builder()
                        .switchId(sw.dpId)
                        .ofVersion(OfVersion.of(sw.ofVersion))
                        .cookie(new Cookie(1))
                        .table(OfTable.EGRESS)
                        .priority(100)
                        .instructions(Instructions.builder().build())
                        .build()).toJson())).get()
        producer.send(new ProducerRecord(speakerTopic, sw.dpId.toString(), buildMessage(
                FlowSpeakerData.builder()
                        .switchId(sw.dpId)
                        .ofVersion(OfVersion.of(sw.ofVersion))
                        .cookie(new Cookie(2))
                        .table(OfTable.TRANSIT)
                        .priority(100)
                        .instructions(Instructions.builder().build())
                        .build()).toJson())).get()
        producer.send(new ProducerRecord(speakerTopic, sw.dpId.toString(), buildMessage([
                FlowSpeakerData.builder()
                        .switchId(sw.dpId)
                        .ofVersion(OfVersion.of(sw.ofVersion))
                        .cookie(new Cookie(3))
                        .table(OfTable.INPUT)
                        .priority(100)
                        .instructions(Instructions.builder().build())
                        .build(),
                MeterSpeakerData.builder()
                        .switchId(sw.dpId)
                        .ofVersion(OfVersion.of(sw.ofVersion))
                        .meterId(new MeterId(excessMeterId))
                        .rate(300)
                        .burst(300)
                        .flags(Sets.newHashSet(MeterFlag.PKTPS, MeterFlag.BURST, MeterFlag.STATS))
                        .build()]).toJson())).get()

        producer.flush()

        Wrappers.wait(WAIT_OFFSET) {
            verifyAll(northbound.validateSwitch(sw.dpId)) {
                it.rules.excess.size() == 3
                it.rules.excessHex.size() == 3
                it.verifyRuleSectionsAreEmpty(["proper", "missing"])
                it.verifyHexRuleSectionsAreEmpty(["properHex", "missingHex"])
                it.meters.excess.size() == 1
                it.verifyMeterSectionsAreEmpty(["missing", "proper", "misconfigured"])
            }
        }

        def blockData = switchHelper.knockoutSwitch(sw, RW)

        when: "Connect the switch to the controller"
        switchHelper.reviveSwitch(sw, blockData)

        then: "Excess meters/rules were synced during switch activation"
        verifyAll(northbound.validateSwitch(sw.dpId)) {
            it.verifyRuleSectionsAreEmpty(["missing", "excess", "proper"])
            it.verifyHexRuleSectionsAreEmpty(["missingHex", "excessHex", "properHex"])
        }
    }

    @Tags([HARDWARE])
    def "Excess vxlanRules/meters are synced from a new switch before connecting to the controller"() {
        given: "A switch with excess rules/meters and not connected to the controller"
        def sw = topology.getActiveSwitches().find { switchHelper.isVxlanEnabled(it.dpId) }

        def producer = new KafkaProducer(producerProps)
        //pick a meter id which is not yet used on src switch
        def excessMeterId = ((MIN_FLOW_METER_ID..100) - northbound.getAllMeters(sw.dpId)
                .meterEntries*.meterId).first()
        producer.send(new ProducerRecord(speakerTopic, sw.dpId.toString(), buildMessage(
                FlowSpeakerData.builder()
                        .switchId(sw.dpId)
                        .ofVersion(OfVersion.of(sw.ofVersion))
                        .cookie(buildCookie(1L))
                        .table(OfTable.EGRESS)
                        .priority(100)
                        .instructions(Instructions.builder().build())
                        .build()).toJson())).get()
        producer.send(new ProducerRecord(speakerTopic, sw.dpId.toString(), buildMessage(
                FlowSpeakerData.builder()
                        .switchId(sw.dpId)
                        .ofVersion(OfVersion.of(sw.ofVersion))
                        .cookie(buildCookie(2L))
                        .table(OfTable.TRANSIT)
                        .priority(100)
                        .instructions(Instructions.builder().build())
                        .build()).toJson())).get()
        producer.send(new ProducerRecord(speakerTopic, sw.dpId.toString(), buildMessage([
                FlowSpeakerData.builder()
                        .switchId(sw.dpId)
                        .ofVersion(OfVersion.of(sw.ofVersion))
                        .cookie(buildCookie(3L))
                        .table(OfTable.INPUT)
                        .priority(100)
                        .instructions(Instructions.builder().build())
                        .build(),
                MeterSpeakerData.builder()
                        .switchId(sw.dpId)
                        .ofVersion(OfVersion.of(sw.ofVersion))
                        .meterId(new MeterId(excessMeterId))
                        .rate(300)
                        .burst(300)
                        .flags(Sets.newHashSet(MeterFlag.PKTPS, MeterFlag.BURST, MeterFlag.STATS))
                        .build()]).toJson())).get()

        producer.flush()

        Wrappers.wait(WAIT_OFFSET) {
            verifyAll(northbound.validateSwitch(sw.dpId)) {
                it.rules.excess.size() == 3
                it.rules.excessHex.size() == 3
                it.verifyRuleSectionsAreEmpty(["proper", "missing"])
                it.verifyHexRuleSectionsAreEmpty(["properHex", "missingHex"])
                it.meters.excess.size() == 1
                it.verifyMeterSectionsAreEmpty(["missing", "proper", "misconfigured"])
            }
        }

        def blockData = switchHelper.knockoutSwitch(sw, RW)

        when: "Connect the switch to the controller"
        switchHelper.reviveSwitch(sw, blockData)

        then: "Excess meters/rules were synced during switch activation"
        verifyAll(northbound.validateSwitch(sw.dpId)) {
            it.verifyRuleSectionsAreEmpty(["missing", "excess", "proper"])
            it.verifyHexRuleSectionsAreEmpty(["missingHex", "excessHex", "properHex"])
        }
    }

    @Tags([SMOKE, SMOKE_SWITCHES, LOCKKEEPER])
    def "New connected switch is properly discovered with related ISLs in a reasonable time"() {
        setup: "Disconnect one of the switches and remove it from DB. Pretend this switch never existed"
        def sw = topology.activeSwitches.first()
        def isls = topology.getRelatedIsls(sw)
        /*in case supportedTransitEncapsulation == ["transit_vlan", "vxlan"]
        then after removing/adding the same switch this fields will be changed (["transit_vlan"])
        vxlan encapsulation is not set by default*/
        def initSwProps = switchHelper.getCachedSwProps(sw.dpId)
        initSwProps.supportedTransitEncapsulation
        def blockData = switchHelper.knockoutSwitch(sw, RW)
        Wrappers.wait(WAIT_OFFSET + discoveryTimeout) {
            assert northbound.getSwitch(sw.dpId).state == DEACTIVATED
            def allIsls = northbound.getAllLinks()
            isls.each { assert islUtils.getIslInfo(allIsls, it).get().actualState == IslChangeType.FAILED }
        }
        isls.each { northbound.deleteLink(islUtils.toLinkParameters(it), true) }
        Wrappers.retry(2) { northbound.deleteSwitch(sw.dpId, false) }

        when: "New switch connects"
        lockKeeper.reviveSwitch(sw, blockData)

        then: "Switch is activated"
        Wrappers.wait(WAIT_OFFSET / 2) {
            assert northbound.getSwitch(sw.dpId).state == ACTIVATED
        }

        and: "Related ISLs are discovered"
        Wrappers.wait(discoveryExhaustedInterval + WAIT_OFFSET / 2 + antiflapCooldown) {
            def allIsls = northbound.getAllLinks()
            isls.each {
                assert islUtils.getIslInfo(allIsls, it).get().actualState == IslChangeType.DISCOVERED
                assert islUtils.getIslInfo(allIsls, it.reversed).get().actualState == IslChangeType.DISCOVERED
            }
        }

        cleanup:
        initSwProps && switchHelper.updateSwitchProperties(sw, initSwProps)
    }
}
