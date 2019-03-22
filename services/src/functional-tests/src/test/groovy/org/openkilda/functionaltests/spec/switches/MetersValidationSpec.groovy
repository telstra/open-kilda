package org.openkilda.functionaltests.spec.switches

import static org.junit.Assume.assumeTrue
import static org.openkilda.model.MeterId.MAX_SYSTEM_RULE_METER_ID
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.PathHelper
import org.openkilda.functionaltests.helpers.SwitchHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.model.Cookie
import org.openkilda.model.SwitchId
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import groovy.transform.Memoized
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Narrative

@Narrative("""This test suite checks the switch validate feature on a flow that contains 2+ switches.
Description of fields:
- missing - those meters/rules, which are NOT present on a switch, but are present in db
- misconfigured - those meters which have different value (on a switch and in db) for the same parameter
- excess - those meters/rules, which are present on a switch, but are NOT present in db
- proper - meters/rules values are the same on a switch and in db
""")
class MetersValidationSpec extends BaseSpecification {
    @Autowired
    SwitchHelper switchHelper

    def "Switch validation is able to store correct information about a flow in the 'proper' section"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelper.addFlow(flowHelper.randomFlow(srcSwitch, dstSwitch))

        then: "One meter is automatically created on the src and dst switches"
        def srcSwitchCreatedMeterIds = createdMeterIds(srcSwitch.dpId)
        def dstSwitchCreatedMeterIds = createdMeterIds(dstSwitch.dpId)
        srcSwitchCreatedMeterIds.size() == 1
        dstSwitchCreatedMeterIds.size() == 1

        and: "The correct info is stored in the validate meter response"
        def srcSwitchValidateInfo = northbound.switchValidate(srcSwitch.dpId)
        def dstSwitchValidateInfo = northbound.switchValidate(dstSwitch.dpId)
        def srcSwitchCreatedCookies = cookiesWithMeter(srcSwitch.dpId)
        def dstSwitchCreatedCookies = cookiesWithMeter(dstSwitch.dpId)

        srcSwitchValidateInfo.meters.proper*.meterId.containsAll(srcSwitchCreatedMeterIds)
        dstSwitchValidateInfo.meters.proper*.meterId.containsAll(dstSwitchCreatedMeterIds)

        srcSwitchValidateInfo.meters.proper*.cookie.containsAll(srcSwitchCreatedCookies)
        dstSwitchValidateInfo.meters.proper*.cookie.containsAll(dstSwitchCreatedCookies)

        [srcSwitchValidateInfo, dstSwitchValidateInfo].each {
            it.meters.proper.each {
                assert it.rate == flow.maximumBandwidth
                assert it.flowId == flow.id
                assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            }
        }

        def srcSwitchBurstSize = switchHelper.getExpectedBurst(srcSwitch.dpId, flow.maximumBandwidth)
        def dstSwitchBurstSize = switchHelper.getExpectedBurst(dstSwitch.dpId, flow.maximumBandwidth)
        srcSwitchValidateInfo.meters.proper*.burstSize[0] == srcSwitchBurstSize
        dstSwitchValidateInfo.meters.proper*.burstSize[0] == dstSwitchBurstSize


        and: "The rest fields in the 'meter' section are empty"
        [srcSwitchValidateInfo, dstSwitchValidateInfo].each {
            meterSectionsAreEmpty(it, ["missing", "misconfigured", "excess"])
        }

        and: "Created rules are stored in the 'proper' section"
        def createdCookies = srcSwitchCreatedCookies + dstSwitchCreatedCookies
        [srcSwitchValidateInfo, dstSwitchValidateInfo].each {
            it.rules.proper.containsAll(createdCookies)
        }

        and: "The rest fields in the 'rule' section are empty"
        [srcSwitchValidateInfo, dstSwitchValidateInfo].each {
            ruleSectionsAreEmpty(it, ["missing", "excess"])
        }

        then: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        and: "Check that switch validate info is also deleted"
        Wrappers.wait(WAIT_OFFSET) {
            def srcSwitchValidateInfoAfterDelete = northbound.switchValidate(srcSwitch.dpId)
            def dstSwitchValidateInfoAfterDelete = northbound.switchValidate(dstSwitch.dpId)
            [srcSwitchValidateInfoAfterDelete, dstSwitchValidateInfoAfterDelete].each {
                ruleSectionsAreEmpty(it)
                meterSectionsAreEmpty(it)
            }
        }
    }

    def "Switch validation is able to detect meter info into the 'misconfigured' section"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelper.addFlow(flowHelper.randomFlow(srcSwitch, dstSwitch))
        def srcSwitchCreatedMeterIds = createdMeterIds(srcSwitch.dpId)
        def dstSwitchCreatedMeterIds = createdMeterIds(dstSwitch.dpId)

        and: "Change bandwidth for the created flow directly in DB"
        def newBandwidth = northbound.getSwitchRules(srcSwitch.dpId).flowEntries*.instructions.goToMeter.max() + 100
        /** at this point meter is set for given flow. Now update flow bandwidth directly via DB,
         it is done just for moving meter from the 'proper' section into the 'misconfigured'*/
        database.updateFlowBandwidth(flow.id, newBandwidth)
        //at this point existing meters do not correspond with the flow

        then: "Meters info are moved into the 'misconfigured' section"
        def srcSwitchValidateInfo = northbound.switchValidate(srcSwitch.dpId)
        def dstSwitchValidateInfo = northbound.switchValidate(dstSwitch.dpId)
        def srcSwitchCreatedCookies = cookiesWithMeter(srcSwitch.dpId)
        def dstSwitchCreatedCookies = cookiesWithMeter(dstSwitch.dpId)

        def createdCookies = srcSwitchCreatedCookies + dstSwitchCreatedCookies

        srcSwitchValidateInfo.meters.misconfigured*.meterId.containsAll(srcSwitchCreatedMeterIds)
        dstSwitchValidateInfo.meters.misconfigured*.meterId.containsAll(dstSwitchCreatedMeterIds)

        srcSwitchValidateInfo.meters.misconfigured*.cookie.containsAll(srcSwitchCreatedCookies)
        dstSwitchValidateInfo.meters.misconfigured*.cookie.containsAll(dstSwitchCreatedCookies)

        [srcSwitchValidateInfo, dstSwitchValidateInfo].each {
            it.meters.misconfigured.each {
                assert it.rate == flow.maximumBandwidth
                assert it.flowId == flow.id
                assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            }
        }

        def srcSwitchBurstSize = switchHelper.getExpectedBurst(srcSwitch.dpId, flow.maximumBandwidth)
        def dstSwitchBurstSize = switchHelper.getExpectedBurst(dstSwitch.dpId, flow.maximumBandwidth)
        srcSwitchValidateInfo.meters.misconfigured*.burstSize[0] == srcSwitchBurstSize
        dstSwitchValidateInfo.meters.misconfigured*.burstSize[0] == dstSwitchBurstSize

        and: "Reason is specified why meter is misconfigured"
        [srcSwitchValidateInfo, dstSwitchValidateInfo].each {
            it.meters.misconfigured.each {
                it.actual.rate == flow.maximumBandwidth
                it.expected.rate == newBandwidth
            }
        }

        and: "The rest fields of 'meter' section are empty"
        [srcSwitchValidateInfo, dstSwitchValidateInfo].each {
            meterSectionsAreEmpty(it, ["missing", "proper", "excess"])
        }

        and: "Created rules are still stored in the 'proper' section"
        [srcSwitchValidateInfo, dstSwitchValidateInfo].each {
            it.rules.proper.containsAll(createdCookies)
        }

        when: "Restore correct bandwidth via DB"
        database.updateFlowBandwidth(flow.id, flow.maximumBandwidth)

        then: "Misconfigured meters are moved into the 'proper' section"
        def srcSwitchValidateInfoRestored = northbound.switchValidate(srcSwitch.dpId)
        def dstSwitchValidateInfoRestored = northbound.switchValidate(dstSwitch.dpId)

        srcSwitchValidateInfoRestored.meters.proper*.meterId.containsAll(srcSwitchCreatedMeterIds)
        dstSwitchValidateInfoRestored.meters.proper*.meterId.containsAll(dstSwitchCreatedMeterIds)

        [srcSwitchValidateInfoRestored, dstSwitchValidateInfoRestored].each {
            meterSectionsAreEmpty(it, ["missing", "misconfigured", "excess"])
        }

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "Check that switch validate info is also deleted"
        Wrappers.wait(WAIT_OFFSET) {
            def srcSwitchValidateInfoAfterDelete = northbound.switchValidate(srcSwitch.dpId)
            def dstSwitchValidateInfoAfterDelete = northbound.switchValidate(dstSwitch.dpId)
            [srcSwitchValidateInfoAfterDelete, dstSwitchValidateInfoAfterDelete].each {
                ruleSectionsAreEmpty(it)
                meterSectionsAreEmpty(it)
            }
        }
    }

    def "Switch validation is able to detect meter info into the 'missing' section"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelper.addFlow(flowHelper.randomFlow(srcSwitch, dstSwitch))
        def srcSwitchCreatedMeterIds = createdMeterIds(srcSwitch.dpId)
        def dstSwitchCreatedMeterIds = createdMeterIds(dstSwitch.dpId)

        and: "Remove created meter on the srcSwitch"
        northbound.deleteMeter(srcSwitch.dpId, srcSwitchCreatedMeterIds[0])

        then: "Meters info/rules are moved into the 'missing' section on the srcSwitch only"
        def srcSwitchCreatedCookies = cookiesWithMeter(srcSwitch.dpId)
        def dstSwitchCreatedCookies = cookiesWithMeter(dstSwitch.dpId)

        def srcSwitchValidateInfo = northbound.switchValidate(srcSwitch.dpId)
        srcSwitchValidateInfo.rules.missing.containsAll(srcSwitchCreatedCookies)
        srcSwitchValidateInfo.rules.proper.containsAll(dstSwitchCreatedCookies)

        srcSwitchValidateInfo.meters.missing*.meterId.containsAll(srcSwitchCreatedMeterIds)
        srcSwitchValidateInfo.meters.missing*.cookie.containsAll(srcSwitchCreatedCookies)

        def srcSwitchBurstSize = switchHelper.getExpectedBurst(srcSwitch.dpId, flow.maximumBandwidth)
        srcSwitchValidateInfo.meters.missing.each {
            assert it.rate == flow.maximumBandwidth
            assert it.flowId == flow.id
            assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            burstSizeIsCorrect(srcSwitchBurstSize, it.burstSize)
        }

        and: "The rest sections are empty on the srcSwitch"
        meterSectionsAreEmpty(srcSwitchValidateInfo, ["misconfigured", "proper", "excess"])
        ruleSectionsAreEmpty(srcSwitchValidateInfo, ["excess"])

        and: "Meters info/rules are NOT moved into the 'missing' section on the dstSwitch"
        def createdCookies = srcSwitchCreatedCookies + dstSwitchCreatedCookies
        def dstSwitchValidateInfo = northbound.switchValidate(dstSwitch.dpId)
        dstSwitchValidateInfo.rules.proper.containsAll(createdCookies)

        dstSwitchValidateInfo.meters.proper*.meterId.containsAll(dstSwitchCreatedMeterIds)
        dstSwitchValidateInfo.meters.proper*.cookie.containsAll(dstSwitchCreatedCookies)

        def dstSwitchBurstSize = switchHelper.getExpectedBurst(dstSwitch.dpId, flow.maximumBandwidth)
        dstSwitchValidateInfo.meters.proper.each {
            assert it.rate == flow.maximumBandwidth
            assert it.flowId == flow.id
            assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            burstSizeIsCorrect(dstSwitchBurstSize, it.burstSize)
        }

        and: "The rest sections are empty on the dstSwitch"
        meterSectionsAreEmpty(dstSwitchValidateInfo, ["missing", "misconfigured", "excess"])
        ruleSectionsAreEmpty(dstSwitchValidateInfo, ["missing", "excess"])

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "Check that switch validate info is also deleted"
        Wrappers.wait(WAIT_OFFSET) {
            def srcSwitchValidateInfoAfterDelete = northbound.switchValidate(srcSwitch.dpId)
            def dstSwitchValidateInfoAfterDelete = northbound.switchValidate(dstSwitch.dpId)
            [srcSwitchValidateInfoAfterDelete, dstSwitchValidateInfoAfterDelete].each {
                ruleSectionsAreEmpty(it)
                meterSectionsAreEmpty(it)
            }
        }
    }

    def "Switch validation is able to detect meter info into the 'excess' section"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelper.addFlow(flowHelper.randomFlow(srcSwitch, dstSwitch))
        def srcSwitchCreatedMeterIds = createdMeterIds(srcSwitch.dpId)
        def dstSwitchCreatedMeterIds = createdMeterIds(dstSwitch.dpId)

        and: "Update meterId for created flow directly via db"
        def newMeterId = 100
        database.updateFlowMeterId(flow.id, newMeterId)

        then: "Meters info is moved into the 'excess' section on the src and dst switches"
        def srcSwitchValidateInfo = northbound.switchValidate(srcSwitch.dpId)
        def dstSwitchValidateInfo = northbound.switchValidate(dstSwitch.dpId)

        [srcSwitchValidateInfo, dstSwitchValidateInfo].each {
            it.meters.excess.each {
                assert it.rate == flow.maximumBandwidth
                assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            }
        }

        srcSwitchValidateInfo.meters.excess*.meterId.containsAll(srcSwitchCreatedMeterIds)
        dstSwitchValidateInfo.meters.excess*.meterId.containsAll(dstSwitchCreatedMeterIds)

        def srcSwitchBurstSize = switchHelper.getExpectedBurst(srcSwitch.dpId, flow.maximumBandwidth)
        def dstSwitchBurstSize = switchHelper.getExpectedBurst(dstSwitch.dpId, flow.maximumBandwidth)
        srcSwitchValidateInfo.meters.excess*.burstSize[0] == srcSwitchBurstSize
        dstSwitchValidateInfo.meters.excess*.burstSize[0] == dstSwitchBurstSize

        and: "Meters info is also stored in the 'missing' section on the src and dst switches"
        def srcSwitchCreatedCookies = cookiesWithMeter(srcSwitch.dpId)
        srcSwitchValidateInfo.meters.missing*.meterId[0] == newMeterId
        srcSwitchValidateInfo.meters.missing*.cookie.containsAll(srcSwitchCreatedCookies)

        srcSwitchValidateInfo.meters.missing.each {
            assert it.rate == flow.maximumBandwidth
            assert it.flowId == flow.id
            assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            burstSizeIsCorrect(srcSwitchBurstSize, it.burstSize)
        }

        def dstSwitchCreatedCookies = cookiesWithMeter(dstSwitch.dpId)
        dstSwitchValidateInfo.meters.missing*.meterId[0] == newMeterId
        dstSwitchValidateInfo.meters.missing*.cookie.containsAll(dstSwitchCreatedCookies)

        dstSwitchValidateInfo.meters.missing.each {
            assert it.rate == flow.maximumBandwidth
            assert it.flowId == flow.id
            assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            burstSizeIsCorrect(dstSwitchBurstSize, it.burstSize)
        }

        and: "The rest fields are empty on the src and dst switches"
        [srcSwitchValidateInfo, dstSwitchValidateInfo].each {
            meterSectionsAreEmpty(it, ["misconfigured", "proper"])
        }

        and: "Rules are still exist in the 'proper' section on the src and dst switches"
        [srcSwitchValidateInfo, dstSwitchValidateInfo].each {
            ruleSectionsAreEmpty(it, ["missing", "excess"])
        }

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        and: "Delete created meter on the both switches"
        srcSwitchCreatedMeterIds.each { northbound.deleteMeter(srcSwitch.dpId, it) }
        dstSwitchCreatedMeterIds.each { northbound.deleteMeter(dstSwitch.dpId, it) }

        then: "Check that switch validate info is also deleted"
        Wrappers.wait(WAIT_OFFSET) {
            def srcSwitchValidateInfoAfterDelete = northbound.switchValidate(srcSwitch.dpId)
            def dstSwitchValidateInfoAfterDelete = northbound.switchValidate(dstSwitch.dpId)
            [srcSwitchValidateInfoAfterDelete, dstSwitchValidateInfoAfterDelete].each {
                ruleSectionsAreEmpty(it)
                meterSectionsAreEmpty(it)
            }
        }
    }

    def "Switch validate is able to detect rule info into the 'missing' section"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelper.addFlow(flowHelper.randomFlow(srcSwitch, dstSwitch))

        and: "Delete created rules on the srcSwitch"
        northbound.deleteSwitchRules(srcSwitch.dpId, DeleteRulesAction.IGNORE_DEFAULTS)

        then: "Rule info is moved into the 'missing' section on the srcSwitch"
        def srcSwitchCreatedCookies = cookiesWithMeter(srcSwitch.dpId)
        def dstSwitchCreatedCookies = cookiesWithMeter(dstSwitch.dpId)
        def createdCookies = srcSwitchCreatedCookies + dstSwitchCreatedCookies

        def srcSwitchValidateInfo = northbound.switchValidate(srcSwitch.dpId)
        srcSwitchValidateInfo.rules.missing.containsAll(createdCookies)
        ruleSectionsAreEmpty(srcSwitchValidateInfo, ["proper", "excess"])

        and: "Rule info is NOT moved into the 'missing' section on the dstSwitch"
        def dstSwitchValidateInfo = northbound.switchValidate(dstSwitch.dpId)
        dstSwitchValidateInfo.rules.proper.collect { it }.containsAll(createdCookies)
        ruleSectionsAreEmpty(dstSwitchValidateInfo, ["missing", "excess"])

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "Check that switch validate info is also deleted"
        Wrappers.wait(WAIT_OFFSET) {
            def srcSwitchValidateInfoAfterDelete = northbound.switchValidate(srcSwitch.dpId)
            def dstSwitchValidateInfoAfterDelete = northbound.switchValidate(dstSwitch.dpId)
            [srcSwitchValidateInfoAfterDelete, dstSwitchValidateInfoAfterDelete].each {
                ruleSectionsAreEmpty(it)
                meterSectionsAreEmpty(it)
            }
        }
    }

    def "Switch validate is able to detect rule info into the 'excess' section"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelper.addFlow(flowHelper.randomFlow(srcSwitch, dstSwitch))

        and: "Change cookies for the created flow directly in DB"
        def srcSwitchCreatedCookies = cookiesWithMeter(srcSwitch.dpId)
        def dstSwitchCreatedCookies = cookiesWithMeter(dstSwitch.dpId)
        def createdCookies = srcSwitchCreatedCookies + dstSwitchCreatedCookies
        def newFlowCookies = [createdCookies[0] + 1, createdCookies[1] + 1]
        /** at this point rules are set for given flow. Now update flow cookies directly via DB,
         it is done just for moving rules from the 'proper' section into the 'excess' section*/
        //TODO(andriidovhan)try to create excess rules directly via kafka, then remove the updateFlowSegmentCookie impl
        database.updateFlowCookie(flow.id, newFlowCookies[0], newFlowCookies[1])
        database.updateFlowSegmentCookie(flow.id, srcSwitchCreatedCookies[0])
        database.updateFlowSegmentCookie(flow.id, dstSwitchCreatedCookies[0])

        then: "Rule validate info is moved into the 'missing' and 'excess' sections on the src and dst switches"
        def srcSwitchValidateInfo = northbound.switchValidate(srcSwitch.dpId)
        srcSwitchValidateInfo.rules.excess.containsAll(srcSwitchCreatedCookies)
        srcSwitchValidateInfo.rules.missing.containsAll(newFlowCookies[0])

        def dstSwitchValidateInfo = northbound.switchValidate(dstSwitch.dpId)
        dstSwitchValidateInfo.rules.excess.containsAll(dstSwitchCreatedCookies)
        dstSwitchValidateInfo.rules.missing.containsAll(newFlowCookies[1])

        [srcSwitchValidateInfo, dstSwitchValidateInfo].each {
            ruleSectionsAreEmpty(it, ["proper"])
        }

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        and: "Clean the excess rules"
        //remove excess rule which is still exist on the both switches
        // srcSwitch -> dstSwitchCreatedCookies
        // dstSwitch -> srcSwitchCreatedCookies
        northbound.deleteSwitchRules(srcSwitch.dpId, DeleteRulesAction.IGNORE_DEFAULTS)
        northbound.deleteSwitchRules(dstSwitch.dpId, DeleteRulesAction.IGNORE_DEFAULTS)

        then: "Check that switch validate info is also deleted"
        Wrappers.wait(WAIT_OFFSET) {
            def srcSwitchValidateInfoAfterDelete = northbound.switchValidate(srcSwitch.dpId)
            def dstSwitchValidateInfoAfterDelete = northbound.switchValidate(dstSwitch.dpId)
            [srcSwitchValidateInfoAfterDelete, dstSwitchValidateInfoAfterDelete].each {
                ruleSectionsAreEmpty(it)
                meterSectionsAreEmpty(it)
            }
        }
    }

    def "Able to get empty switch validate information from the intermediate switch(flow contain > 2 switches)"() {
        when: "Create an intermediate-switch flow"
        def flow = intermediateSwitchFlow()
        flowHelper.addFlow(flow)
        def flowPath = PathHelper.convert(northbound.getFlowPath(flow.id))

        then: "The intermediate switch does not contain any information about meter"
        def intermediateSwitchValidateInfo = northbound.switchValidate(flowPath[1].switchId)
        meterSectionsAreEmpty(intermediateSwitchValidateInfo)

        when: "Delete the flow"
        flowHelper.deleteFlow(flow.id)

        then: "Check that switch validate info is also deleted"
        flowPath.each {
            if (switchHelper.getDescription(it.switchId).contains("OF_13")) {
                def switchValidateInfo = northbound.switchValidate(it.switchId)
                meterSectionsAreEmpty(switchValidateInfo)
                ruleSectionsAreEmpty(switchValidateInfo)
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

    List<Integer> createdMeterIds(SwitchId switchId) {
        northbound.getAllMeters(switchId).meterEntries.findAll { it.meterId > MAX_SYSTEM_RULE_METER_ID }.collect {
            it.meterId
        }
    }

    List<Long> cookiesWithMeter(SwitchId switchId) {
        def cookies = []
        northbound.getSwitchRules(switchId).flowEntries.collect {
            if (!Cookie.isDefaultRule(it.cookie) && it.instructions.goToMeter) {
                cookies << it.cookie
            }
        }
        return cookies
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
