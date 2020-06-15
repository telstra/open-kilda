package org.openkilda.functionaltests.spec.switches

import static org.junit.Assume.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.VIRTUAL
import static org.openkilda.model.MeterId.MAX_SYSTEM_RULE_METER_ID
import static org.openkilda.model.MeterId.MIN_FLOW_METER_ID
import static org.openkilda.testing.Constants.NON_EXISTENT_FLOW_ID
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.SwitchHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.Message
import org.openkilda.messaging.command.CommandMessage
import org.openkilda.messaging.command.flow.BaseInstallFlow
import org.openkilda.messaging.command.flow.InstallEgressFlow
import org.openkilda.messaging.command.flow.InstallFlowForSwitchManagerRequest
import org.openkilda.messaging.command.flow.InstallIngressFlow
import org.openkilda.messaging.command.flow.InstallTransitFlow
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.model.FlowEncapsulationType
import org.openkilda.model.FlowEndpoint
import org.openkilda.model.OutputVlanType
import org.openkilda.model.cookie.Cookie

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value

class SwitchActivationSpec extends HealthCheckSpecification {
    @Value("#{kafkaTopicsConfig.getSpeakerTopic()}")
    String speakerTopic
    @Autowired
    @Qualifier("kafkaProducerProperties")
    Properties producerProps
    @Autowired
    SwitchHelper switchHelper

    @Tags(VIRTUAL)
    def "Missing flow rules/meters are installed on a new switch before connecting to the controller"() {
        given: "A switch with missing flow rules/meters and not connected to the controller"
        def switchPair = topologyHelper.getNeighboringSwitchPair()
        def flow = flowHelperV2.randomFlow(switchPair)
        flowHelperV2.addFlow(flow)

        def originalMeterIds = northbound.getAllMeters(switchPair.src.dpId).meterEntries*.meterId
        assert originalMeterIds.size() == 1 + switchPair.src.defaultMeters.size()
        def createdCookies = northbound.getSwitchRules(switchPair.src.dpId).flowEntries.findAll {
            !Cookie.isDefaultRule(it.cookie)
        }*.cookie
        def amountOfFlowRules = northbound.getSwitchProperties(switchPair.src.dpId).multiTable ? 3 : 2
        assert createdCookies.size() == amountOfFlowRules

        def nonDefaultMeterIds = originalMeterIds.findAll({it > MAX_SYSTEM_RULE_METER_ID})
        northbound.deleteMeter(switchPair.src.dpId, nonDefaultMeterIds[0])
        northbound.deleteSwitchRules(switchPair.src.dpId, DeleteRulesAction.IGNORE_DEFAULTS)
        Wrappers.wait(WAIT_OFFSET) {
            verifyAll(northbound.validateSwitch(switchPair.src.dpId)) {
                it.rules.missing.containsAll(createdCookies)
                switchHelper.verifyRuleSectionsAreEmpty(it, ["proper", "excess"])
                it.meters.missing.size() == 1
                switchHelper.verifyMeterSectionsAreEmpty(it, ["proper", "misconfigured", "excess"])
            }
        }

        def blockData = switchHelper.knockoutSwitch(switchPair.src, mgmtFlManager)

        when: "Connect the switch to the controller"
        switchHelper.reviveSwitch(switchPair.src, blockData)

        then: "Missing flow rules/meters were synced during switch activation"
        verifyAll(northbound.validateSwitch(switchPair.src.dpId)) {
            it.rules.proper.containsAll(createdCookies)
            switchHelper.verifyRuleSectionsAreEmpty(it, ["missing", "excess"])
            it.meters.proper*.meterId == originalMeterIds.sort()
            switchHelper.verifyMeterSectionsAreEmpty(it, ["missing", "excess", "misconfigured"])
        }

        and: "Cleanup: Delete the flow"
        flowHelperV2.deleteFlow(flow.flowId)
    }

    @Tags(VIRTUAL)
    def "Excess rules/meters are synced from a new switch before connecting to the controller"() {
        given: "A switch with excess rules/meters and not connected to the controller"
        def sw = topology.getActiveSwitches().find { it.virtual }
        assumeTrue("Unable to find required switches in topology", sw as boolean)

        def producer = new KafkaProducer(producerProps)
        //pick a meter id which is not yet used on src switch
        def excessMeterId = ((MIN_FLOW_METER_ID..100) - northbound.getAllMeters(sw.dpId)
                                                                  .meterEntries*.meterId).first()
        producer.send(new ProducerRecord(speakerTopic, sw.dpId.toString(), buildMessage(
                new InstallEgressFlow(UUID.randomUUID(), NON_EXISTENT_FLOW_ID, 1L, sw.dpId, 1, 2, 1,
                        FlowEncapsulationType.TRANSIT_VLAN, 1, 0,
                        OutputVlanType.REPLACE, false, new FlowEndpoint(sw.dpId, 17))).toJson()))

        producer.send(new ProducerRecord(speakerTopic, sw.dpId.toString(), buildMessage(
                new InstallTransitFlow(UUID.randomUUID(), NON_EXISTENT_FLOW_ID, 2L, sw.dpId, 3, 4, 1,
                        FlowEncapsulationType.TRANSIT_VLAN, false)).toJson()))

        producer.send(new ProducerRecord(speakerTopic, sw.dpId.toString(), buildMessage(
                new InstallIngressFlow(UUID.randomUUID(), NON_EXISTENT_FLOW_ID, 3L, sw.dpId, 5, 6, 1, 0, 1,
                        FlowEncapsulationType.TRANSIT_VLAN,
                        OutputVlanType.REPLACE, 300, excessMeterId,
                        sw.dpId, false, false, false)).toJson()))
        producer.flush()

        Wrappers.wait(WAIT_OFFSET) {
            verifyAll(northbound.validateSwitch(sw.dpId)) {
                it.rules.excess.size() == 3
                switchHelper.verifyRuleSectionsAreEmpty(it, ["proper", "missing"])
                it.meters.excess.size() == 1
                switchHelper.verifyMeterSectionsAreEmpty(it, ["missing", "proper", "misconfigured"])
            }
        }

        def blockData = switchHelper.knockoutSwitch(sw, mgmtFlManager)

        when: "Connect the switch to the controller"
        switchHelper.reviveSwitch(sw, blockData)

        then: "Excess meters/rules were synced during switch activation"
        verifyAll(northbound.validateSwitch(sw.dpId)) {
            switchHelper.verifyRuleSectionsAreEmpty(it, ["missing", "excess", "proper"])
        }
    }

    @Tags([VIRTUAL])
    def "New connected switch is properly discovered with related ISLs in a reasonable time"() {
        setup: "Disconnect one of the switches and remove it from DB. Pretend this switch never existed"
        def sw = topology.activeSwitches.first()
        def isls = topology.getRelatedIsls(sw)
        def blockData = switchHelper.knockoutSwitch(sw, mgmtFlManager, true)
        isls.each { northbound.deleteLink(islUtils.toLinkParameters(it)) }
        Wrappers.wait(WAIT_OFFSET) {
            def allLinks = northbound.getAllLinks()
            isls.every { !islUtils.getIslInfo(allLinks, it).present }
        }
        northbound.deleteSwitch(sw.dpId, false)

        when: "New switch connects"
        lockKeeper.reviveSwitch(sw, blockData)

        then: "Switch is activated"
        Wrappers.wait(WAIT_OFFSET / 2) {
            assert northbound.getSwitch(sw.dpId).state == SwitchChangeType.ACTIVATED
        }

        and: "Related ISLs are discovered"
        Wrappers.wait(discoveryInterval + WAIT_OFFSET / 2 + antiflapCooldown) {
            def allIsls = northbound.getAllLinks()
            isls.each {
                assert islUtils.getIslInfo(allIsls, it).get().state == IslChangeType.DISCOVERED
                assert islUtils.getIslInfo(allIsls, it.reversed).get().state == IslChangeType.DISCOVERED
            }
        }
    }

    private static Message buildMessage(final BaseInstallFlow commandData) {
        InstallFlowForSwitchManagerRequest data = new InstallFlowForSwitchManagerRequest(commandData)
        return new CommandMessage(data, System.currentTimeMillis(), UUID.randomUUID().toString(), null)
    }
}
