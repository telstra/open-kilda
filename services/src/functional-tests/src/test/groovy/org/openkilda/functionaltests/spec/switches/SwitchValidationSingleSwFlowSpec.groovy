package org.openkilda.functionaltests.spec.switches

import static org.junit.Assume.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.model.MeterId.MAX_SYSTEM_RULE_METER_ID
import static org.openkilda.model.MeterId.MIN_FLOW_METER_ID
import static org.openkilda.testing.Constants.NON_EXISTENT_FLOW_ID
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.SwitchHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.Message
import org.openkilda.messaging.command.CommandData
import org.openkilda.messaging.command.CommandMessage
import org.openkilda.messaging.command.flow.InstallEgressFlow
import org.openkilda.messaging.command.flow.InstallIngressFlow
import org.openkilda.messaging.command.flow.InstallTransitFlow
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.messaging.error.MessageError
import org.openkilda.model.Cookie
import org.openkilda.model.FlowEncapsulationType
import org.openkilda.model.MeterId
import org.openkilda.model.OutputVlanType
import org.openkilda.model.SwitchId
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import groovy.transform.Memoized
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Ignore
import spock.lang.Narrative
import spock.lang.Unroll


@Narrative("""This test suite checks the switch validate feature on a single flow switch.
Description of fields:
- missing - those meters/rules, which are NOT present on a switch, but are present in db
- misconfigured - those meters which have different value (on a switch and in db) for the same parameter
- excess - those meters/rules, which are present on a switch, but are NOT present in db
- proper - meters/rules values are the same on a switch and in db
""")
class SwitchValidationSingleSwFlowSpec extends BaseSpecification {
    @Value("#{kafkaTopicsConfig.getSpeakerFlowTopic()}")
    String flowTopic
    @Autowired
    @Qualifier("kafkaProducerProperties")
    Properties producerProps
    @Autowired
    SwitchHelper switchHelper

    @Unroll
    @Tags([TOPOLOGY_DEPENDENT, SMOKE])
    def "Switch validation is able to store correct information on a #switchType switch in the 'proper' section"() {
        assumeTrue("Unable to find required switches in topology", switches as boolean)

        setup: "Select a #switchType switch and retrieve default meters"
        def sw = switches.first()

        when: "Create a flow"
        def flow = flowHelper.addFlow(flowHelper.singleSwitchFlow(sw))
        def meterIds = getCreatedMeterIds(sw.dpId)
        Long burstSize = switchHelper.getExpectedBurst(sw.dpId, flow.maximumBandwidth)

        then: "Two meters are automatically created."
        meterIds.size() == 2

        and: "The correct info is stored in the 'proper' section"
        def switchValidateInfo = northbound.validateSwitch(sw.dpId)
        switchValidateInfo.meters.proper.collect { it.meterId }.containsAll(meterIds)

        def createdCookies = getCookiesWithMeter(sw.dpId)
        switchValidateInfo.meters.proper*.cookie.containsAll(createdCookies)

        switchValidateInfo.meters.proper.each {
            assert it.rate == flow.maximumBandwidth
            assert it.flowId == flow.id
            assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            verifyBurstSizeIsCorrect(burstSize, it.burstSize)
        }

        switchHelper.verifyMeterSectionsAreEmpty(switchValidateInfo, ["missing", "misconfigured", "excess"])

        and: "Created rules are stored in the 'proper' section"
        switchValidateInfo.rules.proper.containsAll(createdCookies)

        and: "The rest fields in the 'rule' section are empty"
        switchHelper.verifyRuleSectionsAreEmpty(switchValidateInfo, ["missing", "excess"])

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "Check that the switch validate request returns empty sections"
        Wrappers.wait(WAIT_OFFSET) {
            def switchValidateInfoAfterDelete = northbound.validateSwitch(sw.dpId)
            switchHelper.verifyRuleSectionsAreEmpty(switchValidateInfoAfterDelete)
            switchHelper.verifyMeterSectionsAreEmpty(switchValidateInfoAfterDelete)
        }

        where:
        switchType   | switches
        "Centec"     | getCentecSwitches()
        "non-Centec" | getNonCentecSwitches()
    }

    @Unroll
    @Tags([TOPOLOGY_DEPENDENT])
    def "Switch validation is able to detect meter info into the 'misconfigured' section on a #switchType switch"() {
        assumeTrue("Unable to find required switches in topology", switches as boolean)

        setup: "Select a #switchType switch and retrieve default meters"
        def sw = switches.first()

        when: "Create a flow"
        def flow = flowHelper.addFlow(flowHelper.singleSwitchFlow(sw))
        def meterIds = getCreatedMeterIds(sw.dpId)
        Long burstSize = switchHelper.getExpectedBurst(sw.dpId, flow.maximumBandwidth)

        and: "Change bandwidth for the created flow directly in DB"
        Long newBandwidth = flow.maximumBandwidth + 100
        /** at this point meters are set for given flow. Now update flow bandwidth directly via DB,
         it is done just for moving meters from the 'proper' section into the 'misconfigured'*/
        database.updateFlowBandwidth(flow.id, newBandwidth)
        //at this point existing meters do not correspond with the flow

        then: "Meters info is moved into the 'misconfigured' section"
        def switchValidateInfo = northbound.validateSwitch(sw.dpId)
        def createdCookies = getCookiesWithMeter(sw.dpId)
        switchValidateInfo.meters.misconfigured.meterId.size() == 2
        switchValidateInfo.meters.misconfigured*.meterId.containsAll(meterIds)

        switchValidateInfo.meters.misconfigured*.cookie.containsAll(createdCookies)
        switchValidateInfo.meters.misconfigured.each {
            assert it.rate == flow.maximumBandwidth
            assert it.flowId == flow.id
            assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            verifyBurstSizeIsCorrect(burstSize, it.burstSize)
        }

        and: "Reason is specified why meters are misconfigured"
        switchValidateInfo.meters.misconfigured.each {
            assert it.actual.rate == flow.maximumBandwidth
            assert it.expected.rate == newBandwidth
        }

        and: "The rest fields are empty"
        switchHelper.verifyMeterSectionsAreEmpty(switchValidateInfo, ["missing", "proper", "excess"])

        and: "Created rules are still stored in the 'proper' section"
        switchValidateInfo.rules.proper.containsAll(createdCookies)

        when: "Restore correct bandwidth via DB"
        database.updateFlowBandwidth(flow.id, flow.maximumBandwidth)

        then: "Misconfigured meters are moved into the 'proper' section"
        def switchValidateInfoRestored = northbound.validateSwitch(sw.dpId)
        switchValidateInfoRestored.meters.proper*.meterId.containsAll(meterIds)
        switchHelper.verifyMeterSectionsAreEmpty(switchValidateInfoRestored, ["missing", "misconfigured", "excess"])

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "Check that the switch validate request returns empty sections"
        Wrappers.wait(WAIT_OFFSET) {
            def switchValidateInfoAfterDelete = northbound.validateSwitch(sw.dpId)
            switchHelper.verifyRuleSectionsAreEmpty(switchValidateInfoAfterDelete)
            switchHelper.verifyMeterSectionsAreEmpty(switchValidateInfoAfterDelete)
        }

        where:
        switchType   | switches
        "Centec"     | getCentecSwitches()
        "non-Centec" | getNonCentecSwitches()
    }

    @Unroll
    @Tags([TOPOLOGY_DEPENDENT])
    def "Switch validation is able to detect meter info into the 'missing' section on a #switchType switch"() {
        assumeTrue("Unable to find required switches in topology", switches as boolean)

        setup: "Select a #switchType switch and retrieve default meters"
        def sw = switches.first()

        when: "Create a flow"
        def flow = flowHelper.addFlow(flowHelper.singleSwitchFlow(sw))
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
            assert it.rate == flow.maximumBandwidth
            assert it.flowId == flow.id
            assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            verifyBurstSizeIsCorrect(burstSize, it.burstSize)
        }

        and: "The rest fields are empty"
        switchHelper.verifyRuleSectionsAreEmpty(switchValidateInfo, ["proper", "excess"])
        switchHelper.verifyMeterSectionsAreEmpty(switchValidateInfo, ["misconfigured", "proper", "excess"])

        // TODO(andriidovhan) add synchronizeSwitch and check that rule inflo is moved back into the 'proper' section
        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "Check that the switch validate request returns empty sections"
        Wrappers.wait(WAIT_OFFSET) {
            def switchValidateInfoAfterDelete = northbound.validateSwitch(sw.dpId)
            switchHelper.verifyRuleSectionsAreEmpty(switchValidateInfoAfterDelete)
            switchHelper.verifyMeterSectionsAreEmpty(switchValidateInfoAfterDelete)
        }

        where:
        switchType   | switches
        "Centec"     | getCentecSwitches()
        "non-Centec" | getNonCentecSwitches()
    }

    @Unroll
    @Tags([TOPOLOGY_DEPENDENT])
    def "Switch validation is able to detect meter info into the 'excess' section on a #switchType switch"() {
        assumeTrue("Unable to find required switches in topology", switches as boolean)

        setup: "Select a #switchType switch and retrieve default meters"
        def sw = switches.first()

        when: "Create a flow"
        def flow = flowHelper.addFlow(flowHelper.singleSwitchFlow(sw))
        def metersIds = getCreatedMeterIds(sw.dpId)
        Long burstSize = switchHelper.getExpectedBurst(sw.dpId, flow.maximumBandwidth)

        then: "Rules and meters are created"
        def swValidateInfo = northbound.validateSwitch(sw.dpId)
        swValidateInfo.meters.proper.meterId.size() == 2
        swValidateInfo.rules.proper.size() == 2

        when: "Update meterId for created flow directly via db"
        MeterId newMeterId = new MeterId(100)
        database.updateFlowMeterId(flow.id, newMeterId)

        then: "Origin meters are moved into the 'excess' section"
        def switchValidateInfo = northbound.validateSwitch(sw.dpId)
        switchValidateInfo.meters.excess.meterId.size() == 2
        switchValidateInfo.meters.excess.collect { it.meterId }.containsAll(metersIds)
        switchValidateInfo.meters.excess.each {
            assert it.rate == flow.maximumBandwidth
            assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            verifyBurstSizeIsCorrect(burstSize, it.burstSize)
        }

        and: "Updated meters are stored in the 'missing' section"
        def createdCookies = getCookiesWithMeter(sw.dpId)
        switchValidateInfo.meters.missing.collect { it.cookie }.containsAll(createdCookies)
        switchValidateInfo.meters.missing.each {
            assert it.rate == flow.maximumBandwidth
            assert it.flowId == flow.id
            assert it.meterId == newMeterId.value
            assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            verifyBurstSizeIsCorrect(burstSize, it.burstSize)
        }

        and: "Rules still exist in the 'proper' section"
        switchHelper.verifyRuleSectionsAreEmpty(switchValidateInfo, ["missing", "excess"])

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        and: "Delete excess meters"
        metersIds.each { northbound.deleteMeter(sw.dpId, it) }

        then: "Check that the switch validate request returns empty sections"
        Wrappers.wait(WAIT_OFFSET) {
            def switchValidateInfoAfterDelete = northbound.validateSwitch(sw.dpId)
            switchHelper.verifyRuleSectionsAreEmpty(switchValidateInfoAfterDelete)
            switchHelper.verifyMeterSectionsAreEmpty(switchValidateInfoAfterDelete)
        }

        where:
        switchType   | switches
        "Centec"     | getCentecSwitches()
        "non-Centec" | getNonCentecSwitches()
    }

    @Unroll
    @Tags([TOPOLOGY_DEPENDENT])
    def "Switch validation is able to detect rule info into the 'missing' section on a #switchType switch"() {
        assumeTrue("Unable to find required switches in topology", switches as boolean)

        setup: "Select a #switchType switch and retrieve default meters"
        def sw = switches.first()

        when: "Create a flow"
        def flow = flowHelper.addFlow(flowHelper.singleSwitchFlow(sw))
        def createdCookies = getCookiesWithMeter(sw.dpId)

        and: "Delete created rules"
        northbound.deleteSwitchRules(sw.dpId, DeleteRulesAction.IGNORE_DEFAULTS)

        then: "Rule info is moved into the 'missing' section"
        def switchValidateInfo = northbound.validateSwitch(sw.dpId)
        switchValidateInfo.rules.missing.containsAll(createdCookies)

        and: "The rest fields in the 'rule' section are empty"
        switchHelper.verifyRuleSectionsAreEmpty(switchValidateInfo, ["proper", "excess"])

        // TODO(andriidovhan) add synchronizeSwitch and check that rule inflo is moved back into the 'proper' section
        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "Check that the switch validate request returns empty sections"
        Wrappers.wait(WAIT_OFFSET) {
            def switchValidateInfoAfterDelete = northbound.validateSwitch(sw.dpId)
            switchHelper.verifyRuleSectionsAreEmpty(switchValidateInfoAfterDelete)
            switchHelper.verifyMeterSectionsAreEmpty(switchValidateInfoAfterDelete)
        }

        where:
        switchType   | switches
        "Centec"     | getCentecSwitches()
        "non-Centec" | getNonCentecSwitches()
    }

    @Unroll
    @Tags([TOPOLOGY_DEPENDENT])
    def "Switch validation is able to detect rule info into the 'excess' section on a #switchType switch"() {
        assumeTrue("Unable to find required switches in topology", switches as boolean)

        setup: "Select a #switchType switch and no meters/rules exist on a switch"
        def sw = switches.first()
        def switchValidateInfoInitState = northbound.validateSwitch(sw.dpId)
        switchHelper.verifyRuleSectionsAreEmpty(switchValidateInfoInitState)
        switchHelper.verifyMeterSectionsAreEmpty(switchValidateInfoInitState)

        when: "Create excess rules directly via kafka"
        Long fakeBandwidth = 333
        Long burstSize = switchHelper.getExpectedBurst(sw.dpId, fakeBandwidth)
        def producer = new KafkaProducer(producerProps)
        //pick a meter id which is not yet used on src switch
        def excessMeterId = ((MIN_FLOW_METER_ID..100) - northbound.getAllMeters(sw.dpId)
                .meterEntries*.meterId).first()
        producer.send(new ProducerRecord(flowTopic, sw.dpId.toString(), buildMessage(
                new InstallEgressFlow(UUID.randomUUID(), NON_EXISTENT_FLOW_ID, 1L, sw.dpId, 1, 2, 1,
                        FlowEncapsulationType.TRANSIT_VLAN, 1, OutputVlanType.REPLACE, sw.dpId)).toJson()))
        producer.send(new ProducerRecord(flowTopic, sw.dpId.toString(), buildMessage(
                new InstallTransitFlow(UUID.randomUUID(), NON_EXISTENT_FLOW_ID, 2L, sw.dpId, 3, 4, 3,
                FlowEncapsulationType.TRANSIT_VLAN, sw.dpId)).toJson()))
        producer.send(new ProducerRecord(flowTopic, sw.dpId.toString(), buildMessage(
                new InstallIngressFlow(UUID.randomUUID(), NON_EXISTENT_FLOW_ID, 3L, sw.dpId, 5, 6, 5, 3,
                        FlowEncapsulationType.TRANSIT_VLAN,
                        OutputVlanType.REPLACE, fakeBandwidth, excessMeterId, sw.dpId)).toJson()))

        then: "System detects created rules as excess rules"
        //excess egress/ingress/transit rules are not added yet
        //they will be added after the next line
        def switchValidateInfo
        Wrappers.wait(WAIT_OFFSET) {
            switchValidateInfo = northbound.validateSwitch(sw.dpId)
            //excess egress/ingress/transit rules are added
            switchValidateInfo.rules.excess.size() == 3
        }

        and: "System detects one meter as excess"
        switchValidateInfo.meters.excess.size() == 1
        switchValidateInfo.meters.excess.each {
            assert it.rate == fakeBandwidth
            assert it.meterId == excessMeterId
            assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            verifyBurstSizeIsCorrect(burstSize, it.burstSize)
        }

        // TODO(andriidovhan) add synchronizeSwitch and check that rule inflo is moved back into the 'proper' section
        and: "Cleanup: delete the excess rules"
        northbound.deleteSwitchRules(sw.dpId, DeleteRulesAction.IGNORE_DEFAULTS)
        northbound.deleteMeter(sw.dpId, excessMeterId)
        Wrappers.wait(WAIT_OFFSET) {
            def switchValidateInfoAfterDelete = northbound.validateSwitch(sw.dpId)
            switchHelper.verifyRuleSectionsAreEmpty(switchValidateInfoAfterDelete)
            switchHelper.verifyMeterSectionsAreEmpty(switchValidateInfoAfterDelete)
        }

        cleanup:
        producer && producer.close()

        where:
        switchType   | switches
        "Centec"     | getCentecSwitches()
        "non-Centec" | getNonCentecSwitches()
    }
    @Ignore("This is not implemented yet.")
    def "Not able to get the switch validate info on a NOT supported switch"() {
        given: "Not supported switch"
        def sw = topology.activeSwitches.find { it.ofVersion == "OF_12" }
        assumeTrue("Unable to find required switches in topology", sw as boolean)

        when: "Try to invoke the switch validate request"
        northbound.validateSwitch(sw.dpId)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        exc.rawStatusCode == 400
        exc.responseBodyAsString.to(MessageError).errorMessage ==
                "Meters are not supported on switch $sw.dpId because of OF version OF_12"
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
        return northbound.getAllMeters(switchId).meterEntries.findAll {
            it.meterId > MAX_SYSTEM_RULE_METER_ID
        }*.meterId
    }

    List<Long> getCookiesWithMeter(SwitchId switchId) {
        return northbound.getSwitchRules(switchId).flowEntries.findAll {
            !Cookie.isDefaultRule(it.cookie) && it.instructions.goToMeter
        }*.cookie
    }

    void verifyBurstSizeIsCorrect(Long expected, Long actual) {
        assert Math.abs(expected - actual) <= 1
    }

    private static Message buildMessage(final CommandData data) {
        return new CommandMessage(data, System.currentTimeMillis(), UUID.randomUUID().toString(), null);
    }
}
