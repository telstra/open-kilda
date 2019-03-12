package org.openkilda.functionaltests.spec.switches

import static org.junit.Assume.assumeTrue

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.SwitchHelper
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import groovy.transform.Memoized
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative

@Narrative("""This test suite check the meter validate request on a flow that contains 2+ switches.
description of fields:
- missing - those meters/rules, which is NOT exist on a switch, but it is exist in db
- misconfigured - those meters which have different value (on a switch and in db) for the same parameter
- excess - those meters/rules, which is exist on a switch, but it is NOT exist in db
- proper - meters/rules value is the same on a switch and in db
""")
class MetersValidateSimpleFlowSpec extends BaseSpecification {
    @Autowired
    SwitchHelper switchHelper

    def "Able to get meter validate information"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def defaultMetersSrcSwitch = northbound.getAllMeters(srcSwitch.dpId)
        def defaultMetersDstSwitch = northbound.getAllMeters(dstSwitch.dpId)
        def flow = flowHelper.addFlow(flowHelper.randomFlow(srcSwitch, dstSwitch))

        then: "One meter is automatically created on the both switches"
        def createdMetersIdSrcSwitch = getCreatedMetersId(srcSwitch.dpId, defaultMetersSrcSwitch)
        def createdMetersIdDstSwitch = getCreatedMetersId(dstSwitch.dpId, defaultMetersDstSwitch)
        def burstSizeSrcSwitch = switchHelper.getExpectedBurst(srcSwitch.dpId, flow.maximumBandwidth)
        def burstSizeDstSwitch = switchHelper.getExpectedBurst(dstSwitch.dpId, flow.maximumBandwidth)

        createdMetersIdSrcSwitch.size() == 1
        createdMetersIdDstSwitch.size() == 1

        and: "The correct info is stored in the validate meter response"
        def meterValidateInfoSrcSwitch = northbound.validateMeters(srcSwitch.dpId)
        def meterValidateInfoDstSwitch = northbound.validateMeters(dstSwitch.dpId)
        def createdCookieSrcSwitch = getCreatedCookies(srcSwitch.dpId, createdMetersIdSrcSwitch)
        def createdCookieDstSwitch = getCreatedCookies(dstSwitch.dpId, createdMetersIdDstSwitch)

        def createdCookies = createdCookieSrcSwitch + createdCookieDstSwitch

        meterValidateInfoSrcSwitch.meters.proper.collect { it.meterId }.containsAll(createdMetersIdSrcSwitch)
        meterValidateInfoDstSwitch.meters.proper.collect { it.meterId }.containsAll(createdMetersIdDstSwitch)

        meterValidateInfoSrcSwitch.meters.proper.collect { it.cookie }.containsAll(createdCookieSrcSwitch)
        meterValidateInfoDstSwitch.meters.proper.collect { it.cookie }.containsAll(createdCookieDstSwitch)

        [meterValidateInfoSrcSwitch, meterValidateInfoDstSwitch].each {
            it.meters.proper.each {
                assert it.rate == flow.maximumBandwidth
                assert it.flowId == flow.id
            }
        }

        meterValidateInfoSrcSwitch.meters.proper.burstSize[0] == burstSizeSrcSwitch
        meterValidateInfoDstSwitch.meters.proper.burstSize[0] == burstSizeDstSwitch

        [meterValidateInfoSrcSwitch, meterValidateInfoDstSwitch].each {
            it.meters.proper.every {
                ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            }
        }

        [srcSwitch, dstSwitch].each {
            checkNoMeterValidation(it.dpId, ["proper"])
        }

        and: "Created rules are stored in the proper section"
        [meterValidateInfoSrcSwitch, meterValidateInfoDstSwitch].each {
            it.rules.properRules.containsAll(createdCookies)
        }

        and: "The rest fields in the rule section are empty"
        [srcSwitch, dstSwitch].each {
            checkNoRuleMeterValidation(it.dpId, ["properRules"])
        }

        then: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        and: "Check that meter validate info is also deleted"
        [srcSwitch, dstSwitch].each {
            checkNoRuleMeterValidation(it.dpId)
            checkNoMeterValidation(it.dpId)
        }
    }

    def "Able to move meter info into the misconfigured section"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def defaultMetersSrcSwitch = northbound.getAllMeters(srcSwitch.dpId)
        def defaultMetersDstSwitch = northbound.getAllMeters(dstSwitch.dpId)
        def flow = flowHelper.addFlow(flowHelper.randomFlow(srcSwitch, dstSwitch))
        def createdMetersIdSrcSwitch = getCreatedMetersId(srcSwitch.dpId, defaultMetersSrcSwitch)
        def createdMetersIdDstSwitch = getCreatedMetersId(dstSwitch.dpId, defaultMetersDstSwitch)

        and: "Change bandwidth for the created flow directly in DB"
        def newBandwidth = northbound.getSwitchRules(srcSwitch.dpId).flowEntries*.instructions.goToMeter.max() + 100
        /** at this point meter is set for given flow. Now update flow bandwidth directly via DB,
         it is done just for moving meter from the proper section into the misconfigured*/
        database.updateFlowBandwidth(flow.id, newBandwidth)
        //at this point existing meters do not correspond with the flow

        then: "Meters info are moved into the misconfigured section"
        def meterValidateInfoSrcSwitch = northbound.validateMeters(srcSwitch.dpId)
        def meterValidateInfoDstSwitch = northbound.validateMeters(dstSwitch.dpId)
        def burstSizeSrcSwitch = switchHelper.getExpectedBurst(srcSwitch.dpId, flow.maximumBandwidth)
        def burstSizeDstSwitch = switchHelper.getExpectedBurst(dstSwitch.dpId, flow.maximumBandwidth)
        def createdCookieSrcSwitch = getCreatedCookies(srcSwitch.dpId, createdMetersIdSrcSwitch)
        def createdCookieDstSwitch = getCreatedCookies(dstSwitch.dpId, createdMetersIdDstSwitch)

        def createdCookies = createdCookieSrcSwitch + createdCookieDstSwitch

        meterValidateInfoSrcSwitch.meters.misconfigured.collect { it.meterId }.containsAll(createdMetersIdSrcSwitch)
        meterValidateInfoDstSwitch.meters.misconfigured.collect { it.meterId }.containsAll(createdMetersIdDstSwitch)

        meterValidateInfoSrcSwitch.meters.misconfigured.collect { it.cookie }.containsAll(createdCookieSrcSwitch)
        meterValidateInfoDstSwitch.meters.misconfigured.collect { it.cookie }.containsAll(createdCookieDstSwitch)

        [meterValidateInfoSrcSwitch, meterValidateInfoDstSwitch].each {
            it.meters.misconfigured.each {
                assert it.rate == flow.maximumBandwidth
                assert it.flowId == flow.id
            }
        }

        meterValidateInfoSrcSwitch.meters.misconfigured.burstSize[0] == burstSizeSrcSwitch
        meterValidateInfoDstSwitch.meters.misconfigured.burstSize[0] == burstSizeDstSwitch

        [meterValidateInfoSrcSwitch, meterValidateInfoDstSwitch].each {
            it.meters.misconfigured.every {
                ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            }
        }

        and: "Reason is specified why meter is misconfigured"
        [meterValidateInfoSrcSwitch, meterValidateInfoDstSwitch].each {
            it.meters.misconfigured.each {
                it.actual.rate == flow.maximumBandwidth
                it.expected.rate == newBandwidth
            }
        }

        and: "The rest fields are empty"
        [srcSwitch, dstSwitch].each {
            checkNoMeterValidation(it.dpId, ["misconfigured"])
        }

        and: "Created rules are still stored in the proper section"
        [meterValidateInfoSrcSwitch, meterValidateInfoDstSwitch].each {
            it.rules.properRules.containsAll(createdCookies)
        }

        when: "Restore correct bandwidth via DB"
        database.updateFlowBandwidth(flow.id, flow.maximumBandwidth)

        then: "Misconfigured meters are moved into the proper section"
        def meterValidateInfoSrcSwitchRestored = northbound.validateMeters(srcSwitch.dpId)
        def meterValidateInfoDstSwitchRestored = northbound.validateMeters(dstSwitch.dpId)

        meterValidateInfoSrcSwitchRestored.meters.proper.collect { it.meterId }.containsAll(createdMetersIdSrcSwitch)
        meterValidateInfoDstSwitchRestored.meters.proper.collect { it.meterId }.containsAll(createdMetersIdDstSwitch)

        [srcSwitch, dstSwitch].each {
            checkNoMeterValidation(it.dpId, ["proper"])
        }

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "Check that meter validate info is also deleted"
        [srcSwitch, dstSwitch].each {
            checkNoRuleMeterValidation(it.dpId)
            checkNoMeterValidation(it.dpId)
        }
    }

    def "Able to move meter info into the missing section"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def defaultMetersSrcSwitch = northbound.getAllMeters(srcSwitch.dpId)
        def defaultMetersDstSwitch = northbound.getAllMeters(dstSwitch.dpId)
        def flow = flowHelper.addFlow(flowHelper.randomFlow(srcSwitch, dstSwitch))
        def burstSizeSrcSwitch = switchHelper.getExpectedBurst(srcSwitch.dpId, flow.maximumBandwidth)
        def burstSizeDstSwitch = switchHelper.getExpectedBurst(dstSwitch.dpId, flow.maximumBandwidth)
        def createdMetersIdSrcSwitch = getCreatedMetersId(srcSwitch.dpId, defaultMetersSrcSwitch)
        def createdMetersIdDstSwitch = getCreatedMetersId(dstSwitch.dpId, defaultMetersDstSwitch)
        def createdCookieSrcSwitch = getCreatedCookies(srcSwitch.dpId, createdMetersIdSrcSwitch)
        def createdCookieDstSwitch = getCreatedCookies(dstSwitch.dpId, createdMetersIdDstSwitch)

        def createdCookies = createdCookieSrcSwitch + createdCookieDstSwitch

        and: "Remove created meter on the srcSwitch"
        northbound.deleteMeter(srcSwitch.dpId, createdMetersIdSrcSwitch[0])

        then: "Meters info/rules are moved into the missing section on the srcSwitch only"
        def meterValidateInfoSrcSwitch = northbound.validateMeters(srcSwitch.dpId)
        meterValidateInfoSrcSwitch.rules.missingRules.collect { it }.containsAll(createdCookieSrcSwitch)
        meterValidateInfoSrcSwitch.rules.properRules.collect { it }.containsAll(createdCookieDstSwitch)

        meterValidateInfoSrcSwitch.meters.missing.collect { it.meterId }.containsAll(createdMetersIdSrcSwitch)
        meterValidateInfoSrcSwitch.meters.missing.collect { it.cookie }.containsAll(createdCookieSrcSwitch)

        meterValidateInfoSrcSwitch.meters.missing.each {
            assert it.rate == flow.maximumBandwidth
            assert it.flowId == flow.id
            assert it.burstSize == burstSizeSrcSwitch
        }

        meterValidateInfoSrcSwitch.meters.missing.every { ["KBPS", "BURST", "STATS"].containsAll(it.flags) }

        and: "The rest sections are empty on the srcSwitch"
        checkNoMeterValidation(srcSwitch.dpId, ["missing"])
        checkNoRuleMeterValidation(srcSwitch.dpId, ["missingRules", "properRules"])

        and: "Meters info/rules are NOT moved into the missing section on the srcSwitch only"
        def meterValidateInfoDstSwitch = northbound.validateMeters(dstSwitch.dpId)
        meterValidateInfoDstSwitch.rules.properRules.collect { it }.containsAll(createdCookies)

        meterValidateInfoDstSwitch.meters.proper.collect { it.meterId }.containsAll(createdMetersIdDstSwitch)
        meterValidateInfoDstSwitch.meters.proper.collect { it.cookie }.containsAll(createdCookieDstSwitch)

        meterValidateInfoDstSwitch.meters.proper.each {
            assert it.rate == flow.maximumBandwidth
            assert it.flowId == flow.id
            assert it.burstSize == burstSizeDstSwitch
        }

        and: "The rest sections are empty on the dstSwitch"
        checkNoMeterValidation(dstSwitch.dpId, ["proper"])
        checkNoRuleMeterValidation(dstSwitch.dpId, ["properRules"])


        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "Check that meter validate info is also deleted"
        [srcSwitch, dstSwitch].each {
            checkNoRuleMeterValidation(it.dpId)
            checkNoMeterValidation(it.dpId)
        }
    }

    def "Able to move meter info into the excess section"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def defaultMetersSrcSwitch = northbound.getAllMeters(srcSwitch.dpId)
        def defaultMetersDstSwitch = northbound.getAllMeters(dstSwitch.dpId)
        def flow = flowHelper.addFlow(flowHelper.randomFlow(srcSwitch, dstSwitch))
        def burstSizeSrcSwitch = switchHelper.getExpectedBurst(srcSwitch.dpId, flow.maximumBandwidth)
        def burstSizeDstSwitch = switchHelper.getExpectedBurst(dstSwitch.dpId, flow.maximumBandwidth)
        def createdMetersIdSrcSwitch = getCreatedMetersId(srcSwitch.dpId, defaultMetersSrcSwitch)
        def createdMetersIdDstSwitch = getCreatedMetersId(dstSwitch.dpId, defaultMetersDstSwitch)
        def createdCookieSrcSwitch = getCreatedCookies(srcSwitch.dpId, createdMetersIdSrcSwitch)
        def createdCookieDstSwitch = getCreatedCookies(dstSwitch.dpId, createdMetersIdDstSwitch)

        and: "Update meterId for created flow directly via db"
        def newMeterId = 100
        database.updateFlowMeterId(flow.id, newMeterId)

        then: "Meters info is moved into the excess section on both switches"
        def meterValidateInfoSrcSwitch = northbound.validateMeters(srcSwitch.dpId)
        def meterValidateInfoDstSwitch = northbound.validateMeters(dstSwitch.dpId)

        [meterValidateInfoSrcSwitch, meterValidateInfoDstSwitch].each {
            it.meters.excess.each { assert it.rate == flow.maximumBandwidth }
        }

        meterValidateInfoSrcSwitch.meters.excess.collect { it.meterId }.containsAll(createdMetersIdSrcSwitch)
        meterValidateInfoDstSwitch.meters.excess.collect { it.meterId }.containsAll(createdMetersIdDstSwitch)

        meterValidateInfoSrcSwitch.meters.excess.burstSize[0] == burstSizeSrcSwitch
        meterValidateInfoDstSwitch.meters.excess.burstSize[0] == burstSizeDstSwitch

        [meterValidateInfoSrcSwitch, meterValidateInfoDstSwitch].each {
            it.meters.excess.every {
                ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            }
        }

        and: "Meters info is also stored in the missing section"
        meterValidateInfoSrcSwitch.meters.missing.each { assert it.meterId == newMeterId }
        meterValidateInfoSrcSwitch.meters.missing.collect { it.cookie }.containsAll(createdCookieSrcSwitch)

        meterValidateInfoSrcSwitch.meters.missing.each {
            assert it.rate == flow.maximumBandwidth
            assert it.flowId == flow.id
            assert it.burstSize == burstSizeSrcSwitch
        }

        meterValidateInfoDstSwitch.meters.missing.each { assert it.meterId == newMeterId }
        meterValidateInfoDstSwitch.meters.missing.collect { it.cookie }.containsAll(createdCookieDstSwitch)

        meterValidateInfoDstSwitch.meters.missing.each {
            assert it.rate == flow.maximumBandwidth
            assert it.flowId == flow.id
            assert it.burstSize == burstSizeDstSwitch
        }

        [meterValidateInfoSrcSwitch, meterValidateInfoDstSwitch].each {
            it.meters.missing.every {
                ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            }
        }

        and: "The rest fileds are empty on the both switches"
        [srcSwitch, dstSwitch].each {
            checkNoMeterValidation(it.dpId, ["missing", "excess"])
        }

        and: "Rules are still exist in the proper section"
        [srcSwitch, dstSwitch].each {
            checkNoRuleMeterValidation(it.dpId, ["properRules"])
        }

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        and: "Delete created meter on the both switches"
        createdMetersIdSrcSwitch.each { northbound.deleteMeter(srcSwitch.dpId, it) }
        createdMetersIdDstSwitch.each { northbound.deleteMeter(dstSwitch.dpId, it) }

        then: "Check that meter validate info is also deleted"
        [srcSwitch, dstSwitch].each {
            checkNoRuleMeterValidation(it.dpId)
            checkNoMeterValidation(it.dpId)
        }
    }

    def "Able to move rule info into the missing section"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def defaultMetersSrcSwitch = northbound.getAllMeters(srcSwitch.dpId)
        def defaultMetersDstSwitch = northbound.getAllMeters(dstSwitch.dpId)
        def flow = flowHelper.addFlow(flowHelper.randomFlow(srcSwitch, dstSwitch))
        def createdMetersIdSrcSwitch = getCreatedMetersId(srcSwitch.dpId, defaultMetersSrcSwitch)
        def createdMetersIdDstSwitch = getCreatedMetersId(dstSwitch.dpId, defaultMetersDstSwitch)
        def createdCookieSrcSwitch = getCreatedCookies(srcSwitch.dpId, createdMetersIdSrcSwitch)
        def createdCookieDstSwitch = getCreatedCookies(dstSwitch.dpId, createdMetersIdDstSwitch)

        def createdCookies = createdCookieSrcSwitch + createdCookieDstSwitch

        and: "Delete created rules on the srcSwitch"
        northbound.deleteSwitchRules(srcSwitch.dpId, DeleteRulesAction.IGNORE_DEFAULTS)

        then: "Rule info is moved into the missing section on the srcSwitch"
        def meterValidateInfoSrcSwitch = northbound.validateMeters(srcSwitch.dpId)
        meterValidateInfoSrcSwitch.rules.missingRules.collect { it }.containsAll(createdCookies)
        checkNoRuleMeterValidation(srcSwitch.dpId, ["missingRules"])

        and: "Rule info is NOT moved into the missing section on the dstSwitch"
        def meterValidateInfoDstSwitch = northbound.validateMeters(dstSwitch.dpId)
        meterValidateInfoDstSwitch.rules.properRules.collect { it }.containsAll(createdCookies)
        checkNoRuleMeterValidation(dstSwitch.dpId, ["properRules"])

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "Check that meter validate info is also deleted"
        [srcSwitch, dstSwitch].each {
            checkNoRuleMeterValidation(it.dpId)
            checkNoMeterValidation(it.dpId)
        }
    }

    def "Able to move rule info into the excess section"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def defaultMetersSrcSwitch = northbound.getAllMeters(srcSwitch.dpId)
        def defaultMetersDstSwitch = northbound.getAllMeters(dstSwitch.dpId)
        def flow = flowHelper.addFlow(flowHelper.randomFlow(srcSwitch, dstSwitch))
        def createdMetersIdSrcSwitch = getCreatedMetersId(srcSwitch.dpId, defaultMetersSrcSwitch)
        def createdMetersIdDstSwitch = getCreatedMetersId(dstSwitch.dpId, defaultMetersDstSwitch)
        def createdCookieSrcSwitch = getCreatedCookies(srcSwitch.dpId, createdMetersIdSrcSwitch)
        def createdCookieDstSwitch = getCreatedCookies(dstSwitch.dpId, createdMetersIdDstSwitch)

        def createdCookies = createdCookieSrcSwitch + createdCookieDstSwitch

        and: "Change cookies for the created flow directly in DB"
        def newFlowCookies = [createdCookies[0] + 1, createdCookies[1] + 1]
        /** at this point rules are set for given flow. Now update flow cookies directly via DB,
         it is done just for moving rules from the proper section into the excess section*/
        //TODO(andriidovhan)try to create excess rules directly via kafka, then remove the updateFlowSegmentCookie impl
        database.updateFlowCookie(flow.id, newFlowCookies[0], newFlowCookies[1])
        database.updateFlowSegmentCookie(flow.id, createdCookieSrcSwitch[0])
        database.updateFlowSegmentCookie(flow.id, createdCookieDstSwitch[0])

        then: "Rule validate info is moved into the correct sections on both switches"
        def meterValidateInfoSrcSwitch = northbound.validateMeters(srcSwitch.dpId)
        meterValidateInfoSrcSwitch.rules.excessRules.collect { it }.containsAll(createdCookieSrcSwitch)
        meterValidateInfoSrcSwitch.rules.missingRules.collect { it }.containsAll(newFlowCookies[0])

        def meterValidateInfoDstSwitch = northbound.validateMeters(dstSwitch.dpId)
        meterValidateInfoDstSwitch.rules.excessRules.collect { it }.containsAll(createdCookieDstSwitch)
        meterValidateInfoDstSwitch.rules.missingRules.collect { it }.containsAll(newFlowCookies[1])

        [srcSwitch, dstSwitch].each { checkNoRuleMeterValidation(it.dpId, ["missingRules", "excessRules"]) }

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        and: "Clean excess rules"
        //remove excess rule which is still exist on the both switches
        // srcSwitch -> createdCookieDstSwitch
        // dstSwitch -> createdCookieSrcSwitch
        northbound.deleteSwitchRules(srcSwitch.dpId, DeleteRulesAction.IGNORE_DEFAULTS)
        northbound.deleteSwitchRules(dstSwitch.dpId, DeleteRulesAction.IGNORE_DEFAULTS)

        then: "Check that meter validate info is also deleted"
        [srcSwitch, dstSwitch].each {
            checkNoRuleMeterValidation(it.dpId)
            checkNoMeterValidation(it.dpId)
        }
    }

    def "Able to get empty meter validate information from the intermediate switch(flow contain > 2 switches)"() {
        when: "Create an intermediate-switch flow"
        def flow = intermediateSwitchFlow()
        flowHelper.addFlow(flow)
        def flowPath = PathHelper.convert(northbound.getFlowPath(flow.id))

        then: "The intermediate switch does not contain any information about meter"
        checkNoMeterValidation(flowPath[1].switchId)

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "Check that meter validate info is also deleted"
        flowPath.each {
            if (switchHelper.getDescription(it.switchId).contains("OF_13")) {
                checkNoRuleMeterValidation(it.switchId)
                checkNoMeterValidation(it.switchId)
            }
        }
    }

    def intermediateSwitchFlow(boolean ensureAltPathsExist = false, boolean getAllPaths = false) {
        def flowWithPaths = getFlowWithPaths(3, Integer.MAX_VALUE, ensureAltPathsExist ? 1 : 0)
        return getAllPaths ? flowWithPaths : flowWithPaths[0]
    }

    def getFlowWithPaths(int minSwitchesInPath, int maxSwitchesInPath, int minAltPaths) {
        def allFlowPaths = []
        def switches = topology.getActiveSwitches()
        def switchPair = [switches, switches].combinations().findAll {
            src, dst -> src != dst
        }.unique {
            it.sort()
        }.find { src, dst ->
            allFlowPaths = database.getPaths(src.dpId, dst.dpId)*.path
            allFlowPaths.size() > minAltPaths && allFlowPaths.min {
                it.size()
            }.size() in (minSwitchesInPath..maxSwitchesInPath)
        } ?: assumeTrue("No suiting switches found", false)

        return [flowHelper.randomFlow(switchPair[0], switchPair[1]), allFlowPaths]
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