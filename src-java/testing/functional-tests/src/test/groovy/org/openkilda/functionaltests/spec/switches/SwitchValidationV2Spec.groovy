package org.openkilda.functionaltests.spec.switches

import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.extension.tags.Tag.VIRTUAL
import static org.openkilda.functionaltests.helpers.SwitchHelper.isDefaultMeter
import static org.openkilda.functionaltests.helpers.model.SwitchExtended.verifyMeterSectionsAreEmpty
import static org.openkilda.functionaltests.helpers.model.SwitchExtended.verifyRuleSectionsAreEmpty
import static org.openkilda.functionaltests.helpers.model.SwitchExtended.verifySectionInSwitchValidationInfo
import static org.openkilda.functionaltests.helpers.model.SwitchExtended.verifySectionsAsExpectedFields
import static org.openkilda.functionaltests.helpers.model.SwitchOfVersion.*
import static org.openkilda.functionaltests.helpers.model.Switches.validateAndCollectFoundDiscrepancies
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.SYNCHRONIZE_SWITCH
import static org.openkilda.model.MeterId.MIN_FLOW_METER_ID
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.tools.KafkaUtils.buildMessage

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.DockerHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.ContainerName
import org.openkilda.functionaltests.helpers.model.FlowDirection
import org.openkilda.functionaltests.helpers.model.FlowEncapsulationType
import org.openkilda.functionaltests.helpers.model.SwitchExtended
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.messaging.model.FlowDirectionType
import org.openkilda.model.MeterId
import org.openkilda.model.SwitchFeature
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
class SwitchValidationV2Spec extends HealthCheckSpecification {
    @Value("#{kafkaTopicsConfig.getSpeakerSwitchManagerTopic()}")
    String speakerTopic
    @Autowired
    @Qualifier("kafkaProducerProperties")
    Properties producerProps
    @Value('${docker.host}')
    @Shared
    String dockerHost
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
        def srcSwitchValidateInfo = srcSwitch.validate()
        def dstSwitchValidateInfo = dstSwitch.validate()
        def srcSwitchCreatedCookies = srcSwitch.rulesManager.getRulesWithMeter().cookie
        def dstSwitchCreatedCookies = dstSwitch.rulesManager.getRulesWithMeter().cookie

        srcSwitchValidateInfo.meters.proper*.meterId.containsAll(srcSwitch.metersManager.getCreatedMeterIds())
        dstSwitchValidateInfo.meters.proper*.meterId.containsAll(dstSwitch.metersManager.getCreatedMeterIds())

//        due to the issue https://github.com/telstra/open-kilda/issues/5360
//        srcSwitchValidateInfo.meters.proper*.cookie.containsAll(srcSwitchCreatedCookies)
//        dstSwitchValidateInfo.meters.proper*.cookie.containsAll(dstSwitchCreatedCookies)

        def srcSwitchProperMeters = srcSwitchValidateInfo.meters.proper.findAll({ !isDefaultMeter(it) })
        def dstSwitchProperMeters = dstSwitchValidateInfo.meters.proper.findAll({ !isDefaultMeter(it) })

        [[srcSwitch, srcSwitchProperMeters], [dstSwitch, dstSwitchProperMeters]].each { sw, meters ->
            meters.each {
                sw.verifyRateSizeIsCorrect(flow.maximumBandwidth, it.rate)
               // assert it.flowId == flow.flowId due to the issue https://github.com/telstra/open-kilda/issues/5360
                assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            }
        }

        Long srcSwitchBurstSize = srcSwitch.getExpectedBurst(flow.maximumBandwidth)
        Long dstSwitchBurstSize = dstSwitch.getExpectedBurst(flow.maximumBandwidth)
        srcSwitch.verifyBurstSizeIsCorrect(srcSwitchBurstSize, srcSwitchProperMeters*.burstSize[0])
        dstSwitch.verifyBurstSizeIsCorrect(dstSwitchBurstSize, dstSwitchProperMeters*.burstSize[0])

        and: "The rest fields in the 'meter' section are empty"
        verifyMeterSectionsAreEmpty(srcSwitchValidateInfo, ["missing", "misconfigured", "excess"])
        verifyMeterSectionsAreEmpty(dstSwitchValidateInfo, ["missing", "misconfigured", "excess"])

        and: "Created rules are stored in the 'proper' section"
        def createdCookies = srcSwitchCreatedCookies + dstSwitchCreatedCookies
        [srcSwitchValidateInfo, dstSwitchValidateInfo].each {
            assert it.rules.proper.cookie.containsAll(createdCookies)
        }

        and: "The rest fields in the 'rule' section are empty"
        verifyRuleSectionsAreEmpty(srcSwitchValidateInfo, ["missing", "excess"])
        verifyRuleSectionsAreEmpty(dstSwitchValidateInfo, ["missing", "excess"])

        and: "Able to perform switch sync which does nothing"
        verifyAll(srcSwitch.synchronize(true)) {
            it.rules.removed.empty
            it.rules.installed.empty
            it.meters.removed.empty
            it.meters.installed.empty
        }

        when: "Delete the flow"
        flow.delete()

        then: "Switch validate request returns only default rules information"
        Wrappers.wait(WAIT_OFFSET) {
            def srcSwitchValidateInfoAfterDelete = srcSwitch.validate()
            verifyRuleSectionsAreEmpty(srcSwitchValidateInfoAfterDelete)
            def dstSwitchValidateInfoAfterDelete = dstSwitch.validate()
            verifyRuleSectionsAreEmpty(dstSwitchValidateInfoAfterDelete)

        }
    }

    def "Able to validate and sync a transit switch with proper rules and no meters"() {
        given: "Two active not neighboring switches"
        def switchPair = switchPairs.all().nonNeighbouring().random()

        when: "Create an intermediate-switch flow"
        def flow = flowFactory.getRandom(switchPair)
        def flowPathInfo = flow.retrieveAllEntityPaths()
        def involvedSwitches = switches.all().findSwitchesInPath(flowPathInfo)

        then: "The intermediate switch does not contain any information about meter"
        def switchToValidate = involvedSwitches.find {
            !(it.switchId in switchPair.toList().dpId) && !it.isOf12Version()
        }
        def intermediateSwitchValidateInfo = switchToValidate.validate()
        verifyMeterSectionsAreEmpty(intermediateSwitchValidateInfo)

        and: "Rules are stored in the 'proper' section on the transit switch"
        intermediateSwitchValidateInfo.rules.proper.findAll { !new Cookie(it.cookie).serviceFlag }.size() == 2
        verifyRuleSectionsAreEmpty(intermediateSwitchValidateInfo, ["missing", "excess"])

        and: "Able to perform switch sync which does nothing"
        verifyAll(switchToValidate.synchronize(true)) {
            it.rules.removed.empty
            it.rules.installed.empty
            it.meters.removed.empty
            it.meters.installed.empty
        }

        when: "Delete the flow"
        flow.delete()

        then: "Check that the switch validate request returns empty sections"
        involvedSwitches.each { sw ->
            def switchValidateInfo = sw.validate()
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
        cleanupManager.addAction(SYNCHRONIZE_SWITCH, {srcSwitch.synchronize()})
        cleanupManager.addAction(SYNCHRONIZE_SWITCH, {dstSwitch.synchronize()})
        /** at this point meter is set for given flow. Now update flow bandwidth directly via DB,
         it is done just for moving meter from the 'proper' section into the 'misconfigured'*/
        flow.updateFlowBandwidthInDB(newBandwidth)
        //at this point existing meters do not correspond with the flow

        and: "Validate src and dst switches"
        def srcSwitchValidateInfo = srcSwitch.validate()
        def dstSwitchValidateInfo = dstSwitch.validate()

        then: "Meters info is moved into the 'misconfigured' section"
        def srcSwitchCreatedCookies = srcSwitch.rulesManager.getRulesWithMeter().cookie
        def dstSwitchCreatedCookies = dstSwitch.rulesManager.getRulesWithMeter().cookie

        srcSwitchValidateInfo.meters.misconfigured*.expected.meterId.containsAll(srcSwitchCreatedMeterIds)
        dstSwitchValidateInfo.meters.misconfigured*.expected.meterId.containsAll(dstSwitchCreatedMeterIds)

//        due to the issue due to the issue https://github.com/telstra/open-kilda/issues/5360
//        srcSwitchValidateInfo.meters.misconfigured*.expected.cookie.containsAll(srcSwitchCreatedCookies)
//        dstSwitchValidateInfo.meters.misconfigured*.expected.cookie.containsAll(dstSwitchCreatedCookies)

        [[srcSwitch, srcSwitchValidateInfo], [dstSwitch, dstSwitchValidateInfo]].each { sw, validation ->
            assert validation.meters.misconfigured.id.size() == 1
            validation.meters.misconfigured.each {
                sw.verifyRateSizeIsCorrect(flow.maximumBandwidth, it.discrepancies.rate)
                //assert it.expected.flowId == flow.flowId due to the issue https://github.com/telstra/open-kilda/issues/5360
                assert ["KBPS", "BURST", "STATS"].containsAll(it.expected.flags)
            }
        }

        Long srcSwitchBurstSize = srcSwitch.getExpectedBurst(flow.maximumBandwidth)
        Long dstSwitchBurstSize = dstSwitch.getExpectedBurst(flow.maximumBandwidth)
        srcSwitch.verifyBurstSizeIsCorrect(srcSwitchBurstSize, srcSwitchValidateInfo.meters.misconfigured*.discrepancies.burstSize[0])
        dstSwitch.verifyBurstSizeIsCorrect(dstSwitchBurstSize, dstSwitchValidateInfo.meters.misconfigured*.discrepancies.burstSize[0])

        and: "Reason is specified why meter is misconfigured"
        [srcSwitchValidateInfo, dstSwitchValidateInfo].each {
            it.meters.misconfigured.each {
                assert it.discrepancies.rate == flow.maximumBandwidth
                assert it.expected.rate == newBandwidth
            }
        }

        and: "The rest fields of 'meter' section are empty"
        verifyMeterSectionsAreEmpty(srcSwitchValidateInfo, ["proper", "missing", "excess"])
        verifyMeterSectionsAreEmpty(dstSwitchValidateInfo, ["proper", "missing", "excess"])

        and: "Created rules are still stored in the 'proper' section"
        def createdCookies = srcSwitchCreatedCookies + dstSwitchCreatedCookies
        [[srcSwitch.switchId, srcSwitchValidateInfo], [dstSwitch.switchId, dstSwitchValidateInfo]].each { swId, info ->
            assert info.rules.proper*.cookie.containsAll(createdCookies), swId
            verifyRuleSectionsAreEmpty(info, ["missing", "excess"])
        }

        and: "Flow validation shows discrepancies"
        def involvedSwitches = flow.retrieveAllEntityPaths().getInvolvedSwitches()
        def totalSwitchRules = 0
        def totalSwitchMeters = 0
        involvedSwitches.each { swId ->
            totalSwitchRules += northbound.getSwitchRules(swId).flowEntries.size()
            totalSwitchMeters += northbound.getAllMeters(swId).meterEntries.size()
        }
        def expectedRulesCount = [
                flow.getFlowRulesCountBySwitch(FlowDirection.FORWARD, involvedSwitches.size(), srcSwitch.isS42FlowRttEnabled()),
                flow.getFlowRulesCountBySwitch(FlowDirection.REVERSE, involvedSwitches.size(), dstSwitch.isS42FlowRttEnabled())]

        def flowValidateResponse = flow.validate()
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

            assert direction.flowRulesTotal == (FlowDirectionType.FORWARD.toString() == direction.direction ?
                    expectedRulesCount[0] : expectedRulesCount[1])
            assert direction.switchRulesTotal == totalSwitchRules
            assert direction.flowMetersTotal == 1
            assert direction.switchMetersTotal == totalSwitchMeters
        }

        when: "Restore correct bandwidth via DB"
        flow.updateFlowBandwidthInDB(flow.maximumBandwidth)

        then: "Misconfigured meters are moved into the 'proper' section"
        def srcSwitchValidateInfoRestored = srcSwitch.validate()
        def dstSwitchValidateInfoRestored = dstSwitch.validate()

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
            def srcSwitchValidateInfoAfterDelete = srcSwitch.validate()
            verifyRuleSectionsAreEmpty(srcSwitchValidateInfoAfterDelete)
            def dstSwitchValidateInfoAfterDelete = dstSwitch.validate()
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
        def cookiesOnDstSw = dstSwitch.rulesManager.getRules()*.cookie
        cleanupManager.addAction(SYNCHRONIZE_SWITCH, {srcSwitch.synchronize()})
        cleanupManager.addAction(SYNCHRONIZE_SWITCH, {dstSwitch.synchronize()})
        srcSwitch.metersManager.delete(srcSwitchCreatedMeterIds[0])

        then: "Meters info/rules are moved into the 'missing' section on the srcSwitch"
        verifyAll(srcSwitch.validate()) {
            it.rules.missing*.cookie.sort() == forwardCookies
            it.rules.proper*.cookie.findAll { !new Cookie(it).serviceFlag }.sort() == untouchedCookiesOnSrcSw
//forward cookie's removed with meter

            it.meters.missing*.meterId == srcSwitchCreatedMeterIds
            //it.meters.missing*.cookie == forwardCookies due to the issue https://github.com/telstra/open-kilda/issues/5360

            Long srcSwitchBurstSize = srcSwitch.getExpectedBurst(flow.maximumBandwidth)
            it.meters.missing.each {
                srcSwitch.verifyRateSizeIsCorrect(flow.maximumBandwidth, it.rate)
                //assert it.flowId == flow.flowId due to the issue https://github.com/telstra/open-kilda/issues/5360
                assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
                srcSwitch.verifyBurstSizeIsCorrect(srcSwitchBurstSize, it.burstSize)
            }
            verifyMeterSectionsAreEmpty(it, ["proper", "misconfigured", "excess"])
            verifyRuleSectionsAreEmpty(it, ["excess"])
        }

        and: "Meters info/rules are NOT moved into the 'missing' section on the dstSwitch"
        verifyAll(dstSwitch.validate()) {
            it.rules.proper*.cookie.sort() == cookiesOnDstSw.sort()

            def properMeters = it.meters.proper.findAll({ dto -> !isDefaultMeter(dto) })
            properMeters*.meterId == dstSwitchCreatedMeterIds
            properMeters.cookie.size() == 1
            //properMeters*.cookie == reverseCookies due to https://github.com/telstra/open-kilda/issues/5360

            Long dstSwitchBurstSize = dstSwitch.getExpectedBurst(flow.maximumBandwidth)
            properMeters.each {
                dstSwitch.verifyRateSizeIsCorrect(flow.maximumBandwidth, it.rate)
                //assert it.flowId == flow.flowId due to https://github.com/telstra/open-kilda/issues/5360
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
        with(srcSwitch.validate()) {
            verifyMeterSectionsAreEmpty(it, ["missing", "misconfigured", "excess"])
            verifyRuleSectionsAreEmpty(it, ["missing", "excess"])
        }

        when: "Delete the flow"
        flow.delete()

        then: "Check that the switch validate request returns empty sections"
        Wrappers.wait(WAIT_OFFSET) {
            def srcSwitchValidateInfoAfterDelete = srcSwitch.validate()
            verifyRuleSectionsAreEmpty(srcSwitchValidateInfoAfterDelete)
            def dstSwitchValidateInfoAfterDelete = dstSwitch.validate()
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
        verifyAll(srcSwitch.validate()) {
            it.rules.missing*.cookie == [ingressCookie]
            it.rules.proper*.cookie.findAll {
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
        with(srcSwitch.validate()) {
            verifyMeterSectionsAreEmpty(it)
            verifyRuleSectionsAreEmpty(it, ["missing", "excess"])
            it.rules.proper*.cookie.findAll { !new Cookie(it).serviceFlag }.sort() == (untouchedCookies + ingressCookie).sort()
        }

        when: "Delete the flow"
        flow.delete()

        then: "Check that the switch validate request returns empty sections"
        Wrappers.wait(WAIT_OFFSET) {
            def srcSwitchValidateInfoAfterDelete = srcSwitch.validate()
            verifyRuleSectionsAreEmpty(srcSwitchValidateInfoAfterDelete)
            def dstSwitchValidateInfoAfterDelete = dstSwitch.validate()
            verifyRuleSectionsAreEmpty(dstSwitchValidateInfoAfterDelete)
        }
    }

    def "Able to validate and sync a switch with missing transit rule"() {
        given: "Two active not neighboring switches"
        def switchPair = switchPairs.all().nonNeighbouring().random()

        and: "Create an intermediate-switch flow"
        def flow = flowFactory.getRandom(switchPair)

        when: "Delete created rules on the transit"
        def involvedSwitches = switches.all().findSwitchesInPath( flow.retrieveAllEntityPaths())
        def transitSw = involvedSwitches.find { !(it.switchId in switchPair.toList().dpId) }
        transitSw.rulesManager.delete(DeleteRulesAction.IGNORE_DEFAULTS)

        then: "Rule info is moved into the 'missing' section"
        verifyAll(transitSw.validate()) {
            it.rules.missing.size() == 2
            it.rules.proper*.cookie.findAll { !new Cookie(it).serviceFlag }.empty
            it.rules.excess.empty
        }
        when: "Synchronize the switch"
        with(transitSw.synchronize(false)) {
            rules.installed.size() == 2
        }
        then: "Repeated validation shows no discrepancies"
        verifyAll(transitSw.validate()) {
            it.rules.proper*.cookie.findAll { !new Cookie(it).serviceFlag }.size() == 2
            verifyRuleSectionsAreEmpty(it, ["missing", "excess"])
        }
        when: "Delete the flow"
        flow.delete()

        then: "Check that the switch validate request returns empty sections on all involved switches"
        Wrappers.wait(WAIT_OFFSET) {
            involvedSwitches.each { sw ->
                def switchValidateInfo = sw.validate()
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
        def srcSwitch = switches.all().findSpecific(switchPair.src.dpId)
        def dstSwitch = switches.all().findSpecific(switchPair.dst.dpId)

        and: "Create an intermediate-switch flow"
        def flow = flowFactory.getRandom(switchPair)

        def rulesOnSrc = srcSwitch.rulesManager.getRules()
        def rulesOnDst = dstSwitch.rulesManager.getRules()

        when: "Delete created rules on the srcSwitch"
        def egressCookie = flow.retrieveDetailsFromDB().reversePath.cookie.value
        srcSwitch.rulesManager.delete(egressCookie)

        then: "Rule info is moved into the 'missing' section on the srcSwitch"
        verifyAll(srcSwitch.validate()) {
            it.rules.missing*.cookie == [egressCookie]
            it.rules.proper.size() == rulesOnSrc.size() - 1
            it.rules.excess.empty
        }

        and: "Rule info is NOT moved into the 'missing' section on the dstSwitch and transit switches"
        def dstSwitchValidateInfo = dstSwitch.validate()
        dstSwitchValidateInfo.rules.proper*.cookie.sort() == rulesOnDst*.cookie.sort()
        verifyRuleSectionsAreEmpty(dstSwitchValidateInfo, ["missing", "excess"])
        def involvedSwitchIds =  switches.all().findSwitchesInPath(flow.retrieveAllEntityPaths())
        def transitSwitches = involvedSwitchIds.findAll {
            !(it.switchId in switchPair.toList().dpId) && !it.isOf12Version()
        }
        transitSwitches.each { sw ->
            def transitSwitchValidateInfo = sw.validate()
            assert transitSwitchValidateInfo.rules.proper*.cookie.findAll { !new Cookie(it).serviceFlag }.size() == 2
            verifyRuleSectionsAreEmpty(transitSwitchValidateInfo, ["missing", "excess"])
        }

        when: "Synchronize the switch"
        with(srcSwitch.synchronize(false)) {
            rules.installed == [egressCookie]
        }

        then: "Repeated validation shows no discrepancies"
        verifyAll(dstSwitch.validate()) {
            it.rules.proper*.cookie.sort() == rulesOnDst*.cookie.sort()
            verifyRuleSectionsAreEmpty(it, ["missing", "excess"])
        }

        when: "Delete the flow"
        flow.delete()

        then: "Check that the switch validate request returns empty sections on all involved switches"
        Wrappers.wait(WAIT_OFFSET) {
            involvedSwitchIds.findAll { !it.isOf12Version() }.each { sw ->
                def switchValidateInfo = sw.validate()
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
        def transitSwitches = involvedSwitches.findAll { !(it.switchId in switchPair.toList().dpId) }
        def srcSwitch = involvedSwitches.find { it.switchId == switchPair.src.dpId }
        def dstSwitch = involvedSwitches.find { it.switchId == switchPair.dst.dpId }

        def createdCookiesSrcSw = srcSwitch.rulesManager.getRules().cookie
        def createdCookiesDstSw = dstSwitch.rulesManager.getRules().cookie
        def createdCookiesTransitSwitch = transitSwitches.first().rulesManager.getRules().cookie

        when: "Create excess rules on switches"
        def producer = new KafkaProducer(producerProps)
        //pick a meter id which is not yet used on src switch
        def excessMeterId = ((MIN_FLOW_METER_ID..100) - srcSwitch.metersManager.getMeters().meterId).first()
        cleanupManager.addAction(SYNCHRONIZE_SWITCH, { involvedSwitches.each { it.synchronize() }})
        producer.send(new ProducerRecord(speakerTopic, dstSwitch.switchId.toString(), buildMessage(
                FlowSpeakerData.builder()
                        .switchId(dstSwitch.switchId)
                        .ofVersion(OfVersion.of(dstSwitch.ofVersion))
                        .cookie(new Cookie(1L))
                        .table(OfTable.EGRESS)
                        .priority(101)
                        .instructions(Instructions.builder().build())
                        .build()).toJson())).get()
        transitSwitches.findAll { !it.isOf12Version() }.each { transitSw ->
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
                def involvedSwitchValidateInfo = sw.validate()
                if (sw.switchId == switchPair.src.dpId) {
                    assert involvedSwitchValidateInfo.rules.proper*.cookie.sort() == createdCookiesSrcSw.sort()
                } else if (sw.switchId == switchPair.dst.dpId) {
                    assert involvedSwitchValidateInfo.rules.proper*.cookie.sort() == createdCookiesDstSw.sort()
                } else {
                    assert involvedSwitchValidateInfo.rules.proper*.cookie.sort() == createdCookiesTransitSwitch.sort()
                }
                verifyRuleSectionsAreEmpty(involvedSwitchValidateInfo, ["missing"])
                assert involvedSwitchValidateInfo.rules.excess.size() == 1
                assert involvedSwitchValidateInfo.rules.excess.cookie == [1L]
            }
        }
        and: "Excess meter is shown on the srcSwitch only"
        Long burstSize = flow.maximumBandwidth
        def validateSwitchInfo = srcSwitch.validate()
        assert validateSwitchInfo.meters.excess.size() == 1
        validateSwitchInfo.meters.excess.each {
            assert it.meterId == excessMeterId
            assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            srcSwitch.verifyRateSizeIsCorrect(flow.maximumBandwidth, it.rate)
            dstSwitch.verifyBurstSizeIsCorrect(burstSize, it.burstSize)
        }
        involvedSwitches.findAll{ it.switchId != switchPair.src.dpId && !it.isOf12Version() }.each { sw ->
            assert sw.validate().meters.excess.empty
        }
        when: "Try to synchronize every involved switch"
        then: "System deletes excess rules and meters"
        involvedSwitches.each { sw ->
            def syncResponse = sw.synchronize()
            if( !sw.isOf12Version()){
                assert syncResponse.rules.excess.size() == 1
                assert syncResponse.rules.excess[0] == 1L
                assert syncResponse.rules.removed.size() == 1
                assert syncResponse.rules.removed[0] == 1L
            }

            if(sw == srcSwitch){
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
                def switchValidateInfo = sw.validate()
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
        def flow = flowFactory.getBuilder(switchPair)
                .withEncapsulationType(FlowEncapsulationType.VXLAN).build()
                .create()

        and: "Remove required rules and meters from switches"
        def flowInfoFromDb = flow.retrieveDetailsFromDB()
        def involvedSwitches = switches.all().findSwitchesInPath(flow.retrieveAllEntityPaths())
        def transitSwitch = involvedSwitches.findAll { !(it.switchId in switchPair.toList().dpId) }
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
        involvedSwitches.each { sw -> sw.rulesManager.delete(DeleteRulesAction.IGNORE_DEFAULTS) }
        terminalSwitches.each { sw -> sw.metersManager.delete(metersMap[sw.switchId][0])}

        Wrappers.wait(RULES_DELETION_TIME) {
            involvedSwitches.each { sw ->
                def validateResponse = sw.validate()
                def rulesCount = sw.collectFlowRelatedRulesAmount(flow)

                assert validateResponse.rules.missing.size() == rulesCount
                assert validateResponse.rules.missing.cookieHex.size() == rulesCount

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
            if(sw in terminalSwitches){
                assert syncResponse.meters.proper.findAll { !it.defaultMeter }.size() == 0
                assert syncResponse.meters.excess.size() == 0
                assert syncResponse.meters.missing*.meterId == metersMap[sw.switchId]
                assert syncResponse.meters.removed.size() == 0
                assert syncResponse.meters.installed*.meterId == metersMap[sw.switchId]
            }
        }


        and: "Switch validation doesn't complain about missing rules and meters"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            assert validateAndCollectFoundDiscrepancies(involvedSwitches).isEmpty()
        }

        and: "Rules are synced correctly"
        // ingressRule should contain "pushVxlan"
        // egressRule should contain "tunnel-id"
        with(terminalSwitches.find { it.switchId in switchPair.src.dpId }.rulesManager.getRules()) { rules ->
            assert rules.find {
                it.cookie == flowInfoFromDb.forwardPath.cookie.value
            }.instructions.applyActions.pushVxlan
            assert rules.find {
                it.cookie == flowInfoFromDb.reversePath.cookie.value
            }.match.tunnelId
        }
        with(terminalSwitches.find { it.switchId in switchPair.dst.dpId }.rulesManager.getRules()) { rules ->
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
        def flow = flowFactory.getBuilder(swPair)
                .withProtectedPath(true).build()
                .create()

        def flowPathInfo = flow.retrieveAllEntityPaths()
        def allSwitches = switches.all().findSwitchesInPath(flowPathInfo)
        def srcSwitch = allSwitches.find { it.switchId == swPair.src.dpId }
        def rulesPerSwitch = allSwitches.collectEntries {
            [it.switchId, it.rulesManager.getRules().cookie.sort()]
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
        def srcSwitchRules = srcSwitch.rulesManager.getRules().findAll { !new Cookie(it.cookie).serviceFlag }
        def ruleToDelete = srcSwitchRules.find {
            //specifying protectedPath[0](src.inputPort) and protectedPath[1](src.outputPort) as protected path for FORWARD direction is used
            it.instructions?.applyActions?.flowOutput == protectedPath[0].portNo.toString() &&
                    it.match.inPort == protectedPath[1].portNo.toString()
        }.cookie
        srcSwitch.rulesManager.delete(ruleToDelete)

        then: "Deleted rule is moved to the 'missing' section on the srcSwitch"
        verifyAll(srcSwitch.validate()) {
            it.rules.proper*.cookie.sort() == (rulesPerSwitch[swPair.src.dpId] - ruleToDelete).sort()
            it.rules.missing*.cookie == [ruleToDelete]
            it.rules.excess.empty
        }
        and: "Rest switches are not affected by deleting the rule on the srcSwitch"
        allSwitches.findAll { it.switchId != srcSwitch.switchId }.each { sw ->
            def validation = sw.validate()
            assert validation.rules.proper*.cookie.sort() == rulesPerSwitch[sw.switchId]
            assert validation.rules.missing.empty
            assert validation.rules.excess.empty
        }
        when: "Synchronize switch with a missing protected path egress rule"
        with(srcSwitch.synchronize(false)) {
            rules.installed == [ruleToDelete]
        }
        then: "Switch validation no longer shows missing rules"
        verifyAll(srcSwitch.validate()) {
            verifyRuleSectionsAreEmpty(it, ["missing", "excess"])
            it.rules.proper*.cookie.sort() == rulesPerSwitch[srcSwitch.switchId]
        }
    }

    def "Able to validate and sync a missing 'connected device' #data.descr rule"() {
        given: "A flow with enabled connected devices"
        def swPair = switchPairs.all().random()
        def flow = flowFactory.getBuilder(swPair)
                .withDetectedDevicesOnDst(true, true).build()
                .create()

        expect: "Switch validation puts connected device lldp rule into 'proper' section"
        def dstSwitch = switches.all().findSpecific(swPair.dst.dpId)
        def deviceCookie = dstSwitch.rulesManager.getRules().find(data.cookieSearchClosure).cookie
        with(dstSwitch.validate()) {
            it.rules.proper*.cookie.contains(deviceCookie)
        }

        when: "Remove the connected device rule"
        dstSwitch.rulesManager.delete(deviceCookie)

        then: "Switch validation puts connected device rule into 'missing' section"
        verifyAll(dstSwitch.validate()) {
            !it.rules.proper*.cookie.contains(deviceCookie)
            it.rules.missing*.cookie.contains(deviceCookie)
            it.rules.missing*.cookieHex.contains(Long.toHexString(deviceCookie).toUpperCase())
        }

        when: "Synchronize the switch"
        with(dstSwitch.synchronize(false)) {
            it.rules.installed == [deviceCookie]
        }

        then: "Switch validation no longer shows any discrepancies in rules nor meters"
        verifyAll(dstSwitch.validate()) {
            it.rules.proper*.cookie.contains(deviceCookie)
            it.rules.missing.empty
            it.rules.excess.empty
            it.meters.missing.empty
            it.meters.excess.empty
        }

        when: "Delete the flow"
        flow.delete()

        then: "Switch validation is empty"
        verifyAll(dstSwitch.validate()) {
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

    def "Able to filter results using request query, and asExpected field verification"() {
        given: "Create a flow"
        def (SwitchExtended srcSwitch, SwitchExtended dstSwitch) = switches.all().notOF12Version()
        def flow = flowFactory.getRandom(srcSwitch, dstSwitch)

        when: "Perform result filtering"
        def srcSwitchValidateInfo = srcSwitch.validate(include, null)
        def dstSwitchValidateInfo = dstSwitch.validate()

        and: "Result contains only included sections"
        verifySectionInSwitchValidationInfo(srcSwitchValidateInfo, sectionsToVerifyPresence)
        verifySectionsAsExpectedFields(srcSwitchValidateInfo, sectionsToVerifyPresence)

        verifySectionInSwitchValidationInfo(dstSwitchValidateInfo, sectionsToVerifyPresence)
        verifySectionsAsExpectedFields(dstSwitchValidateInfo, sectionsToVerifyPresence)

        then: "Delete the flow"
        flow.delete()

        and: "Check that the switch validate request returns empty sections"
        Wrappers.wait(WAIT_OFFSET) {
            def srcSwitchValidateInfoAfterDelete = srcSwitch.validate()
            verifyRuleSectionsAreEmpty(srcSwitchValidateInfoAfterDelete)
            def dstSwitchValidateInfoAfterDelete = dstSwitch.validate()
            verifyRuleSectionsAreEmpty(dstSwitchValidateInfoAfterDelete)
        }

        where:
        include << ["METERS", "METERS|GROUPS", "METERS|GROUPS|RULES"]
        sectionsToVerifyPresence << [["meters"], ["meters", "groups"], ["meters", "groups", "rules"]]
    }

    @Tags([VIRTUAL, LOW_PRIORITY])
    def "Able to validate switch using #apiVersion API when GRPC is down"() {
        given: "Random switch without LAG feature enabled"
        def aSwitch = switches.all().withoutLagSupport().random()

        and: "GRPC container is down"
        def dockerHelper = new DockerHelper(dockerHost)
        def grpcContainerId = dockerHelper.getContainerId(ContainerName.GRPC)
        dockerHelper.pauseContainer(grpcContainerId)

        and: "Switch has a new feature artificially added directly to DB"
        def originalFeatures = aSwitch.getDbFeatures() as Set
        aSwitch.setFeaturesInDb(originalFeatures + SwitchFeature.LAG)

        when: "Validate switch"
        def validationResult = validate(aSwitch)

        then: "Validation is successful"
        validationResult.getLogicalPorts().getError() ==
                "Timeout for waiting response on DumpLogicalPortsRequest() Details: Error in SpeakerWorkerService"

        cleanup:
        dockerHelper.resumeContainer(grpcContainerId)

        where:
        apiVersion | validate
        "V2"       | { it.validate() }
        "V1"       | { it.validateV1() }
    }

}
