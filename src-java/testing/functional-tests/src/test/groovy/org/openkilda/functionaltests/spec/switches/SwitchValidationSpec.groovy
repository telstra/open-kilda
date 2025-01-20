package org.openkilda.functionaltests.spec.switches

import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.helpers.SwitchHelper.isDefaultMeter
import static org.openkilda.functionaltests.helpers.model.FlowEncapsulationType.VXLAN
import static org.openkilda.functionaltests.helpers.model.SwitchExtended.verifyMeterSectionsAreEmpty
import static org.openkilda.functionaltests.helpers.model.SwitchExtended.verifyRuleSectionsAreEmpty
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.SYNCHRONIZE_SWITCH
import static org.openkilda.model.MeterId.MIN_FLOW_METER_ID
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.tools.KafkaUtils.buildMessage

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowDirection
import org.openkilda.functionaltests.helpers.model.SwitchExtended
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.messaging.model.FlowDirectionType
import org.openkilda.model.MeterId
import org.openkilda.model.cookie.Cookie
import org.openkilda.model.cookie.CookieBase.CookieType
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
import spock.lang.Narrative
import spock.lang.See
import spock.lang.Shared

@See(["https://github.com/telstra/open-kilda/tree/develop/docs/design/hub-and-spoke/switch-validate",
        "https://github.com/telstra/open-kilda/tree/develop/docs/design/hub-and-spoke/switch-sync"])
@Narrative("""This test suite checks the switch validate feature followed by switch synchronization for different type
of rules and different rules states.
Rules states: excess, missing, proper.
Rule types: ingress, egress, transit, special (protected path rules, connected devices rules etc.)
Note that 'default' rules are not covered by this spec.
Description of fields:
- missing - those meters/rules, which are NOT present on a switch, but are present in db
- misconfigured - those meters which have different value (on a switch and in db) for the same parameter
- excess - those meters/rules, which are present on a switch, but are NOT present in db
- proper - meters/rules values are the same on a switch and in db
""")
@Tags([SMOKE, SMOKE_SWITCHES])
class SwitchValidationSpec extends HealthCheckSpecification {
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

    def setupSpec() {
        deleteAnyFlowsLeftoversIssue5480()
    }

    def "Able to validate and sync a terminating switch with proper rules and meters"() {
        given: "A flow"
        def (SwitchExtended srcSwitch, SwitchExtended dstSwitch) = switches.all().notOF12Version()
        def flow = flowFactory.getRandom(srcSwitch, dstSwitch)

        expect: "Validate switch for src and dst contains expected meters data in 'proper' section"
        def srcSwitchValidateInfo = srcSwitch.validateV1()
        def dstSwitchValidateInfo = dstSwitch.validateV1()
        def srcSwitchCreatedCookies = srcSwitch.rulesManager.getRulesWithMeter().cookie
        def dstSwitchCreatedCookies = dstSwitch.rulesManager.getRulesWithMeter().cookie

        srcSwitchValidateInfo.meters.proper*.meterId.containsAll(srcSwitch.metersManager.getCreatedMeterIds())
        dstSwitchValidateInfo.meters.proper*.meterId.containsAll(dstSwitch.metersManager.getCreatedMeterIds())

        srcSwitchValidateInfo.meters.proper*.cookie.containsAll(srcSwitchCreatedCookies)
        dstSwitchValidateInfo.meters.proper*.cookie.containsAll(dstSwitchCreatedCookies)

        def srcSwitchProperMeters = srcSwitchValidateInfo.meters.proper.findAll({ !isDefaultMeter(it) })
        def dstSwitchProperMeters = dstSwitchValidateInfo.meters.proper.findAll({ !isDefaultMeter(it) })

        [[srcSwitch, srcSwitchProperMeters], [dstSwitch, dstSwitchProperMeters]].each { sw, meters ->
            meters.each {
                sw.verifyRateSizeIsCorrect(flow.maximumBandwidth, it.rate)
                assert it.flowId == flow.flowId
                assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            }
        }

        Long srcSwitchBurstSize = srcSwitch.getExpectedBurst(flow.maximumBandwidth)
        srcSwitch.verifyBurstSizeIsCorrect(srcSwitchBurstSize, srcSwitchProperMeters*.burstSize[0])

        Long dstSwitchBurstSize = dstSwitch.getExpectedBurst(flow.maximumBandwidth)
        dstSwitch.verifyBurstSizeIsCorrect(dstSwitchBurstSize, dstSwitchProperMeters*.burstSize[0])

        and: "The rest fields in the 'meter' section are empty"
        verifyMeterSectionsAreEmpty(srcSwitchValidateInfo, ["missing", "misconfigured", "excess"])
        verifyMeterSectionsAreEmpty(dstSwitchValidateInfo, ["missing", "misconfigured", "excess"])

        and: "Created rules are stored in the 'proper' section"
        def createdCookies = srcSwitchCreatedCookies + dstSwitchCreatedCookies
        [srcSwitchValidateInfo, dstSwitchValidateInfo].each {
            it.rules.proper.containsAll(createdCookies)
        }

        and: "The rest fields in the 'rule' section are empty"
        verifyRuleSectionsAreEmpty(srcSwitchValidateInfo, ["missing", "excess"])
        verifyRuleSectionsAreEmpty(dstSwitchValidateInfo, ["missing", "excess"])

        and: "Able to perform switch sync which does nothing"
        !srcSwitch.synchronizeAndCollectFixedDiscrepancies().isPresent()

        when: "Delete the flow"
        flow.delete()

        then: "Switch validate request returns only default rules information"
        Wrappers.wait(WAIT_OFFSET) {
            def srcSwitchValidateInfoAfterDelete = srcSwitch.validateV1()
            def dstSwitchValidateInfoAfterDelete = dstSwitch.validateV1()
            verifyRuleSectionsAreEmpty(srcSwitchValidateInfoAfterDelete)
            verifyRuleSectionsAreEmpty(dstSwitchValidateInfoAfterDelete)
        }
    }

    def "Able to validate and sync a transit switch with proper rules and no meters"() {
        given: "Two active not neighboring switches"
        def switchPair = switchPairs.all().nonNeighbouring().random()

        when: "Create an intermediate-switch flow"
        def flow = flowFactory.getRandom(switchPair)
        def flowPathInfo = flow.retrieveAllEntityPaths()
        List<SwitchExtended> involvedSwitches = switches.all().findSwitchesInPath(flowPathInfo)

        then: "The intermediate switch does not contain any information about meter"
        def switchToValidate = involvedSwitches.find {
            !(it.switchId in switchPair.toList().dpId) && !it.isOf12Version()
        }
        def intermediateSwitchValidateInfo = switchToValidate.validateV1()
        verifyMeterSectionsAreEmpty(intermediateSwitchValidateInfo)

        and: "Rules are stored in the 'proper' section on the transit switch"
        intermediateSwitchValidateInfo.rules.proper.findAll { !new Cookie(it).serviceFlag }.size() == 2
        verifyRuleSectionsAreEmpty(intermediateSwitchValidateInfo, ["missing", "excess"])

        and: "Able to perform switch sync which does nothing"
        !switchToValidate.synchronizeAndCollectFixedDiscrepancies().isPresent()

        when: "Delete the flow"
        flow.delete()

        then: "Check that the switch validate request returns empty sections"
        involvedSwitches.each { sw ->
            def switchValidateInfo = sw.validateV1()
            verifyRuleSectionsAreEmpty(switchValidateInfo)
            if (sw.isOf13Version()) {
                verifyMeterSectionsAreEmpty(switchValidateInfo)
            }
        }
    }

    def "Able to validate switch with 'misconfigured' meters"() {
        when: "Create a flow"
        def (SwitchExtended srcSwitch, SwitchExtended dstSwitch) = switches.all().notOF12Version()
        def flow = flowFactory.getRandom(srcSwitch, dstSwitch)
        def srcSwitchCreatedMeterIds = srcSwitch.metersManager.getCreatedMeterIds()
        def dstSwitchCreatedMeterIds = dstSwitch.metersManager.getCreatedMeterIds()

        and: "Change bandwidth for the created flow directly in DB so that system thinks the installed meter is \
misconfigured"
        def newBandwidth = flow.maximumBandwidth + 100
        /** at this point meter is set for given flow. Now update flow bandwidth directly via DB,
         it is done just for moving meter from the 'proper' section into the 'misconfigured'*/
        cleanupManager.addAction(SYNCHRONIZE_SWITCH, {srcSwitch.synchronize()})
        cleanupManager.addAction(SYNCHRONIZE_SWITCH, {dstSwitch.synchronize()})
        flow.updateFlowBandwidthInDB(newBandwidth)
        //at this point existing meters do not correspond with the flow

        and: "Validate src and dst switches"
        def srcSwitchValidateInfo = srcSwitch.validateV1()
        def dstSwitchValidateInfo = dstSwitch.validateV1()

        then: "Meters info is moved into the 'misconfigured' section"
        def srcSwitchCreatedCookies = srcSwitch.rulesManager.getRulesWithMeter().cookie
        def dstSwitchCreatedCookies = dstSwitch.rulesManager.getRulesWithMeter().cookie

        srcSwitchValidateInfo.meters.misconfigured*.meterId.containsAll(srcSwitchCreatedMeterIds)
        dstSwitchValidateInfo.meters.misconfigured*.meterId.containsAll(dstSwitchCreatedMeterIds)

        srcSwitchValidateInfo.meters.misconfigured*.cookie.containsAll(srcSwitchCreatedCookies)
        dstSwitchValidateInfo.meters.misconfigured*.cookie.containsAll(dstSwitchCreatedCookies)

        [[srcSwitch, srcSwitchValidateInfo], [dstSwitch, dstSwitchValidateInfo]].each { sw, validation ->
            assert validation.meters.misconfigured.meterId.size() == 1
            validation.meters.misconfigured.each {
                sw.verifyRateSizeIsCorrect(flow.maximumBandwidth, it.rate)
                assert it.flowId == flow.flowId
                assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            }
        }

        Long srcSwitchBurstSize = srcSwitch.getExpectedBurst(flow.maximumBandwidth)
        Long dstSwitchBurstSize = dstSwitch.getExpectedBurst(flow.maximumBandwidth)
        srcSwitch.verifyBurstSizeIsCorrect(srcSwitchBurstSize, srcSwitchValidateInfo.meters.misconfigured*.burstSize[0])
        dstSwitch.verifyBurstSizeIsCorrect(dstSwitchBurstSize, dstSwitchValidateInfo.meters.misconfigured*.burstSize[0])

        and: "Reason is specified why meter is misconfigured"
        [srcSwitchValidateInfo, dstSwitchValidateInfo].each {
            it.meters.misconfigured.each {
                assert it.actual.rate == flow.maximumBandwidth
                assert it.expected.rate == newBandwidth
            }
        }

        and: "The rest fields of 'meter' section are empty"
        verifyMeterSectionsAreEmpty(srcSwitchValidateInfo, ["proper", "missing", "excess"])
        verifyMeterSectionsAreEmpty(dstSwitchValidateInfo, ["proper", "missing", "excess"])

        and: "Created rules are still stored in the 'proper' section"
        def createdCookies = srcSwitchCreatedCookies + dstSwitchCreatedCookies
        [[srcSwitch.switchId, srcSwitchValidateInfo], [dstSwitch.switchId, dstSwitchValidateInfo]].each { swId, info ->
            assert info.rules.proper.containsAll(createdCookies), swId
            verifyRuleSectionsAreEmpty(info, ["missing", "excess"])
        }

        and: "Flow validation shows discrepancies"
        def involvedSwitches =  switches.all().findSwitchesInPath(flow.retrieveAllEntityPaths())
        def totalSwitchRules = 0
        def totalSwitchMeters = 0
        involvedSwitches.each { sw ->
            totalSwitchRules += sw.rulesManager.getRules().size()
            totalSwitchMeters += sw.metersManager.getMeters().size()
        }
        def flowValidateResponse = flow.validate()
        def expectedRulesCount = [
                flow.getFlowRulesCountBySwitch(FlowDirection.FORWARD, involvedSwitches.size(), srcSwitch.isS42FlowRttEnabled()),
                flow.getFlowRulesCountBySwitch(FlowDirection.REVERSE, involvedSwitches.size(), dstSwitch.isS42FlowRttEnabled())]

        flowValidateResponse.eachWithIndex { direction, i ->
            assert direction.discrepancies.size() == 2

            def rate = direction.discrepancies[0]
            assert rate.field == "meterRate"
            assert rate.expectedValue == newBandwidth.toString()
            assert rate.actualValue == flow.maximumBandwidth.toString()

            def burst = direction.discrepancies[1]
            assert burst.field == "meterBurstSize"
            // src/dst switches can be different
            // then as a result burstSize in forward/reverse directions can be different
            // that's why we use eachWithIndex and why we calculate burstSize for src/dst switches
            def sw = (i == 0) ? srcSwitch : dstSwitch
            def switchBurstSize = (i == 0) ? srcSwitchBurstSize : dstSwitchBurstSize
            Long newBurstSize = sw.getExpectedBurst(newBandwidth)
            sw.verifyBurstSizeIsCorrect(newBurstSize, burst.expectedValue.toLong())
            sw.verifyBurstSizeIsCorrect(switchBurstSize, burst.actualValue.toLong())

            assert direction.flowRulesTotal == ((FlowDirectionType.FORWARD.toString() == direction.direction) ?
                    expectedRulesCount[0] : expectedRulesCount[1])
            assert direction.switchRulesTotal == totalSwitchRules
            assert direction.flowMetersTotal == 1
            assert direction.switchMetersTotal == totalSwitchMeters
        }

        when: "Restore correct bandwidth via DB"
        flow.updateFlowBandwidthInDB(flow.maximumBandwidth)

        then: "Misconfigured meters are moved into the 'proper' section"
        def srcSwitchValidateInfoRestored = srcSwitch.validateV1()
        def dstSwitchValidateInfoRestored = dstSwitch.validateV1()

        srcSwitchValidateInfoRestored.meters.proper*.meterId.containsAll(srcSwitchCreatedMeterIds)
        dstSwitchValidateInfoRestored.meters.proper*.meterId.containsAll(dstSwitchCreatedMeterIds)
        verifyMeterSectionsAreEmpty(srcSwitchValidateInfoRestored, ["missing", "misconfigured", "excess"])
        verifyMeterSectionsAreEmpty(dstSwitchValidateInfoRestored, ["missing", "misconfigured", "excess"])

        and: "Flow validation shows no discrepancies"
        flow.validateAndCollectDiscrepancies().isEmpty()

        when: "Delete the flow"
        flow.delete()

        then: "Check that the switch validate request returns empty sections"
        Wrappers.wait(WAIT_OFFSET) {
            def srcSwitchValidateInfoAfterDelete = srcSwitch.validateV1()
            verifyRuleSectionsAreEmpty(srcSwitchValidateInfoAfterDelete)
            def dstSwitchValidateInfoAfterDelete = dstSwitch.validateV1()
            verifyRuleSectionsAreEmpty(dstSwitchValidateInfoAfterDelete)
        }
    }

    def "Able to validate and sync a switch with missing ingress rule + meter"() {
        when: "Create a flow"
        def (SwitchExtended srcSwitch, SwitchExtended dstSwitch) = switches.all().notOF12Version()
        def flow = flowFactory.getRandom(srcSwitch, dstSwitch)
        def srcSwitchCreatedMeterIds = srcSwitch.metersManager.getCreatedMeterIds()
        def dstSwitchCreatedMeterIds = dstSwitch.metersManager.getCreatedMeterIds()

        and: "Remove created meter on the srcSwitch"
        def forwardCookies = srcSwitch.rulesManager.getRulesWithMeter().cookie
        def reverseCookies = dstSwitch.rulesManager.getRulesWithMeter().cookie
        def sharedCookieOnSrcSw = srcSwitch.rulesManager.getRules().findAll {
            new Cookie(it.cookie).getType() in [CookieType.SHARED_OF_FLOW, CookieType.SERVER_42_FLOW_RTT_INGRESS]
        }?.cookie
        def untouchedCookiesOnSrcSw = (reverseCookies + sharedCookieOnSrcSw).sort()
        def cookiesOnDstSw = dstSwitch.rulesManager.getRules().cookie
        cleanupManager.addAction(SYNCHRONIZE_SWITCH, {srcSwitch.synchronize()})
        srcSwitch.metersManager.delete(srcSwitchCreatedMeterIds[0])

        then: "Meters info/rules are moved into the 'missing' section on the srcSwitch"
        verifyAll(srcSwitch.validateV1()) {
            it.rules.missing.sort() == forwardCookies
            it.rules.proper.findAll { !new Cookie(it).serviceFlag }.sort() == untouchedCookiesOnSrcSw
//forward cookie's removed with meter

            it.meters.missing*.meterId == srcSwitchCreatedMeterIds
            it.meters.missing*.cookie == forwardCookies

            Long srcSwitchBurstSize = srcSwitch.getExpectedBurst(flow.maximumBandwidth)
            it.meters.missing.each {
                srcSwitch.verifyRateSizeIsCorrect(flow.maximumBandwidth, it.rate)
                assert it.flowId == flow.flowId
                assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
                srcSwitch.verifyBurstSizeIsCorrect(srcSwitchBurstSize, it.burstSize)
            }
            verifyMeterSectionsAreEmpty(it, ["proper", "misconfigured", "excess"])
            verifyRuleSectionsAreEmpty(it, ["excess"])
        }

        and: "Meters info/rules are NOT moved into the 'missing' section on the dstSwitch"
        verifyAll(dstSwitch.validateV1()) {
            it.rules.proper.sort() == cookiesOnDstSw.sort()

            def properMeters = it.meters.proper.findAll({ dto -> !isDefaultMeter(dto) })
            properMeters*.meterId == dstSwitchCreatedMeterIds
            properMeters.cookie.size() == 1
            properMeters*.cookie == reverseCookies

            Long dstSwitchBurstSize = dstSwitch.getExpectedBurst(flow.maximumBandwidth)
            properMeters.each {
                dstSwitch.verifyRateSizeIsCorrect(flow.maximumBandwidth, it.rate)
                assert it.flowId == flow.flowId
                assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
                dstSwitch.verifyBurstSizeIsCorrect(dstSwitchBurstSize, it.burstSize)
            }
            verifyMeterSectionsAreEmpty(it, ["missing", "misconfigured", "excess"])
            verifyRuleSectionsAreEmpty(it, ["missing", "excess"])
        }

        when: "Synchronize switch with missing rule and meter"
        verifyAll(srcSwitch.synchronize(false)) {
            it.rules.installed == forwardCookies
            it.meters.installed*.meterId == srcSwitchCreatedMeterIds as List<Long>
        }

        then: "Repeated validation shows no missing entities"
        with(srcSwitch.validateV1()) {
            verifyMeterSectionsAreEmpty(it, ["missing", "misconfigured", "excess"])
            verifyRuleSectionsAreEmpty(it, ["missing", "excess"])
        }

        when: "Delete the flow"
        flow.delete()

        then: "Check that the switch validate request returns empty sections"
        Wrappers.wait(WAIT_OFFSET) {
            def srcSwitchValidateInfoAfterDelete = srcSwitch.validateV1()
            verifyRuleSectionsAreEmpty(srcSwitchValidateInfoAfterDelete)
            def dstSwitchValidateInfoAfterDelete = dstSwitch.validateV1()
            verifyRuleSectionsAreEmpty(dstSwitchValidateInfoAfterDelete)
        }
    }

    def "Able to validate and sync a switch with missing ingress rule (unmetered)"() {
        when: "Create a flow"
        def (SwitchExtended srcSwitch, SwitchExtended dstSwitch) = switches.all().notOF12Version()
        def flow = flowFactory.getBuilder(srcSwitch, dstSwitch)
                .withBandwidth(0)
                .withIgnoreBandwidth(true).build()
                .create()

        and: "Remove ingress rule on the srcSwitch"
        def flowDBInfo = flow.retrieveDetailsFromDB()
        def ingressCookie = flowDBInfo.forwardPath.cookie.value
        def egressCookie = flowDBInfo.reversePath.cookie.value
        srcSwitch.rulesManager.delete(ingressCookie)

        then: "Ingress rule is moved into the 'missing' section on the srcSwitch"
        def sharedCookieOnSrcSw = srcSwitch.rulesManager.getRules().findAll {
            new Cookie(it.cookie).getType() in [CookieType.SHARED_OF_FLOW, CookieType.SERVER_42_FLOW_RTT_INGRESS]
        }?.cookie
        def untouchedCookies = ([egressCookie] + sharedCookieOnSrcSw).sort()
        verifyAll(srcSwitch.validateV1()) {
            it.rules.missing == [ingressCookie]
            it.rules.proper.findAll {
                def cookie = new Cookie(it)
                !cookie.serviceFlag || cookie.type == CookieType.SHARED_OF_FLOW
            }.sort() == untouchedCookies
            verifyMeterSectionsAreEmpty(it)
            verifyRuleSectionsAreEmpty(it, ["excess"])
        }

        when: "Synchronize switch with missing unmetered rule"
        with(srcSwitch.synchronize(false)) {
            rules.installed == [ingressCookie]
        }

        then: "Repeated validation shows no missing entities"
        with(srcSwitch.validateV1()) {
            verifyMeterSectionsAreEmpty(it)
            verifyRuleSectionsAreEmpty(it, ["missing", "excess"])
            it.rules.proper.findAll { !new Cookie(it).serviceFlag }.sort() == (untouchedCookies + ingressCookie).sort()
        }

        when: "Delete the flow"
        flow.delete()

        then: "Check that the switch validate request returns empty sections"
        Wrappers.wait(WAIT_OFFSET) {
            def srcSwitchValidateInfoAfterDelete = srcSwitch.validateV1()
            verifyRuleSectionsAreEmpty(srcSwitchValidateInfoAfterDelete)
            def dstSwitchValidateInfoAfterDelete = dstSwitch.validateV1()
            verifyRuleSectionsAreEmpty(dstSwitchValidateInfoAfterDelete)
        }
    }

    def "Able to validate and sync a switch with missing transit rule"() {
        given: "Two active not neighboring switches"
        def switchPair = switchPairs.all().nonNeighbouring().random()

        and: "Create an intermediate-switch flow"
        def flow = flowFactory.getRandom(switchPair)

        when: "Delete created rules on the transit"
        def involvedSwitches = switches.all().findSwitchesInPath(flow.retrieveAllEntityPaths())
        def transitSw = involvedSwitches.find { !(it.switchId in switchPair.toList().dpId) }
        transitSw.rulesManager.delete(DeleteRulesAction.IGNORE_DEFAULTS)

        then: "Rule info is moved into the 'missing' section"
        verifyAll(transitSw.validateV1()) {
            it.rules.missing.size() == 2
            it.rules.proper.findAll { !new Cookie(it).serviceFlag }.empty
            it.rules.excess.empty
        }

        when: "Synchronize the switch"
        with(transitSw.synchronize(false)) {
            rules.installed.size() == 2
        }

        then: "Repeated validation shows no discrepancies"
        verifyAll(transitSw.validateV1()) {
            it.rules.proper.findAll { !new Cookie(it).serviceFlag }.size() == 2
            verifyRuleSectionsAreEmpty(it, ["missing", "excess"])
        }

        when: "Delete the flow"
        flow.delete()

        then: "Check that the switch validate request returns empty sections on all involved switches"
        Wrappers.wait(WAIT_OFFSET) {
            involvedSwitches.each { sw ->
                def switchValidateInfo = sw.validateV1()
                verifyRuleSectionsAreEmpty(switchValidateInfo)
                if (!sw.isOf12Version()) {
                    verifyMeterSectionsAreEmpty(switchValidateInfo)
                }
            }
        }
    }

    def "Able to validate and sync a switch with missing egress rule"() {
        given: "Two active not neighboring switches"
        def switchPair = switchPairs.all().nonNeighbouring().random()

        and: "Create an intermediate-switch flow"
        def flow = flowFactory.getRandom(switchPair)
        def srcSwitch = switches.all().findSpecific(switchPair.src.dpId)
        def dstSwitch = switches.all().findSpecific(switchPair.dst.dpId)
        def rulesOnSrc = srcSwitch.rulesManager.getRules()
        def rulesOnDst = dstSwitch.rulesManager.getRules()

        when: "Delete created rules on the srcSwitch"
        def egressCookie = flow.retrieveDetailsFromDB().reversePath.cookie.value
        srcSwitch.rulesManager.delete(egressCookie)

        then: "Rule info is moved into the 'missing' section on the srcSwitch"
        verifyAll(srcSwitch.validateV1()) {
            it.rules.missing == [egressCookie]
            it.rules.proper.size() == rulesOnSrc.size() - 1
            it.rules.excess.empty
        }

        and: "Rule info is NOT moved into the 'missing' section on the dstSwitch and transit switches"
        def dstSwitchValidateInfo = dstSwitch.validateV1()
        dstSwitchValidateInfo.rules.proper.sort() == rulesOnDst*.cookie.sort()
        verifyRuleSectionsAreEmpty(dstSwitchValidateInfo, ["missing", "excess"])
        def involvedSwitchIds =  switches.all().findSwitchesInPath(flow.retrieveAllEntityPaths())
        def transitSwitches = involvedSwitchIds.findAll {
            !(it.switchId in switchPair.toList().dpId) && !it.isOf12Version()
        }

        transitSwitches.each { sw ->
            def transitSwitchValidateInfo = sw.validateV1()
            assert transitSwitchValidateInfo.rules.proper.findAll { !new Cookie(it).serviceFlag }.size() == 2
            verifyRuleSectionsAreEmpty(transitSwitchValidateInfo, ["missing", "excess"])
        }

        when: "Synchronize the switch"
        with(srcSwitch.synchronize(false)) {
            rules.installed == [egressCookie]
        }

        then: "Repeated validation shows no discrepancies"
        verifyAll(dstSwitch.validateV1()) {
            it.rules.proper.sort() == rulesOnDst*.cookie.sort()
            verifyRuleSectionsAreEmpty(it, ["missing", "excess"])
        }

        when: "Delete the flow"
        flow.delete()

        then: "Check that the switch validate request returns empty sections on all involved switches"
        Wrappers.wait(WAIT_OFFSET) {
            involvedSwitchIds.findAll { !it.isOf12Version() }.each { sw ->
                def switchValidateInfo = sw.validateV1()
                verifyRuleSectionsAreEmpty(switchValidateInfo)
                verifyMeterSectionsAreEmpty(switchValidateInfo)
            }
        }
    }

    def "Able to validate and sync an excess ingress/egress/transit rule + meter"() {
        given: "Two active not neighboring switches"
        def switchPair = switchPairs.all().nonNeighbouring().random()

        and: "Create an intermediate-switch flow"
        def flow = flowFactory.getRandom(switchPair)
        def involvedSwitches = switches.all().findSwitchesInPath(flow.retrieveAllEntityPaths())
        def srcSwitch = involvedSwitches.find { it.switchId == switchPair.src.dpId }
        def dstSwitch = involvedSwitches.find { it.switchId == switchPair.dst.dpId }

        def createdCookiesSrcSw = srcSwitch.rulesManager.getRules().cookie
        def createdCookiesDstSw = dstSwitch.rulesManager.getRules().cookie
        def createdCookiesTransitSwitch = involvedSwitches.find { it != srcSwitch && it != dstSwitch }
                .rulesManager.getRules().cookie

        when: "Create excess rules on switches"
        def producer = new KafkaProducer(producerProps)
        //pick a meter id which is not yet used on src switch
        def excessMeterId = ((MIN_FLOW_METER_ID..100) - srcSwitch.metersManager.getMeters().meterId).first()
        cleanupManager.addAction(SYNCHRONIZE_SWITCH, {srcSwitch.synchronize()})
        cleanupManager.addAction(SYNCHRONIZE_SWITCH, {dstSwitch.synchronize()})
        producer.send(new ProducerRecord(speakerTopic, dstSwitch.switchId.toString(), buildMessage(
                FlowSpeakerData.builder()
                        .switchId(dstSwitch.switchId)
                        .ofVersion(OfVersion.of(dstSwitch.ofVersion))
                        .cookie(new Cookie(1L))
                        .table(OfTable.EGRESS)
                        .priority(101)
                        .instructions(Instructions.builder().build())
                        .build()).toJson())).get()
        involvedSwitches.findAll { it != srcSwitch && it != dstSwitch && !it.isOf12Version() }.each { transitSw ->
            producer.send(new ProducerRecord(speakerTopic, transitSw.switchId.toString(), buildMessage(
                    FlowSpeakerData.builder()
                            .switchId(transitSw.switchId)
                            .ofVersion(OfVersion.of(transitSw.ofVersion))
                            .cookie(new Cookie(1L))
                            .table(OfTable.TRANSIT)
                            .priority(102)
                            .instructions(Instructions.builder().build())
                            .build()).toJson())).get()
        }
        producer.send(new ProducerRecord(speakerTopic, srcSwitch.switchId.toString(), buildMessage([
                FlowSpeakerData.builder()
                        .switchId(srcSwitch.switchId)
                        .ofVersion(OfVersion.of(srcSwitch.ofVersion))
                        .cookie(new Cookie(1L))
                        .table(OfTable.INPUT)
                        .priority(103)
                        .instructions(Instructions.builder().build())
                        .build(),
                MeterSpeakerData.builder()
                        .switchId(srcSwitch.switchId)
                        .ofVersion(OfVersion.of(srcSwitch.ofVersion))
                        .meterId(new MeterId(excessMeterId))
                        .rate(flow.getMaximumBandwidth())
                        .burst(flow.getMaximumBandwidth())
                        .flags(Sets.newHashSet(MeterFlag.KBPS, MeterFlag.BURST, MeterFlag.STATS))
                        .build()]).toJson())).get()
        producer.flush()

        then: "Switch validation shows excess rules and store them in the 'excess' section"
        Wrappers.wait(WAIT_OFFSET) {
            assert srcSwitch.rulesManager.getRules().size() == createdCookiesSrcSw.size() + 1

            involvedSwitches.findAll { !it.isOf12Version() }.each { sw ->
                def involvedSwitchValidateInfo = sw.validateV1()
                if (sw.switchId == switchPair.src.dpId) {
                    assert involvedSwitchValidateInfo.rules.proper.sort() == createdCookiesSrcSw.sort()
                } else if (sw.switchId == switchPair.dst.dpId) {
                    assert involvedSwitchValidateInfo.rules.proper.sort() == createdCookiesDstSw.sort()
                } else {
                    assert involvedSwitchValidateInfo.rules.proper.sort() == createdCookiesTransitSwitch.sort()
                }
                verifyRuleSectionsAreEmpty(involvedSwitchValidateInfo, ["missing"])

                assert involvedSwitchValidateInfo.rules.excess.size() == 1
                assert involvedSwitchValidateInfo.rules.excess == [1L]
                assert involvedSwitchValidateInfo.rules.excessHex.size() == 1
                assert involvedSwitchValidateInfo.rules.excessHex == [Long.toHexString(1L)]
            }
        }

        and: "Excess meter is shown on the srcSwitch only"
        Long burstSize = flow.maximumBandwidth
        def validateSwitchInfo = srcSwitch.validateV1()
        assert validateSwitchInfo.meters.excess.size() == 1
        assert validateSwitchInfo.meters.excess.each {
            assert it.meterId == excessMeterId
            assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            srcSwitch.verifyRateSizeIsCorrect(flow.maximumBandwidth, it.rate)
            srcSwitch.verifyBurstSizeIsCorrect(burstSize, it.burstSize)
        }
        involvedSwitches.findAll { it.switchId != switchPair.src.dpId && !it.isOf12Version() }.each { sw ->
            assert sw.validateV1().meters.excess.empty
        }

        when: "Try to synchronize every involved switch"
        then: "System deletes excess rules and meters"
        involvedSwitches.each { sw ->
            def syncResponse = sw.synchronize()
            if(!sw.isOf12Version()) {
                assert syncResponse.rules.excess.size() == 1
                assert syncResponse.rules.excess[0] == 1L
                assert syncResponse.rules.removed.size() == 1
                assert syncResponse.rules.removed[0] == 1L
            }
            if(sw.switchId == switchPair.src.dpId ) {
                assert syncResponse.meters.excess.size() == 1
                assert syncResponse.meters.excess.meterId[0] == excessMeterId
                assert syncResponse.meters.removed.size() == 1
                assert syncResponse.meters.removed.meterId[0] == excessMeterId
            }
        }

        when: "Delete the flow"
        flow.delete()

        then: "Check that the switch validate request returns empty sections on all involved switches"
        Wrappers.wait(WAIT_OFFSET) {
            involvedSwitches.findAll { !it.isOf12Version() }.each { sw ->
                def switchValidateInfo = sw.validateV1()
                verifyRuleSectionsAreEmpty(switchValidateInfo)
                verifyMeterSectionsAreEmpty(switchValidateInfo)
            }
        }

        cleanup:
        producer && producer.close()
    }

    @Tags(TOPOLOGY_DEPENDENT)
    def "Able to validate and sync a switch with missing 'vxlan' ingress/transit/egress rule + meter"() {
        given: "Two active not neighboring VXLAN supported switches"
        def switchPair = switchPairs.all().nonNeighbouring().withBothSwitchesVxLanEnabled().random()

        and: "Create a flow with vxlan encapsulation"
        def flow = flowFactory.getBuilder(switchPair).withEncapsulationType(VXLAN).build().create()

        and: "Remove required rules and meters from switches"
        def flowInfoFromDb = flow.retrieveDetailsFromDB()
        List<SwitchExtended> involvedSwitches = switches.all().findSwitchesInPath(flow.retrieveAllEntityPaths())
        def transitSwitch = involvedSwitches.findAll { !(it.switchId in switchPair.toList().dpId)}
        def terminalSwitches = involvedSwitches.findAll { it.switchId in switchPair.toList().dpId }

        def cookiesMap = involvedSwitches.collectEntries { sw ->
            def defaultCookies = sw.collectDefaultCookies()
            [sw.switchId, sw.rulesManager.getRules().findAll {
                !(it.cookie in defaultCookies) && !new Cookie(it.cookie).serviceFlag
            }*.cookie]
        }
        def metersMap = involvedSwitches.collectEntries { sw ->
            [sw.switchId, sw.metersManager.getCreatedMeterIds()]
        }

        expect: "Switch validation shows missing rules and meters on every related switch"
        involvedSwitches.each { it.rulesManager.delete(DeleteRulesAction.IGNORE_DEFAULTS) }
        terminalSwitches.each { it.metersManager.delete(metersMap[it.switchId][0]) }

        Wrappers.wait(RULES_DELETION_TIME) {
            involvedSwitches.each { sw ->
                def validateResponse = sw.validateV1()
                def rulesCount = sw.collectFlowRelatedRulesAmount(flow)

                assert validateResponse.rules.missing.size() == rulesCount
                assert validateResponse.rules.missingHex.size() == rulesCount

                if(sw in terminalSwitches) {
                    assert validateResponse.meters.missing.size() == 1
                }
            }
        }

        when: "Try to synchronize all switches"
        then: "System installs missing rules and meters"
        involvedSwitches.each { sw ->
            def syncResponse = sw.synchronize()
            assert syncResponse.rules.proper.findAll { !new Cookie(it).serviceFlag }.size() == 0
            assert syncResponse.rules.excess.size() == 0
            assert syncResponse.rules.missing.containsAll(cookiesMap[sw.switchId])
            assert syncResponse.rules.removed.size() == 0
            assert syncResponse.rules.installed.containsAll(cookiesMap[sw.switchId])

            if(sw in terminalSwitches) {
                assert syncResponse.meters.proper.findAll { !it.defaultMeter }.size() == 0
                assert syncResponse.meters.excess.size() == 0
                assert syncResponse.meters.missing*.meterId == metersMap[sw.switchId]
                assert syncResponse.meters.removed.size() == 0
                assert syncResponse.meters.installed*.meterId == metersMap[sw.switchId]
            }
        }

        and: "Switch validation doesn't complain about missing rules and meters"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            involvedSwitches.each { sw ->
                def validationResult = sw.validateV1()
                assert validationResult.rules.missing.size() == 0
                assert validationResult.rules.missingHex.size() == 0
                assert validationResult.meters.missing.size() == 0
            }
        }

        and: "Rules are synced correctly"
        // ingressRule should contain "pushVxlan"
        // egressRule should contain "tunnel-id"
        with(terminalSwitches.find { it.switchId == switchPair.src.dpId }.rulesManager.getRules()) { rules ->
            assert rules.find {
                it.cookie == flowInfoFromDb.forwardPath.cookie.value
            }.instructions.applyActions.pushVxlan
            assert rules.find {
                it.cookie == flowInfoFromDb.reversePath.cookie.value
            }.match.tunnelId
        }

        with(terminalSwitches.find { it.switchId == switchPair.dst.dpId }.rulesManager.getRules()) { rules ->
            assert rules.find {
                it.cookie == flowInfoFromDb.forwardPath.cookie.value
            }.match.tunnelId
            assert rules.find {
                it.cookie == flowInfoFromDb.reversePath.cookie.value
            }.instructions.applyActions.pushVxlan
        }

        transitSwitch.each { sw ->
            with(sw.rulesManager.getRules()) { rules ->
                assert rules.find {
                    it.cookie == flowInfoFromDb.forwardPath.cookie.value
                }.match.tunnelId
                assert rules.find {
                    it.cookie == flowInfoFromDb.reversePath.cookie.value
                }.match.tunnelId
            }
        }
    }

    def "Able to validate and sync a missing 'protected path' egress rule"() {
        given: "A flow with protected path"
        def swPair = switchPairs.all().nonNeighbouring().withAtLeastNNonOverlappingPaths(2).random()
        def flow = flowFactory.getBuilder(swPair).withProtectedPath(true).build().create()

        def flowPathInfo = flow.retrieveAllEntityPaths()
        def allSwitches = switches.all().findSwitchesInPath(flowPathInfo)
        def rulesPerSwitch = allSwitches.collectEntries { sw ->
            [sw.switchId, sw.rulesManager.getRules().cookie.sort()]
        }

        expect: "Upon validation all rules are stored in the 'proper' section"
        allSwitches.each { sw ->
            def rules = sw.rulesManager.validate()
            assert rules.properRules.sort() == rulesPerSwitch[sw.switchId]
            assert rules.missingRules.empty
            assert rules.excessRules.empty
        }

        when: "Delete rule of protected path on the srcSwitch (egress)"
        def protectedPath = flowPathInfo.flowPath.protectedPath.forward.nodes.nodes

        def srcSwitch = allSwitches.find { it.switchId == swPair.src.dpId }
        def srcSwitchRules = srcSwitch.rulesManager.getNotDefaultRules()
        def ruleToDelete = srcSwitchRules.find {
            //specifying protectedPath[0](src.inputPort) and protectedPath[1](src.outputPort) as protected path for FORWARD direction is used
            it.instructions?.applyActions?.flowOutput == protectedPath[0].portNo.toString() &&
                    it.match.inPort == protectedPath[1].portNo.toString()
        }.cookie
        srcSwitch.rulesManager.delete(ruleToDelete)

        then: "Deleted rule is moved to the 'missing' section on the srcSwitch"
        verifyAll(srcSwitch.validateV1()) {
            it.rules.proper.sort() == (rulesPerSwitch[srcSwitch.switchId] - ruleToDelete).sort()
            it.rules.missing == [ruleToDelete]
            it.rules.excess.empty
        }

        and: "Rest switches are not affected by deleting the rule on the srcSwitch"
        allSwitches.findAll { it != srcSwitch }.each { sw ->
            def validation = sw.validateV1()
            assert validation.rules.proper.sort() == rulesPerSwitch[sw.switchId]
            assert validation.rules.missing.empty
            assert validation.rules.excess.empty
        }

        when: "Synchronize switch with a missing protected path egress rule"
        with(srcSwitch.synchronize(false)) {
            rules.installed == [ruleToDelete]
        }

        then: "Switch validation no longer shows missing rules"
        verifyAll(srcSwitch.validateV1()) {
            verifyRuleSectionsAreEmpty(it, ["missing", "excess"])
            it.rules.proper.sort() == rulesPerSwitch[swPair.src.dpId]
        }
    }

    def "Able to validate and sync a missing 'connected device' #data.descr rule"() {
        given: "A flow with enabled connected devices"
        def swPair = switchPairs.all().random()
        def flow = flowFactory.getBuilder(swPair)
                .withDetectedDevicesOnDst(true, true).build()
                .create()

        expect: "Switch validation puts connected device lldp rule into 'proper' section"
        def dstSwitch = switches.all().findSpecific(flow.destination.switchId)
        def deviceCookie = dstSwitch.rulesManager.getRules().find(data.cookieSearchClosure).cookie

        with(dstSwitch.validateV1()) {
            it.rules.proper.contains(deviceCookie)
        }

        when: "Remove the connected device rule"
        dstSwitch.rulesManager.delete(deviceCookie)

        then: "Switch validation puts connected device rule into 'missing' section"
        verifyAll(dstSwitch.validateV1()) {
            !it.rules.proper.contains(deviceCookie)
            it.rules.missing.contains(deviceCookie)
            it.rules.missingHex.contains(Long.toHexString(deviceCookie))
        }

        when: "Synchronize the switch"
        with(dstSwitch.synchronize(false)) {
            it.rules.installed == [deviceCookie]
        }

        then: "Switch validation no longer shows any discrepancies in rules nor meters"
        verifyAll(dstSwitch.validateV1()) {
            it.rules.proper.contains(deviceCookie)
            it.rules.missing.empty
            it.rules.missingHex.empty
            it.rules.excess.empty
            it.rules.excessHex.empty
            it.meters.missing.empty
            it.meters.excess.empty
        }

        when: "Delete the flow"
        flow.delete()

        then: "Switch validation is empty"
        verifyAll(dstSwitch.validateV1()) {
            verifyRuleSectionsAreEmpty(it)
            verifyMeterSectionsAreEmpty(it)
        }

        where:
        data << [
                [
                        descr              : "LLDP",
                        cookieSearchClosure: {
                            new Cookie(it.cookie).getType() == CookieType.LLDP_INPUT_CUSTOMER_TYPE
                        }
                ],

                [
                        descr              : "ARP",
                        cookieSearchClosure: {
                            new Cookie(it.cookie).getType() == CookieType.ARP_INPUT_CUSTOMER_TYPE
                        }
                ]
        ]
    }
}
