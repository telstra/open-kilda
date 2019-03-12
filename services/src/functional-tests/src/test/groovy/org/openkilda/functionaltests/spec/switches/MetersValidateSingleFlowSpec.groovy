package org.openkilda.functionaltests.spec.switches

import static org.junit.Assume.assumeTrue

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.SwitchHelper
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.messaging.error.MessageError
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import groovy.transform.Memoized
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Unroll

@Narrative("""This test suite check the meter validate request on a single flow switch.
description of fields:
- missing - those meters/rules, which is NOT exist on a switch, but it is exist in db
- misconfigured - those meters which have different value (on a switch and in db) for the same parameter
- excess - those meters/rules, which is exist on a switch, but it is NOT exist in db
- proper - meters/rules value is the same on a switch and in db
""")
class MetersValidateSingleFlowSpec extends BaseSpecification {
    @Autowired
    SwitchHelper switchHelper

    @Unroll
    def "Able to get meter validate information on a #switchType switch"() {
        assumeTrue("Unable to find required switches in topology", switches as boolean)

        setup: "Select a #switchType switch and retrieve default meters"
        getNonCentecSwitches()
        def sw = switches.first()
        def defaultMeters = northbound.getAllMeters(sw.dpId)

        when: "Create a flow"
        def flow = flowHelper.addFlow(flowHelper.singleSwitchFlow(sw))
        def createdMetersId = getCreatedMetersId(sw.dpId, defaultMeters)
        def burstSize = switchHelper.getExpectedBurst(sw.dpId, flow.maximumBandwidth)

        then: "Two meters are automatically created."
        createdMetersId.size() == 2

        and: "The correct info is stored in the validate meter response"
        def meterValidateInfo = northbound.validateMeters(sw.dpId)
        meterValidateInfo.meters.proper.collect { it.meterId }.containsAll(createdMetersId)

        def createdCookies = getCreatedCookies(sw.dpId, createdMetersId)
        meterValidateInfo.meters.proper.collect { it.cookie }.containsAll(createdCookies)

        meterValidateInfo.meters.proper.each {
            assert it.rate == flow.maximumBandwidth
            assert it.flowId == flow.id
            assert it.burstSize == burstSize
        }
        meterValidateInfo.meters.proper.every { flagsOnSwitch.containsAll(it.flags) }

        checkNoMeterValidation(sw.dpId, ["proper"])

        and: "Created rules are stored in the proper section"
        meterValidateInfo.rules.properRules.containsAll(createdCookies)

        and: "The rest fileds in the rule section are empty"
        checkNoRuleMeterValidation(sw.dpId, ["properRules"])

        then: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        and: "Check that meter validate info is also deleted"
        checkNoRuleMeterValidation(sw.dpId)
        checkNoMeterValidation(sw.dpId)

        where:
        switchType   | switches               | flagsOnSwitch
        "Centec"     | getCentecSwitches()    | ["KBPS", "BURST", "STATS"]
        "non-Centec" | getNonCentecSwitches() | ["KBPS", "BURST", "STATS"]
    }

    @Unroll
    def "Able to move meter info into the misconfigured section on a #switchType switch"() {
        assumeTrue("Unable to find required switches in topology", switches as boolean)

        setup: "Select a #switchType switch and retrieve default meters"
        def sw = switches.first()
        def defaultMeters = northbound.getAllMeters(sw.dpId)

        when: "Create a flow"
        def flow = flowHelper.addFlow(flowHelper.singleSwitchFlow(sw))
        def createdMetersId = getCreatedMetersId(sw.dpId, defaultMeters)
        def burstSize = switchHelper.getExpectedBurst(sw.dpId, flow.maximumBandwidth)

        and: "Change bandwidth for the created flow directly in DB"
        def newBandwidth = northbound.getSwitchRules(sw.dpId).flowEntries*.instructions.goToMeter.max() + 100
        /** at this point meters are set for given flow. Now update flow bandwidth directly via DB,
         it is done just for moving meters from the proper section into the misconfigured*/
        database.updateFlowBandwidth(flow.id, newBandwidth)
        //at this point existing meters do not correspond with the flow

        then: "Meters info are moved into the misconfigured section"
        def meterValidateInfo = northbound.validateMeters(sw.dpId)
        def createdCookies = getCreatedCookies(sw.dpId, createdMetersId)
        meterValidateInfo.meters.misconfigured.collect { it.meterId }.containsAll(createdMetersId)

        meterValidateInfo.meters.misconfigured.collect { it.cookie }.containsAll(createdCookies)
        meterValidateInfo.meters.misconfigured.each {
            assert it.rate == flow.maximumBandwidth
            assert it.flowId == flow.id
            assert it.burstSize == burstSize
        }
        meterValidateInfo.meters.misconfigured.every { flagsOnSwitch.containsAll(it.flags) }

        and: "Reason is specified why meters are misconfigured"
        meterValidateInfo.meters.misconfigured.each {
            it.actual.rate == flow.maximumBandwidth
            it.expected.rate == newBandwidth
        }

        and: "The rest fields are empty"
        checkNoMeterValidation(sw.dpId, ["misconfigured"])

        and: "Created rules are still stored in the proper section"
        meterValidateInfo.rules.properRules.containsAll(createdCookies)

        when: "Restore correct bandwidth via DB"
        database.updateFlowBandwidth(flow.id, flow.maximumBandwidth)

        then: "Misconfigured meters are moved into the proper section"
        def meterValidateInfoRestored = northbound.validateMeters(sw.dpId)
        meterValidateInfoRestored.meters.proper.collect { it.meterId }.containsAll(createdMetersId)
        checkNoMeterValidation(sw.dpId, ["proper"])

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "Check that meter validate info is also deleted"
        checkNoRuleMeterValidation(sw.dpId)
        checkNoMeterValidation(sw.dpId)

        where:
        switchType   | switches               | flagsOnSwitch
        "Centec"     | getCentecSwitches()    | ["KBPS", "BURST", "STATS"]
        "non-Centec" | getNonCentecSwitches() | ["KBPS", "BURST", "STATS"]
    }

    @Unroll
    def "Able to move meter info into the missing section on a #switchType switch"() {
        assumeTrue("Unable to find required switches in topology", switches as boolean)

        setup: "Select a #switchType switch and retrieve default meters"
        def sw = switches.first()
        def defaultMeters = northbound.getAllMeters(sw.dpId)

        when: "Create a flow"
        def flow = flowHelper.addFlow(flowHelper.singleSwitchFlow(sw))
        def createdMetersId = getCreatedMetersId(sw.dpId, defaultMeters)
        def burstSize = switchHelper.getExpectedBurst(sw.dpId, flow.maximumBandwidth)

        and: "Remove created meter"
        northbound.deleteMeter(sw.dpId, createdMetersId[0])
        northbound.deleteMeter(sw.dpId, createdMetersId[1])

        then: "Meters info/rules are moved into the missing section"
        def meterValidateInfo = northbound.validateMeters(sw.dpId)
        def createdCookies = getCreatedCookies(sw.dpId, createdMetersId)
        meterValidateInfo.rules.missingRules.collect { it }.containsAll(createdCookies)
        meterValidateInfo.meters.missing.collect { it.meterId }.containsAll(createdMetersId)
        meterValidateInfo.meters.missing.collect { it.cookie }.containsAll(createdCookies)
        meterValidateInfo.meters.missing.each {
            assert it.rate == flow.maximumBandwidth
            assert it.flowId == flow.id
            assert it.burstSize == burstSize
        }
        meterValidateInfo.meters.missing.every { flagsOnSwitch.containsAll(it.flags) }

        and: "The rest sections are empty"
        checkNoMeterValidation(sw.dpId, ["missing"])
        checkNoRuleMeterValidation(sw.dpId, ["missingRules"])

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "Check that meter validate info is also deleted"
        checkNoRuleMeterValidation(sw.dpId)
        checkNoMeterValidation(sw.dpId)


        where:
        switchType   | switches               | flagsOnSwitch
        "Centec"     | getCentecSwitches()    | ["KBPS", "BURST", "STATS"]
        "non-Centec" | getNonCentecSwitches() | ["KBPS", "BURST", "STATS"]
    }

    @Unroll
    def "Able to move meter info into the excess section on a #switchType switch"() {
        assumeTrue("Unable to find required switches in topology", switches as boolean)

        setup: "Select a #switchType switch and retrieve default meters"
        def sw = switches.first()
        def defaultMeters = northbound.getAllMeters(sw.dpId)

        when: "Create a flow"
        def flow = flowHelper.addFlow(flowHelper.singleSwitchFlow(sw))
        def createdMetersId = getCreatedMetersId(sw.dpId, defaultMeters)
        def burstSize = switchHelper.getExpectedBurst(sw.dpId, flow.maximumBandwidth)

        and: "Update meterId for created flow directly via db"
        def newMeterId = 100
        database.updateFlowMeterId(flow.id, newMeterId)

        then: "Meters info are moved into the excess section"
        def meterValidateInfo = northbound.validateMeters(sw.dpId)
        meterValidateInfo.meters.excess.collect { it.meterId }.containsAll(createdMetersId)
        meterValidateInfo.meters.excess.each {
            assert it.rate == flow.maximumBandwidth
            assert it.burstSize == burstSize
        }
        meterValidateInfo.meters.excess.every { flagsOnSwitch.containsAll(it.flags) }

        and: "Meters info is also stored in the missing section"
        def createdCookies = getCreatedCookies(sw.dpId, createdMetersId)
        meterValidateInfo.meters.missing.collect { it.cookie }.containsAll(createdCookies)
        meterValidateInfo.meters.missing.each {
            assert it.rate == flow.maximumBandwidth
            assert it.flowId == flow.id
            assert it.meterId == newMeterId
            assert it.burstSize == burstSize
        }
        meterValidateInfo.meters.missing.every { flagsOnSwitch.containsAll(it.flags) }

        and: "Rules are still exist in the proper section"
        checkNoRuleMeterValidation(sw.dpId, ["properRules"])

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        and: "Delete created meters on a switch"
        createdMetersId.each { northbound.deleteMeter(sw.dpId, it) }

        then: "Check that meter validate info is also deleted"
        checkNoRuleMeterValidation(sw.dpId)
        checkNoMeterValidation(sw.dpId)

        where:
        switchType   | switches               | flagsOnSwitch
        "Centec"     | getCentecSwitches()    | ["KBPS", "BURST", "STATS"]
        "non-Centec" | getNonCentecSwitches() | ["KBPS", "BURST", "STATS"]
    }

    @Unroll
    def "Able to move rule info into the missing section on a #switchType switch"() {
        assumeTrue("Unable to find required switches in topology", switches as boolean)

        setup: "Select a #switchType switch and retrieve default meters"
        def sw = switches.first()
        def defaultMeters = northbound.getAllMeters(sw.dpId)

        when: "Create a flow"
        def flow = flowHelper.addFlow(flowHelper.singleSwitchFlow(sw))
        def createdMetersId = getCreatedMetersId(sw.dpId, defaultMeters)
        def createdCookies = getCreatedCookies(sw.dpId, createdMetersId)

        and: "Delete created rules"
        northbound.deleteSwitchRules(sw.dpId, DeleteRulesAction.IGNORE_DEFAULTS)

        then: "Rule info is moved into the missing section"
        def meterValidateInfo = northbound.validateMeters(sw.dpId)
        meterValidateInfo.rules.missingRules.collect { it }.containsAll(createdCookies)

        and: "The rest fields in the rule section are empty"
        checkNoRuleMeterValidation(sw.dpId, ["missingRules"])

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "Check that rules validate info is also deleted"
        checkNoRuleMeterValidation(sw.dpId)
        checkNoMeterValidation(sw.dpId)

        where:
        switchType   | switches
        "Centec"     | getCentecSwitches()
        "non-Centec" | getNonCentecSwitches()
    }

    @Unroll
    def "Able to move rule info into the excess section on a #switchType switch"() {
        assumeTrue("Unable to find required switches in topology", switches as boolean)

        setup: "Select a #switchType switch and retrieve default meters"
        def sw = switches.first()
        def defaultMeters = northbound.getAllMeters(sw.dpId)

        when: "Create a flow"
        def flow = flowHelper.addFlow(flowHelper.singleSwitchFlow(sw))
        def createdMetersId = getCreatedMetersId(sw.dpId, defaultMeters)
        def createdCookies = getCreatedCookies(sw.dpId, createdMetersId)

        and: "Change cookies for the created flow directly in DB"
        def newFlowCookies = [createdCookies[0] + 1, createdCookies[1] + 1]
        /** at this point rules are set for given flow. Now update flow cookie directly via DB,
         it is done just for moving rules from the proper section into the excess section*/
        database.updateFlowCookie(flow.id, newFlowCookies[0], newFlowCookies[1])

        then: "Rule validate info(original cookies) is moved into the excess section"
        def meterValidateInfo = northbound.validateMeters(sw.dpId)
        meterValidateInfo.rules.excessRules.collect { it }.containsAll(createdCookies)

        and: "Rule validate info(new cookies) is moved into the excess section"
        meterValidateInfo.rules.missingRules.collect { it }.containsAll(newFlowCookies)
        checkNoRuleMeterValidation(sw.dpId, ["excessRules", "missingRules"])

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "Check that rules validate info is also deleted"
        checkNoRuleMeterValidation(sw.dpId)
        checkNoMeterValidation(sw.dpId)

        where:
        switchType   | switches
        "Centec"     | getCentecSwitches()
        "non-Centec" | getNonCentecSwitches()
    }

    def "Not able to get meter validate information on a NOT supported switch"() {
        given: "Not supported switch"
        def switches = topology.activeSwitches.findAll { it.ofVersion == "OF_12" }
        assumeTrue("Unable to find required switches in topology", switches as boolean)
        def sw = switches.first()

        when: "Try to validate meter"
        northbound.getAllMeters(sw.dpId)

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

    @Memoized
    List<Switch> getSwitchNotSupportedMeter() {
        topology.activeSwitches.findAll { it.ofVersion == "OF_12" }
    }

    List<Integer> getCreatedMetersId(switchId, defaultMeters) {
        northbound.getAllMeters(switchId).meterEntries.findAll {
            !defaultMeters.meterEntries*.meterId.contains(it.meterId)
        }.collect { it.meterId }
    }

    List<Integer> getCreatedCookies(switchId, createdMetersId) {
        def createdCookies = []
        createdMetersId.each { id ->
            northbound.getSwitchRules(switchId).flowEntries.each {
                if (it.instructions.goToMeter == id) {
                    createdCookies << it.cookie
                }
            }
        }
        return createdCookies
    }

    void checkNoMeterValidation(switchId, List<String> excludeSection = null) {
        def listOfSections = ["missing", "misconfigured", "proper", "excess"]
        excludeSection.each { listOfSections.remove(it) }

        listOfSections.each {
            assert northbound.validateMeters(switchId).meters."$it".size() == 0
        }
    }

    void checkNoRuleMeterValidation(switchId, List<String> excludeSection = null) {
        def listOfSections = ["missingRules", "properRules", "excessRules"]
        excludeSection.each { listOfSections.remove(it) }

        listOfSections.each {
            assert northbound.validateMeters(switchId).rules."$it".size() == 0
        }
    }
}