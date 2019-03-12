package org.openkilda.functionaltests.spec.switches

import static org.junit.Assume.assumeTrue
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.SwitchHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.messaging.error.MessageError
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import groovy.transform.Memoized
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Unroll

@Narrative("""This test suite check the switch validate feature on a single flow switch.
Description of fields:
- missing - those meters/rules, which are NOT present on a switch, but are present in db
- misconfigured - those meters which have different value (on a switch and in db) for the same parameter
- excess - those meters/rules, which are present on a switch, but are NOT present in db
- proper - meters/rules values are the same on a switch and in db
""")
class MetersValidationSingleSwFlowSpec extends BaseSpecification {
    @Autowired
    SwitchHelper switchHelper

    @Unroll
    def "Switch validation is able to store correct information on a #switchType switch in the proper section"() {
        assumeTrue("Unable to find required switches in topology", switches as boolean)

        setup: "Select a #switchType switch and retrieve default meters"
        def sw = switches.first()
        def defaultMeters = northbound.getAllMeters(sw.dpId)

        when: "Create a flow"
        def flow = flowHelper.addFlow(flowHelper.singleSwitchFlow(sw))
        def meterIds = getCreatedMetersId(sw.dpId, defaultMeters)
        def burstSize = switchHelper.getExpectedBurst(sw.dpId, flow.maximumBandwidth)

        then: "Two meters are automatically created."
        meterIds.size() == 2

        and: "The correct info is stored in the proper section"
        def switchValidateInfo = northbound.switchValidate(sw.dpId)
        switchValidateInfo.meters.proper.collect { it.meterId }.containsAll(meterIds)

        def createdCookies = getCreatedCookies(sw.dpId, meterIds)
        switchValidateInfo.meters.proper*.cookie.containsAll(createdCookies)

        switchValidateInfo.meters.proper.each {
            assert it.rate == flow.maximumBandwidth
            assert it.flowId == flow.id
            assert flagsOnSwitch.containsAll(it.flags)
            burstSizeIsCorrect(burstSize, it.burstSize)
        }

        meterSectionsAreEmpty(switchValidateInfo, ["missing", "misconfigured", "excess"])

        and: "Created rules are stored in the proper section"
        switchValidateInfo.rules.proper.containsAll(createdCookies)

        and: "The rest fields in the rule section are empty"
        ruleSectionsAreEmpty(switchValidateInfo, ["missing", "excess"])

        then: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        and: "Check that switch validate info is also deleted"
        Wrappers.wait(WAIT_OFFSET) {
            def switchValidateInfoAfterDelete = northbound.switchValidate(sw.dpId)
            ruleSectionsAreEmpty(switchValidateInfoAfterDelete)
            meterSectionsAreEmpty(switchValidateInfoAfterDelete)
        }

        where:
        switchType   | switches               | flagsOnSwitch
        "Centec"     | getCentecSwitches()    | ["KBPS", "BURST", "STATS"]
        "non-Centec" | getNonCentecSwitches() | ["KBPS", "BURST", "STATS"]
    }

    @Unroll
    def "Switch validation is able to detect meter info into the misconfigured section on a #switchType switch"() {
        assumeTrue("Unable to find required switches in topology", switches as boolean)

        setup: "Select a #switchType switch and retrieve default meters"
        def sw = switches.first()
        def defaultMeters = northbound.getAllMeters(sw.dpId)

        when: "Create a flow"
        def flow = flowHelper.addFlow(flowHelper.singleSwitchFlow(sw))
        def meterIds = getCreatedMetersId(sw.dpId, defaultMeters)
        def burstSize = switchHelper.getExpectedBurst(sw.dpId, flow.maximumBandwidth)

        and: "Change bandwidth for the created flow directly in DB"
        def newBandwidth = northbound.getSwitchRules(sw.dpId).flowEntries*.instructions.goToMeter.max() + 100
        /** at this point meters are set for given flow. Now update flow bandwidth directly via DB,
         it is done just for moving meters from the proper section into the misconfigured*/
        database.updateFlowBandwidth(flow.id, newBandwidth)
        //at this point existing meters do not correspond with the flow

        then: "Meters info are moved into the misconfigured section"
        def switchValidateInfo = northbound.switchValidate(sw.dpId)
        def createdCookies = getCreatedCookies(sw.dpId, meterIds)
        switchValidateInfo.meters.misconfigured*.meterId.containsAll(meterIds)

        switchValidateInfo.meters.misconfigured*.cookie.containsAll(createdCookies)
        switchValidateInfo.meters.misconfigured.each {
            assert it.rate == flow.maximumBandwidth
            assert it.flowId == flow.id
            assert flagsOnSwitch.containsAll(it.flags)
            burstSizeIsCorrect(burstSize, it.burstSize)
        }

        and: "Reason is specified why meters are misconfigured"
        switchValidateInfo.meters.misconfigured.each {
            it.actual.rate == flow.maximumBandwidth
            it.expected.rate == newBandwidth
        }

        and: "The rest fields are empty"
        meterSectionsAreEmpty(switchValidateInfo, ["missing", "proper", "excess"])

        and: "Created rules are still stored in the proper section"
        switchValidateInfo.rules.proper.containsAll(createdCookies)

        when: "Restore correct bandwidth via DB"
        database.updateFlowBandwidth(flow.id, flow.maximumBandwidth)

        then: "Misconfigured meters are moved into the proper section"
        def switchValidateInfoRestored = northbound.switchValidate(sw.dpId)
        switchValidateInfoRestored.meters.proper*.meterId.containsAll(meterIds)
        meterSectionsAreEmpty(switchValidateInfoRestored, ["missing", "misconfigured", "excess"])

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "Check that switch validate info is also deleted"
        Wrappers.wait(WAIT_OFFSET) {
            def switchValidateInfoAfterDelete = northbound.switchValidate(sw.dpId)
            ruleSectionsAreEmpty(switchValidateInfoAfterDelete)
            meterSectionsAreEmpty(switchValidateInfoAfterDelete)
        }

        where:
        switchType   | switches               | flagsOnSwitch
        "Centec"     | getCentecSwitches()    | ["KBPS", "BURST", "STATS"]
        "non-Centec" | getNonCentecSwitches() | ["KBPS", "BURST", "STATS"]
    }

    @Unroll
    def "Switch validation is able to detect meter info into the missing section on a #switchType switch"() {
        assumeTrue("Unable to find required switches in topology", switches as boolean)

        setup: "Select a #switchType switch and retrieve default meters"
        def sw = switches.first()
        def defaultMeters = northbound.getAllMeters(sw.dpId)

        when: "Create a flow"
        def flow = flowHelper.addFlow(flowHelper.singleSwitchFlow(sw))
        def meterIds = getCreatedMetersId(sw.dpId, defaultMeters)

        and: "Remove created meter"
        northbound.deleteMeter(sw.dpId, meterIds[0])
        northbound.deleteMeter(sw.dpId, meterIds[1])

        then: "Meters info/rules are moved into the missing section"
        def burstSize = switchHelper.getExpectedBurst(sw.dpId, flow.maximumBandwidth)
        def switchValidateInfo = northbound.switchValidate(sw.dpId)
        def createdCookies = getCreatedCookies(sw.dpId, meterIds)
        switchValidateInfo.rules.missing.collect { it }.containsAll(createdCookies)
        switchValidateInfo.meters.missing*.meterId.containsAll(meterIds)
        switchValidateInfo.meters.missing*.cookie.containsAll(createdCookies)
        switchValidateInfo.meters.missing.each {
            assert it.rate == flow.maximumBandwidth
            assert it.flowId == flow.id
            assert flagsOnSwitch.containsAll(it.flags)
            burstSizeIsCorrect(burstSize, it.burstSize)
        }

        and: "The rest fields are empty"
        ruleSectionsAreEmpty(switchValidateInfo, ["proper", "excess"])
        meterSectionsAreEmpty(switchValidateInfo, ["misconfigured", "proper", "excess"])

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "Check that switch validate info is also deleted"
        Wrappers.wait(WAIT_OFFSET) {
            def switchValidateInfoAfterDelete = northbound.switchValidate(sw.dpId)
            ruleSectionsAreEmpty(switchValidateInfoAfterDelete)
            meterSectionsAreEmpty(switchValidateInfoAfterDelete)
        }

        where:
        switchType   | switches               | flagsOnSwitch
        "Centec"     | getCentecSwitches()    | ["KBPS", "BURST", "STATS"]
        "non-Centec" | getNonCentecSwitches() | ["KBPS", "BURST", "STATS"]
    }

    @Unroll
    def "Switch validation is able to detect meter info into the excess section on a #switchType switch"() {
        assumeTrue("Unable to find required switches in topology", switches as boolean)

        setup: "Select a #switchType switch and retrieve default meters"
        def sw = switches.first()
        def defaultMeters = northbound.getAllMeters(sw.dpId)

        when: "Create a flow"
        def flow = flowHelper.addFlow(flowHelper.singleSwitchFlow(sw))
        def metersIds = getCreatedMetersId(sw.dpId, defaultMeters)
        def burstSize = switchHelper.getExpectedBurst(sw.dpId, flow.maximumBandwidth)

        and: "Update meterId for created flow directly via db"
        def newMeterId = 100
        database.updateFlowMeterId(flow.id, newMeterId)

        then: "Meters info are moved into the excess section"
        def switchValidateInfo = northbound.switchValidate(sw.dpId)
        switchValidateInfo.meters.excess.collect { it.meterId }.containsAll(metersIds)
        switchValidateInfo.meters.excess.each {
            assert it.rate == flow.maximumBandwidth
            assert flagsOnSwitch.containsAll(it.flags)
            burstSizeIsCorrect(burstSize, it.burstSize)
        }

        and: "Meters info is also stored in the missing section"
        def createdCookies = getCreatedCookies(sw.dpId, metersIds)
        switchValidateInfo.meters.missing.collect { it.cookie }.containsAll(createdCookies)
        switchValidateInfo.meters.missing.each {
            assert it.rate == flow.maximumBandwidth
            assert it.flowId == flow.id
            assert it.meterId == newMeterId
            assert flagsOnSwitch.containsAll(it.flags)
            burstSizeIsCorrect(burstSize, it.burstSize)
        }

        and: "Rules are still exist in the proper section"
        ruleSectionsAreEmpty(switchValidateInfo, ["missing", "excess"])

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        and: "Delete created meters on a switch"
        metersIds.each { northbound.deleteMeter(sw.dpId, it) }

        then: "Check that switch validate info is also deleted"
        Wrappers.wait(WAIT_OFFSET) {
            def switchValidateInfoAfterDelete = northbound.switchValidate(sw.dpId)
            ruleSectionsAreEmpty(switchValidateInfoAfterDelete)
            meterSectionsAreEmpty(switchValidateInfoAfterDelete)
        }

        where:
        switchType   | switches               | flagsOnSwitch
        "Centec"     | getCentecSwitches()    | ["KBPS", "BURST", "STATS"]
        "non-Centec" | getNonCentecSwitches() | ["KBPS", "BURST", "STATS"]
    }

    @Unroll
    def "Switch validation is able to detect rule info into the missing section on a #switchType switch"() {
        assumeTrue("Unable to find required switches in topology", switches as boolean)

        setup: "Select a #switchType switch and retrieve default meters"
        def sw = switches.first()
        def defaultMeters = northbound.getAllMeters(sw.dpId)

        when: "Create a flow"
        def flow = flowHelper.addFlow(flowHelper.singleSwitchFlow(sw))
        def metersIds = getCreatedMetersId(sw.dpId, defaultMeters)
        def createdCookies = getCreatedCookies(sw.dpId, metersIds)

        and: "Delete created rules"
        northbound.deleteSwitchRules(sw.dpId, DeleteRulesAction.IGNORE_DEFAULTS)

        then: "Rule info is moved into the missing section"
        def switchValidateInfo = northbound.switchValidate(sw.dpId)
        switchValidateInfo.rules.missing.containsAll(createdCookies)

        and: "The rest fields in the rule section are empty"
        ruleSectionsAreEmpty(switchValidateInfo, ["proper", "excess"])

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "Check that rules validate info is also deleted"
        Wrappers.wait(WAIT_OFFSET) {
            def switchValidateInfoAfterDelete = northbound.switchValidate(sw.dpId)
            println(sw.dpId)
            ruleSectionsAreEmpty(switchValidateInfoAfterDelete)
            meterSectionsAreEmpty(switchValidateInfoAfterDelete)
        }

        where:
        switchType   | switches
        "Centec"     | getCentecSwitches()
        "non-Centec" | getNonCentecSwitches()
    }

    @Unroll
    def "Switch validation is able to detect rule info into the excess section on a #switchType switch"() {
        assumeTrue("Unable to find required switches in topology", switches as boolean)

        setup: "Select a #switchType switch and retrieve default meters"
        def sw = switches.first()
        def defaultMeters = northbound.getAllMeters(sw.dpId)

        when: "Create a flow"
        def flow = flowHelper.addFlow(flowHelper.singleSwitchFlow(sw))
        def metersIds = getCreatedMetersId(sw.dpId, defaultMeters)
        def createdCookies = getCreatedCookies(sw.dpId, metersIds)

        and: "Change cookies for the created flow directly in DB"
        def newFlowCookies = [createdCookies[0] + 1, createdCookies[1] + 1]
        /** at this point rules are set for given flow. Now update flow cookie directly via DB,
         it is done just for moving rules from the proper section into the excess section*/
        database.updateFlowCookie(flow.id, newFlowCookies[0], newFlowCookies[1])

        then: "Rule validate info(original cookies) is moved into the excess section"
        def switchValidateInfo = northbound.switchValidate(sw.dpId)
        switchValidateInfo.rules.excess.collect { it }.containsAll(createdCookies)

        and: "Rule validate info(new cookies) is moved into the excess section"
        switchValidateInfo.rules.missing.collect { it }.containsAll(newFlowCookies)
        ruleSectionsAreEmpty(switchValidateInfo, ["proper"])

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "Check that rules validate info is also deleted"
        Wrappers.wait(WAIT_OFFSET) {
            def switchValidateInfoAfterDelete = northbound.switchValidate(sw.dpId)
            ruleSectionsAreEmpty(switchValidateInfoAfterDelete)
            meterSectionsAreEmpty(switchValidateInfoAfterDelete)
        }

        where:
        switchType   | switches
        "Centec"     | getCentecSwitches()
        "non-Centec" | getNonCentecSwitches()
    }

    def "Not able to get the switch validate info on a NOT supported switch"() {
        given: "Not supported switch"
        def switches = topology.activeSwitches.findAll { it.ofVersion == "OF_12" }
        assumeTrue("Unable to find required switches in topology", switches as boolean)
        def sw = switches.first()

        when: "Try to invoke the switch validate request"
        northbound.switchValidate(sw.dpId)

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

    void meterSectionsAreEmpty(switchValidateInfo,
                               List<String> sections = ["missing", "misconfigured", "proper", "excess"]) {
        sections.each {
            assert switchValidateInfo.meters."$it".empty
        }
    }

    void ruleSectionsAreEmpty(switchValidateInfo,
                              List<String> sections = ["missing", "proper", "excess"]) {
        sections.each {
            assert switchValidateInfo.rules."$it".empty
        }
    }

    void burstSizeIsCorrect(expected, actual) {
        assert Math.abs(expected - actual) <= 1
    }
}