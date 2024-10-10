package org.openkilda.functionaltests.spec.switches

import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.extension.tags.Tag.VIRTUAL
import static org.openkilda.functionaltests.helpers.SwitchHelper.isDefaultMeter
import static org.openkilda.functionaltests.helpers.SwitchHelper.isServer42Supported
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.SYNCHRONIZE_SWITCH
import static org.openkilda.model.MeterId.MAX_SYSTEM_RULE_METER_ID
import static org.openkilda.model.MeterId.MIN_FLOW_METER_ID
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.tools.KafkaUtils.buildMessage

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.DockerHelper
import org.openkilda.functionaltests.helpers.SwitchHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.ContainerName
import org.openkilda.functionaltests.helpers.model.FlowDirection
import org.openkilda.functionaltests.helpers.model.FlowEncapsulationType
import org.openkilda.functionaltests.helpers.model.SwitchRulesFactory
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.messaging.model.FlowDirectionType
import org.openkilda.messaging.payload.flow.DetectConnectedDevicesPayload
import org.openkilda.model.MeterId
import org.openkilda.model.SwitchFeature
import org.openkilda.model.SwitchId
import org.openkilda.model.cookie.Cookie
import org.openkilda.model.cookie.CookieBase.CookieType
import org.openkilda.rulemanager.FlowSpeakerData
import org.openkilda.rulemanager.Instructions
import org.openkilda.rulemanager.MeterFlag
import org.openkilda.rulemanager.MeterSpeakerData
import org.openkilda.rulemanager.OfTable
import org.openkilda.rulemanager.OfVersion
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

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
    @Autowired
    @Shared
    SwitchRulesFactory switchRulesFactory

    def setupSpec() {
        deleteAnyFlowsLeftoversIssue5480()
    }

    def "Able to validate and sync a terminating switch with proper rules and meters"() {
        given: "A flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches.findAll { it.ofVersion != "OF_12" }
        def flow = flowFactory.getRandom(srcSwitch, dstSwitch)

        expect: "Validate switch for src and dst contains expected meters data in 'proper' section"
        def srcSwitchValidateInfo = switchHelper.validate(srcSwitch.dpId)
        def dstSwitchValidateInfo = switchHelper.validate(dstSwitch.dpId)
        def srcSwitchCreatedCookies = getCookiesWithMeter(srcSwitch.dpId)
        def dstSwitchCreatedCookies = getCookiesWithMeter(dstSwitch.dpId)

        srcSwitchValidateInfo.meters.proper*.meterId.containsAll(getCreatedMeterIds(srcSwitch.dpId))
        dstSwitchValidateInfo.meters.proper*.meterId.containsAll(getCreatedMeterIds(dstSwitch.dpId))

//        due to the issue https://github.com/telstra/open-kilda/issues/5360
//        srcSwitchValidateInfo.meters.proper*.cookie.containsAll(srcSwitchCreatedCookies)
//        dstSwitchValidateInfo.meters.proper*.cookie.containsAll(dstSwitchCreatedCookies)

        def srcSwitchProperMeters = srcSwitchValidateInfo.meters.proper.findAll({ !isDefaultMeter(it) })
        def dstSwitchProperMeters = dstSwitchValidateInfo.meters.proper.findAll({ !isDefaultMeter(it) })

        [[srcSwitch, srcSwitchProperMeters], [dstSwitch, dstSwitchProperMeters]].each { sw, meters ->
            meters.each {
                SwitchHelper.verifyRateSizeIsCorrect(sw, flow.maximumBandwidth, it.rate)
               // assert it.flowId == flow.flowId due to the issue https://github.com/telstra/open-kilda/issues/5360
                assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            }
        }

        Long srcSwitchBurstSize = switchHelper.getExpectedBurst(srcSwitch.dpId, flow.maximumBandwidth)
        Long dstSwitchBurstSize = switchHelper.getExpectedBurst(dstSwitch.dpId, flow.maximumBandwidth)
        switchHelper.verifyBurstSizeIsCorrect(srcSwitch, srcSwitchBurstSize,
                srcSwitchProperMeters*.burstSize[0])
        switchHelper.verifyBurstSizeIsCorrect(dstSwitch, dstSwitchBurstSize,
                dstSwitchProperMeters*.burstSize[0])

        and: "The rest fields in the 'meter' section are empty"
        srcSwitchValidateInfo.verifyMeterSectionsAreEmpty(["missing", "misconfigured", "excess"])
        dstSwitchValidateInfo.verifyMeterSectionsAreEmpty(["missing", "misconfigured", "excess"])

        and: "Created rules are stored in the 'proper' section"
        def createdCookies = srcSwitchCreatedCookies + dstSwitchCreatedCookies
        [srcSwitchValidateInfo, dstSwitchValidateInfo].each {
            it.rules.proper.containsAll(createdCookies)
        }

        and: "The rest fields in the 'rule' section are empty"
        srcSwitchValidateInfo.verifyRuleSectionsAreEmpty(["missing", "excess"])
        dstSwitchValidateInfo.verifyRuleSectionsAreEmpty(["missing", "excess"])

        and: "Able to perform switch sync which does nothing"
        verifyAll(switchHelper.synchronize(srcSwitch.dpId, true)) {
            it.rules.removed.empty
            it.rules.installed.empty
            it.meters.removed.empty
            it.meters.installed.empty
        }

        when: "Delete the flow"
        flow.delete()

        then: "Switch validate request returns only default rules information"
        Wrappers.wait(WAIT_OFFSET) {
            def srcSwitchValidateInfoAfterDelete = switchHelper.validate(srcSwitch.dpId)
            def dstSwitchValidateInfoAfterDelete = switchHelper.validate(dstSwitch.dpId)
            srcSwitchValidateInfoAfterDelete.verifyRuleSectionsAreEmpty()
            dstSwitchValidateInfoAfterDelete.verifyRuleSectionsAreEmpty()

        }
    }

    def "Able to validate and sync a transit switch with proper rules and no meters"() {
        given: "Two active not neighboring switches"
        def switchPair = switchPairs.all().nonNeighbouring().random()

        when: "Create an intermediate-switch flow"
        def flow = flowFactory.getRandom(switchPair)
        def flowPathInfo = flow.retrieveAllEntityPaths()
        def involvedSwitches = flowPathInfo.getInvolvedIsls()
                .collect { [it.srcSwitch, it.dstSwitch] }.flatten().unique() as List<Switch>

        then: "The intermediate switch does not contain any information about meter"
        def switchToValidate = involvedSwitches[1..-2].find { !it.dpId.description.contains("OF_12") }
        def intermediateSwitchValidateInfo = switchHelper.validate(switchToValidate.dpId)
        intermediateSwitchValidateInfo.verifyMeterSectionsAreEmpty()

        and: "Rules are stored in the 'proper' section on the transit switch"
        intermediateSwitchValidateInfo.rules.proper.findAll { !new Cookie(it.cookie).serviceFlag }.size() == 2
        intermediateSwitchValidateInfo.verifyRuleSectionsAreEmpty(["missing", "excess"])

        and: "Able to perform switch sync which does nothing"
        verifyAll(switchHelper.synchronize(switchToValidate.dpId, true)) {
            it.rules.removed.empty
            it.rules.installed.empty
            it.meters.removed.empty
            it.meters.installed.empty
        }

        when: "Delete the flow"
        flow.delete()

        then: "Check that the switch validate request returns empty sections"
        involvedSwitches.each { sw ->
            def switchValidateInfo = switchHelper.validate(sw.dpId)
            switchValidateInfo.verifyRuleSectionsAreEmpty()
            if (sw.description.contains("OF_13")) {
                switchValidateInfo.verifyMeterSectionsAreEmpty()
            }
        }
    }

    def "Able to validate switch with 'misconfigured' meters"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches.findAll { it.ofVersion != "OF_12" }
        def flow = flowFactory.getRandom(srcSwitch, dstSwitch)
        def srcSwitchCreatedMeterIds = getCreatedMeterIds(srcSwitch.dpId)
        def dstSwitchCreatedMeterIds = getCreatedMeterIds(dstSwitch.dpId)

        and: "Change bandwidth for the created flow directly in DB so that system thinks the installed meter is \
misconfigured"
        def newBandwidth = flow.maximumBandwidth + 100
        cleanupManager.addAction(SYNCHRONIZE_SWITCH, {switchHelper.synchronize(srcSwitch.dpId)})
        cleanupManager.addAction(SYNCHRONIZE_SWITCH, {switchHelper.synchronize(dstSwitch.dpId)})
        /** at this point meter is set for given flow. Now update flow bandwidth directly via DB,
         it is done just for moving meter from the 'proper' section into the 'misconfigured'*/
        flow.updateFlowBandwidthInDB(newBandwidth)
        //at this point existing meters do not correspond with the flow

        and: "Validate src and dst switches"
        def srcSwitchValidateInfo = switchHelper.validate(srcSwitch.dpId)
        def dstSwitchValidateInfo = switchHelper.validate(dstSwitch.dpId)

        then: "Meters info is moved into the 'misconfigured' section"
        def srcSwitchCreatedCookies = getCookiesWithMeter(srcSwitch.dpId)
        def dstSwitchCreatedCookies = getCookiesWithMeter(dstSwitch.dpId)

        srcSwitchValidateInfo.meters.misconfigured*.expected.meterId.containsAll(srcSwitchCreatedMeterIds)
        dstSwitchValidateInfo.meters.misconfigured*.expected.meterId.containsAll(dstSwitchCreatedMeterIds)

//        due to the issue due to the issue https://github.com/telstra/open-kilda/issues/5360
//        srcSwitchValidateInfo.meters.misconfigured*.expected.cookie.containsAll(srcSwitchCreatedCookies)
//        dstSwitchValidateInfo.meters.misconfigured*.expected.cookie.containsAll(dstSwitchCreatedCookies)

        [[srcSwitch, srcSwitchValidateInfo], [dstSwitch, dstSwitchValidateInfo]].each { sw, validation ->
            assert validation.meters.misconfigured.id.size() == 1
            validation.meters.misconfigured.each {
                SwitchHelper.verifyRateSizeIsCorrect(sw, flow.maximumBandwidth, it.discrepancies.rate)
                //assert it.expected.flowId == flow.flowId due to the issue https://github.com/telstra/open-kilda/issues/5360
                assert ["KBPS", "BURST", "STATS"].containsAll(it.expected.flags)
            }
        }

        Long srcSwitchBurstSize = switchHelper.getExpectedBurst(srcSwitch.dpId, flow.maximumBandwidth)
        Long dstSwitchBurstSize = switchHelper.getExpectedBurst(dstSwitch.dpId, flow.maximumBandwidth)
        switchHelper.verifyBurstSizeIsCorrect(srcSwitch, srcSwitchBurstSize,
                srcSwitchValidateInfo.meters.misconfigured*.discrepancies.burstSize[0])
        switchHelper.verifyBurstSizeIsCorrect(dstSwitch, dstSwitchBurstSize,
                dstSwitchValidateInfo.meters.misconfigured*.discrepancies.burstSize[0])

        and: "Reason is specified why meter is misconfigured"
        [srcSwitchValidateInfo, dstSwitchValidateInfo].each {
            it.meters.misconfigured.each {
                assert it.discrepancies.rate == flow.maximumBandwidth
                assert it.expected.rate == newBandwidth
            }
        }

        and: "The rest fields of 'meter' section are empty"
        srcSwitchValidateInfo.verifyMeterSectionsAreEmpty(["proper", "missing", "excess"])
        dstSwitchValidateInfo.verifyMeterSectionsAreEmpty(["proper", "missing", "excess"])

        and: "Created rules are still stored in the 'proper' section"
        def createdCookies = srcSwitchCreatedCookies + dstSwitchCreatedCookies
        [[srcSwitch.dpId, srcSwitchValidateInfo], [dstSwitch.dpId, dstSwitchValidateInfo]].each { swId, info ->
            assert info.rules.proper*.cookie.containsAll(createdCookies), swId
            info.verifyRuleSectionsAreEmpty(["missing", "excess"])
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
                flow.getFlowRulesCountBySwitch(FlowDirection.FORWARD, involvedSwitches.size(), isServer42Supported(srcSwitch.dpId)),
                flow.getFlowRulesCountBySwitch(FlowDirection.REVERSE, involvedSwitches.size(), isServer42Supported(dstSwitch.dpId))]

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
            Long newBurstSize = switchHelper.getExpectedBurst(sw.dpId, newBandwidth)
            switchHelper.verifyBurstSizeIsCorrect(sw, newBurstSize, burst.expectedValue.toLong())
            switchHelper.verifyBurstSizeIsCorrect(sw, switchBurstSize, burst.actualValue.toLong())

            assert direction.flowRulesTotal == (FlowDirectionType.FORWARD.toString() == direction.direction ?
                    expectedRulesCount[0] : expectedRulesCount[1])
            assert direction.switchRulesTotal == totalSwitchRules
            assert direction.flowMetersTotal == 1
            assert direction.switchMetersTotal == totalSwitchMeters
        }

        when: "Restore correct bandwidth via DB"
        flow.updateFlowBandwidthInDB(flow.maximumBandwidth)

        then: "Misconfigured meters are moved into the 'proper' section"
        def srcSwitchValidateInfoRestored = switchHelper.validate(srcSwitch.dpId)
        def dstSwitchValidateInfoRestored = switchHelper.validate(dstSwitch.dpId)

        srcSwitchValidateInfoRestored.meters.proper*.meterId.containsAll(srcSwitchCreatedMeterIds)
        dstSwitchValidateInfoRestored.meters.proper*.meterId.containsAll(dstSwitchCreatedMeterIds)
        srcSwitchValidateInfoRestored.verifyMeterSectionsAreEmpty(["missing", "misconfigured", "excess"])
        dstSwitchValidateInfoRestored.verifyMeterSectionsAreEmpty(["missing", "misconfigured", "excess"])

        and: "Flow validation shows no discrepancies"
        flow.validateAndCollectDiscrepancies().isEmpty()

        when: "Delete the flow"
        flow.delete()

        then: "Check that the switch validate request returns empty sections"
        Wrappers.wait(WAIT_OFFSET) {
            def srcSwitchValidateInfoAfterDelete = switchHelper.validate(srcSwitch.dpId)
            def dstSwitchValidateInfoAfterDelete = switchHelper.validate(dstSwitch.dpId)
            srcSwitchValidateInfoAfterDelete.verifyRuleSectionsAreEmpty()
            dstSwitchValidateInfoAfterDelete.verifyRuleSectionsAreEmpty()
        }
    }

    def "Able to validate and sync a switch with missing ingress rule + meter"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches.findAll { it.ofVersion != "OF_12" }
        def flow = flowFactory.getRandom(srcSwitch, dstSwitch)
        def srcSwitchCreatedMeterIds = getCreatedMeterIds(srcSwitch.dpId)
        def dstSwitchCreatedMeterIds = getCreatedMeterIds(dstSwitch.dpId)

        and: "Remove created meter on the srcSwitch"
        def forwardCookies = getCookiesWithMeter(srcSwitch.dpId)
        def reverseCookies = getCookiesWithMeter(dstSwitch.dpId)
        def sharedCookieOnSrcSw = switchRulesFactory.get(srcSwitch.dpId).getRules().findAll {
            new Cookie(it.cookie).getType() in [CookieType.SHARED_OF_FLOW, CookieType.SERVER_42_FLOW_RTT_INGRESS]
        }?.cookie
        def untouchedCookiesOnSrcSw = (reverseCookies + sharedCookieOnSrcSw).sort()
        def cookiesOnDstSw = switchRulesFactory.get(dstSwitch.dpId).getRules()*.cookie
        cleanupManager.addAction(SYNCHRONIZE_SWITCH, {switchHelper.synchronize(srcSwitch.dpId)})
        cleanupManager.addAction(SYNCHRONIZE_SWITCH, {switchHelper.synchronize(dstSwitch.dpId)})
        northbound.deleteMeter(srcSwitch.dpId, srcSwitchCreatedMeterIds[0])

        then: "Meters info/rules are moved into the 'missing' section on the srcSwitch"
        verifyAll(switchHelper.validate(srcSwitch.dpId)) {
            it.rules.missing*.cookie.sort() == forwardCookies
            it.rules.proper*.cookie.findAll { !new Cookie(it).serviceFlag }.sort() == untouchedCookiesOnSrcSw
//forward cookie's removed with meter

            it.meters.missing*.meterId == srcSwitchCreatedMeterIds
            //it.meters.missing*.cookie == forwardCookies due to the issue https://github.com/telstra/open-kilda/issues/5360

            Long srcSwitchBurstSize = switchHelper.getExpectedBurst(srcSwitch.dpId, flow.maximumBandwidth)
            it.meters.missing.each {
                SwitchHelper.verifyRateSizeIsCorrect(srcSwitch, flow.maximumBandwidth, it.rate)
                //assert it.flowId == flow.flowId due to the issue https://github.com/telstra/open-kilda/issues/5360
                assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
                switchHelper.verifyBurstSizeIsCorrect(srcSwitch, srcSwitchBurstSize, it.burstSize)
            }
            it.verifyMeterSectionsAreEmpty(["proper", "misconfigured", "excess"])
            it.verifyRuleSectionsAreEmpty(["excess"])
        }

        and: "Meters info/rules are NOT moved into the 'missing' section on the dstSwitch"
        verifyAll(switchHelper.validate(dstSwitch.dpId)) {
            it.rules.proper*.cookie.sort() == cookiesOnDstSw.sort()

            def properMeters = it.meters.proper.findAll({ dto -> !isDefaultMeter(dto) })
            properMeters*.meterId == dstSwitchCreatedMeterIds
            properMeters.cookie.size() == 1
            //properMeters*.cookie == reverseCookies due to https://github.com/telstra/open-kilda/issues/5360

            Long dstSwitchBurstSize = switchHelper.getExpectedBurst(dstSwitch.dpId, flow.maximumBandwidth)
            properMeters.each {
                SwitchHelper.verifyRateSizeIsCorrect(dstSwitch, flow.maximumBandwidth, it.rate)
                //assert it.flowId == flow.flowId due to https://github.com/telstra/open-kilda/issues/5360
                assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
                switchHelper.verifyBurstSizeIsCorrect(dstSwitch, dstSwitchBurstSize, it.burstSize)
            }
            it.verifyMeterSectionsAreEmpty(["missing", "misconfigured", "excess"])
            it.verifyRuleSectionsAreEmpty(["missing", "excess"])
        }

        when: "Synchronize switch with missing rule and meter"
        verifyAll(switchHelper.synchronize(srcSwitch.dpId, false)) {
            it.rules.installed == forwardCookies
            it.meters.installed*.meterId == srcSwitchCreatedMeterIds as List<Long>
        }

        then: "Repeated validation shows no missing entities"
        with(switchHelper.validate(srcSwitch.dpId)) {
            it.verifyMeterSectionsAreEmpty(["missing", "misconfigured", "excess"])
            it.verifyRuleSectionsAreEmpty(["missing", "excess"])
        }

        when: "Delete the flow"
        flow.delete()

        then: "Check that the switch validate request returns empty sections"
        Wrappers.wait(WAIT_OFFSET) {
            def srcSwitchValidateInfoAfterDelete = switchHelper.validate(srcSwitch.dpId)
            def dstSwitchValidateInfoAfterDelete = switchHelper.validate(dstSwitch.dpId)
            srcSwitchValidateInfoAfterDelete.verifyRuleSectionsAreEmpty()
            dstSwitchValidateInfoAfterDelete.verifyRuleSectionsAreEmpty()
        }
    }

    def "Able to validate and sync a switch with missing ingress rule (unmetered)"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches.findAll { it.ofVersion != "OF_12" }
        def flow = flowFactory.getBuilder(srcSwitch, dstSwitch)
                .withBandwidth(0)
                .withIgnoreBandwidth(true).build()
                .create()

        and: "Remove ingress rule on the srcSwitch"
        def flowDBInfo = flow.retrieveDetailsFromDB()
        def ingressCookie = flowDBInfo.forwardPath.cookie.value
        def egressCookie = flowDBInfo.reversePath.cookie.value
        switchHelper.deleteSwitchRules(srcSwitch.dpId, ingressCookie)

        then: "Ingress rule is moved into the 'missing' section on the srcSwitch"
        def sharedCookieOnSrcSw = switchRulesFactory.get(srcSwitch.dpId).getRules().findAll {
            new Cookie(it.cookie).getType() in [CookieType.SHARED_OF_FLOW, CookieType.SERVER_42_FLOW_RTT_INGRESS]
        }?.cookie
        def untouchedCookies = ([egressCookie] + sharedCookieOnSrcSw).sort()
        verifyAll(switchHelper.validate(srcSwitch.dpId)) {
            it.rules.missing*.cookie == [ingressCookie]
            it.rules.proper*.cookie.findAll {
                def cookie = new Cookie(it)
                !cookie.serviceFlag || cookie.type == CookieType.SHARED_OF_FLOW
            }.sort() == untouchedCookies
            it.verifyMeterSectionsAreEmpty()
            it.verifyRuleSectionsAreEmpty(["excess"])
        }

        when: "Synchronize switch with missing unmetered rule"
        with(switchHelper.synchronize(srcSwitch.dpId, false)) {
            rules.installed == [ingressCookie]
        }

        then: "Repeated validation shows no missing entities"
        with(switchHelper.validate(srcSwitch.dpId)) {
            it.verifyMeterSectionsAreEmpty()
            it.verifyRuleSectionsAreEmpty(["missing", "excess"])
            it.rules.proper*.cookie.findAll { !new Cookie(it).serviceFlag }.sort() == (untouchedCookies + ingressCookie).sort()
        }

        when: "Delete the flow"
        flow.delete()

        then: "Check that the switch validate request returns empty sections"
        Wrappers.wait(WAIT_OFFSET) {
            def srcSwitchValidateInfoAfterDelete = switchHelper.validate(srcSwitch.dpId)
            def dstSwitchValidateInfoAfterDelete = switchHelper.validate(dstSwitch.dpId)
            srcSwitchValidateInfoAfterDelete.verifyRuleSectionsAreEmpty()
            dstSwitchValidateInfoAfterDelete.verifyRuleSectionsAreEmpty()
        }
    }

    def "Able to validate and sync a switch with missing transit rule"() {
        given: "Two active not neighboring switches"
        def switchPair = switchPairs.all().nonNeighbouring().random()

        and: "Create an intermediate-switch flow"
        def flow = flowFactory.getRandom(switchPair)

        when: "Delete created rules on the transit"
        List<Switch> involvedSwitches = flow.retrieveAllEntityPaths().getInvolvedIsls()
                .collect { [it.srcSwitch, it.dstSwitch] }.flatten().unique() as List<Switch>
        def transitSw = involvedSwitches[1]
        switchHelper.deleteSwitchRules(transitSw.dpId, DeleteRulesAction.IGNORE_DEFAULTS)

        then: "Rule info is moved into the 'missing' section"
        verifyAll(switchHelper.validate(transitSw.dpId)) {
            it.rules.missing.size() == 2
            it.rules.proper*.cookie.findAll {
                !new Cookie(it).serviceFlag
            }.empty
            it.rules.excess.empty
        }
        when: "Synchronize the switch"
        with(switchHelper.synchronize(transitSw.dpId, false)) {
            rules.installed.size() == 2
        }
        then: "Repeated validation shows no discrepancies"
        verifyAll(switchHelper.validate(transitSw.dpId)) {
            it.rules.proper*.cookie.findAll { !new Cookie(it).serviceFlag }.size() == 2
            it.verifyRuleSectionsAreEmpty(["missing", "excess"])
        }
        when: "Delete the flow"
        flow.delete()

        then: "Check that the switch validate request returns empty sections on all involved switches"
        Wrappers.wait(WAIT_OFFSET) {
            involvedSwitches.each { sw ->
                def switchValidateInfo = switchHelper.validate(sw.dpId)
                switchValidateInfo.verifyRuleSectionsAreEmpty()
                if (!sw.description.contains("OF_12")) {
                    switchValidateInfo.verifyMeterSectionsAreEmpty()
                }
            }
        }
    }

    def "Able to validate and sync a switch with missing egress rule"() {
        given: "Two active not neighboring switches"
        def switchPair = switchPairs.all().nonNeighbouring().random()

        and: "Create an intermediate-switch flow"
        def flow = flowFactory.getRandom(switchPair)

        def rulesOnSrc = northbound.getSwitchRules(switchPair.src.dpId).flowEntries
        def rulesOnDst = northbound.getSwitchRules(switchPair.dst.dpId).flowEntries

        when: "Delete created rules on the srcSwitch"
        def egressCookie = flow.retrieveDetailsFromDB().reversePath.cookie.value
        switchHelper.deleteSwitchRules(switchPair.src.dpId, egressCookie)

        then: "Rule info is moved into the 'missing' section on the srcSwitch"
        verifyAll(switchHelper.validate(switchPair.src.dpId)) {
            it.rules.missing*.cookie == [egressCookie]
            it.rules.proper.size() == rulesOnSrc.size() - 1
            it.rules.excess.empty
        }

        and: "Rule info is NOT moved into the 'missing' section on the dstSwitch and transit switches"
        def dstSwitchValidateInfo = switchHelper.validate(switchPair.dst.dpId)
        dstSwitchValidateInfo.rules.proper*.cookie.sort() == rulesOnDst*.cookie.sort()
        dstSwitchValidateInfo.verifyRuleSectionsAreEmpty(["missing", "excess"])
        def involvedSwitchIds = flow.retrieveAllEntityPaths().getInvolvedSwitches()
        def transitSwitches = involvedSwitchIds[1..-2].findAll { !it.description.contains("OF_12") }
        transitSwitches.each { switchId ->
            def transitSwitchValidateInfo = switchHelper.validate(switchId)
            assert transitSwitchValidateInfo.rules.proper*.cookie.findAll { !new Cookie(it).serviceFlag }.size() == 2
            transitSwitchValidateInfo.verifyRuleSectionsAreEmpty(["missing", "excess"])
        }

        when: "Synchronize the switch"
        with(switchHelper.synchronize(switchPair.src.dpId, false)) {
            rules.installed == [egressCookie]
        }

        then: "Repeated validation shows no discrepancies"
        verifyAll(switchHelper.validate(switchPair.dst.dpId)) {
            it.rules.proper*.cookie.sort() == rulesOnDst*.cookie.sort()
            it.verifyRuleSectionsAreEmpty(["missing", "excess"])
        }

        when: "Delete the flow"
        flow.delete()

        then: "Check that the switch validate request returns empty sections on all involved switches"
        Wrappers.wait(WAIT_OFFSET) {
            involvedSwitchIds.findAll { !it.description.contains("OF_12") }.each { switchId ->
                def switchValidateInfo = switchHelper.validate(switchId)
                switchValidateInfo.verifyRuleSectionsAreEmpty()
                switchValidateInfo.verifyMeterSectionsAreEmpty()
            }
        }
    }

    def "Able to validate and sync an excess ingress/egress/transit rule + meter"() {
        given: "Two active not neighboring switches"
        def switchPair = switchPairs.all().nonNeighbouring().random()

        and: "Create an intermediate-switch flow"
        def flow = flowFactory.getRandom(switchPair)
        def involvedSwitches = flow.retrieveAllEntityPaths().getInvolvedSwitches()
        def createdCookiesSrcSw = switchRulesFactory.get(switchPair.src.dpId).getRules().cookie
        def createdCookiesDstSw =switchRulesFactory.get(switchPair.dst.dpId).getRules().cookie
        def createdCookiesTransitSwitch = switchRulesFactory.get(involvedSwitches[1]).getRules().cookie

        when: "Create excess rules on switches"
        def producer = new KafkaProducer(producerProps)
        //pick a meter id which is not yet used on src switch
        def excessMeterId = ((MIN_FLOW_METER_ID..100) - northbound.getAllMeters(switchPair.src.dpId)
                .meterEntries*.meterId).first()
        cleanupManager.addAction(SYNCHRONIZE_SWITCH, {switchHelper.synchronizeAndCollectFixedDiscrepancies(involvedSwitches)})
        producer.send(new ProducerRecord(speakerTopic, switchPair.dst.dpId.toString(), buildMessage(
                FlowSpeakerData.builder()
                        .switchId(switchPair.dst.dpId)
                        .ofVersion(OfVersion.of(switchPair.dst.ofVersion))
                        .cookie(new Cookie(1L))
                        .table(OfTable.EGRESS)
                        .priority(101)
                        .instructions(Instructions.builder().build())
                        .build()).toJson())).get()
        involvedSwitches[1..-2].findAll { !it.description.contains("OF_12") }.each { transitSw ->
            producer.send(new ProducerRecord(speakerTopic, transitSw.toString(), buildMessage(
                    FlowSpeakerData.builder()
                            .switchId(transitSw)
                            .ofVersion(OfVersion.of(switchPair.dst.ofVersion))
                            .cookie(new Cookie(1L))
                            .table(OfTable.TRANSIT)
                            .priority(102)
                            .instructions(Instructions.builder().build())
                            .build()).toJson())).get()
        }
        producer.send(new ProducerRecord(speakerTopic, switchPair.src.dpId.toString(), buildMessage([
                FlowSpeakerData.builder()
                        .switchId(switchPair.src.dpId)
                        .ofVersion(OfVersion.of(switchPair.src.ofVersion))
                        .cookie(new Cookie(1L))
                        .table(OfTable.INPUT)
                        .priority(103)
                        .instructions(Instructions.builder().build())
                        .build(),
                MeterSpeakerData.builder()
                        .switchId(switchPair.src.dpId)
                        .ofVersion(OfVersion.of(switchPair.src.ofVersion))
                        .meterId(new MeterId(excessMeterId))
                        .rate(flow.getMaximumBandwidth())
                        .burst(flow.getMaximumBandwidth())
                        .flags(Sets.newHashSet(MeterFlag.KBPS, MeterFlag.BURST, MeterFlag.STATS))
                        .build()]).toJson())).get()
        producer.flush()

        then: "Switch validation shows excess rules and store them in the 'excess' section"
        Wrappers.wait(WAIT_OFFSET) {
            assert switchRulesFactory.get(switchPair.src.dpId).getRules().size() == createdCookiesSrcSw.size() + 1
            involvedSwitches.findAll { !it.description.contains("OF_12") }.each { switchId ->
                def involvedSwitchValidateInfo = switchHelper.validate(switchId)
                if (switchId == switchPair.src.dpId) {
                    assert involvedSwitchValidateInfo.rules.proper*.cookie.sort() == createdCookiesSrcSw.sort()
                } else if (switchId == switchPair.dst.dpId) {
                    assert involvedSwitchValidateInfo.rules.proper*.cookie.sort() == createdCookiesDstSw.sort()
                } else {
                    assert involvedSwitchValidateInfo.rules.proper*.cookie.sort() == createdCookiesTransitSwitch.sort()
                }
                involvedSwitchValidateInfo.verifyRuleSectionsAreEmpty(["missing"])
                assert involvedSwitchValidateInfo.rules.excess.size() == 1
                assert involvedSwitchValidateInfo.rules.excess.cookie == [1L]
            }
        }
        and: "Excess meter is shown on the srcSwitch only"
        Long burstSize = flow.maximumBandwidth
        def validateSwitchInfo = switchHelper.validate(switchPair.src.dpId)
        assert validateSwitchInfo.meters.excess.size() == 1
        assert validateSwitchInfo.meters.excess.each {
            assert it.meterId == excessMeterId
            assert ["KBPS", "BURST", "STATS"].containsAll(it.flags)
            switchHelper.verifyRateSizeIsCorrect(switchPair.src, flow.maximumBandwidth, it.rate)
            switchHelper.verifyBurstSizeIsCorrect(switchPair.src, burstSize, it.burstSize)
        }
        involvedSwitches[1..-1].findAll { !it.description.contains("OF_12") }.each { switchId ->
            assert switchHelper.validate(switchId).meters.excess.empty
        }
        when: "Try to synchronize every involved switch"
        def syncResultsMap = involvedSwitches.collectEntries { switchId ->
            [switchId, northbound.synchronizeSwitch(switchId, true)]
        }
        then: "System deletes excess rules and meters"
        involvedSwitches.findAll { !it.description.contains("OF_12") }.each { switchId ->
            assert syncResultsMap[switchId].rules.excess.size() == 1
            assert syncResultsMap[switchId].rules.excess[0] == 1L
            assert syncResultsMap[switchId].rules.removed.size() == 1
            assert syncResultsMap[switchId].rules.removed[0] == 1L
        }
        assert syncResultsMap[switchPair.src.dpId].meters.excess.size() == 1
        assert syncResultsMap[switchPair.src.dpId].meters.excess.meterId[0] == excessMeterId
        assert syncResultsMap[switchPair.src.dpId].meters.removed.size() == 1
        assert syncResultsMap[switchPair.src.dpId].meters.removed.meterId[0] == excessMeterId

        when: "Delete the flow"
        flow.delete()

        then: "Check that the switch validate request returns empty sections on all involved switches"
        Wrappers.wait(WAIT_OFFSET) {
            involvedSwitches.findAll { !it.description.contains("OF_12") }.each { switchId ->
                def switchValidateInfo = switchHelper.validate(switchId)
                switchValidateInfo.verifyRuleSectionsAreEmpty()
                switchValidateInfo.verifyMeterSectionsAreEmpty()
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
        List<Switch> involvedSwitches = flow.retrieveAllEntityPaths().getInvolvedIsls()
                .collect { [it.srcSwitch, it.dstSwitch] }.flatten().unique() as List<Switch>
        def transitSwitchIds = involvedSwitches[1..-2]*.dpId
        def cookiesMap = involvedSwitches.collectEntries { sw ->
            [sw.dpId, switchRulesFactory.get(sw.dpId).getRules().findAll {
                !(it.cookie in sw.defaultCookies) && !new Cookie(it.cookie).serviceFlag
            }*.cookie]
        }
        def metersMap = involvedSwitches.collectEntries { sw ->
            [sw.dpId, northbound.getAllMeters(sw.dpId).meterEntries.findAll {
                it.meterId > MAX_SYSTEM_RULE_METER_ID
            }*.meterId]
        }

        expect: "Switch validation shows missing rules and meters on every related switch"
        involvedSwitches.each { switchHelper.deleteSwitchRules(it.dpId, DeleteRulesAction.IGNORE_DEFAULTS) }
        [switchPair.src, switchPair.dst].each { northbound.deleteMeter(it.dpId, metersMap[it.dpId][0]) }
        Wrappers.wait(RULES_DELETION_TIME) {
            def validationResultsMap = involvedSwitches.collectEntries { [it.dpId, switchHelper.validate(it.dpId)] }
            involvedSwitches.each {
                def swProps = northbound.getSwitchProperties(it.dpId)
                def switchIdInSrcOrDst = (it.dpId in [switchPair.src.dpId, switchPair.dst.dpId])
                def defaultAmountOfFlowRules = 2 // ingress + egress
                def amountOfServer42Rules = (switchIdInSrcOrDst && swProps.server42FlowRtt ? 1 : 0)
                if (swProps.server42FlowRtt) {
                    if ((flow.destination.getSwitchId() == it.dpId && flow.destination.vlanId) || (
                            flow.source.getSwitchId() == it.dpId && flow.source.vlanId))
                        amountOfServer42Rules += 1
                }
                def rulesCount = defaultAmountOfFlowRules + amountOfServer42Rules +
                        (switchIdInSrcOrDst ? 1 : 0)
                assert validationResultsMap[it.dpId].rules.missing.size() == rulesCount
                assert validationResultsMap[it.dpId].rules.missing.cookieHex.size() == rulesCount
            }
            [switchPair.src, switchPair.dst].each { assert validationResultsMap[it.dpId].meters.missing.size() == 1 }
        }

        when: "Try to synchronize all switches"
        def syncResultsMap = involvedSwitches.collectEntries { [it.dpId, northbound.synchronizeSwitch(it.dpId, false)] }

        then: "System installs missing rules and meters"
        involvedSwitches.each {
            assert syncResultsMap[it.dpId].rules.proper.findAll { !new Cookie(it).serviceFlag }.size() == 0
            assert syncResultsMap[it.dpId].rules.excess.size() == 0
            assert syncResultsMap[it.dpId].rules.missing.containsAll(cookiesMap[it.dpId])
            assert syncResultsMap[it.dpId].rules.removed.size() == 0
            assert syncResultsMap[it.dpId].rules.installed.containsAll(cookiesMap[it.dpId])
        }
        [switchPair.src, switchPair.dst].each {
            assert syncResultsMap[it.dpId].meters.proper.findAll { !it.defaultMeter }.size() == 0
            assert syncResultsMap[it.dpId].meters.excess.size() == 0
            assert syncResultsMap[it.dpId].meters.missing*.meterId == metersMap[it.dpId]
            assert syncResultsMap[it.dpId].meters.removed.size() == 0
            assert syncResultsMap[it.dpId].meters.installed*.meterId == metersMap[it.dpId]
        }

        and: "Switch validation doesn't complain about missing rules and meters"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            switchHelper.validateAndCollectFoundDiscrepancies(involvedSwitches*.getDpId()).isEmpty()
        }

        and: "Rules are synced correctly"
        // ingressRule should contain "pushVxlan"
        // egressRule should contain "tunnel-id"
        with(switchRulesFactory.get(switchPair.src.dpId).getRules()) { rules ->
            assert rules.find {
                it.cookie == flowInfoFromDb.forwardPath.cookie.value
            }.instructions.applyActions.pushVxlan
            assert rules.find {
                it.cookie == flowInfoFromDb.reversePath.cookie.value
            }.match.tunnelId
        }
        with(switchRulesFactory.get(switchPair.dst.dpId).getRules()) { rules ->
            assert rules.find {
                it.cookie == flowInfoFromDb.forwardPath.cookie.value
            }.match.tunnelId
            assert rules.find {
                it.cookie == flowInfoFromDb.reversePath.cookie.value
            }.instructions.applyActions.pushVxlan
        }
        transitSwitchIds.each { swId ->
            with(switchRulesFactory.get(swId).getRules()) { rules ->
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
        def allSwitches = flowPathInfo.getInvolvedSwitches()
        def rulesPerSwitch = allSwitches.collectEntries {
            [it, switchRulesFactory.get(it).getRules().cookie.sort()]
        }
        expect: "Upon validation all rules are stored in the 'proper' section"
        allSwitches.each { switchId ->
            def rules = northbound.validateSwitchRules(switchId)
            assert rules.properRules.sort() == rulesPerSwitch[switchId]
            assert rules.missingRules.empty
            assert rules.excessRules.empty
        }
        when: "Delete rule of protected path on the srcSwitch (egress)"
        def protectedPath = flowPathInfo.flowPath.protectedPath.forward.nodes.nodes
        def srcSwitchRules = northbound.getSwitchRules(swPair.src.dpId).flowEntries.findAll {
            !new Cookie(it.cookie).serviceFlag
        }
        def ruleToDelete = srcSwitchRules.find {
            //specifying protectedPath[0](src.inputPort) and protectedPath[1](src.outputPort) as protected path for FORWARD direction is used
            it.instructions?.applyActions?.flowOutput == protectedPath[0].portNo.toString() &&
                    it.match.inPort == protectedPath[1].portNo.toString()
        }.cookie
        switchHelper.deleteSwitchRules(swPair.src.dpId, ruleToDelete)
        then: "Deleted rule is moved to the 'missing' section on the srcSwitch"
        verifyAll(switchHelper.validate(swPair.src.dpId)) {
            it.rules.proper*.cookie.sort() == rulesPerSwitch[swPair.src.dpId] - ruleToDelete
            it.rules.missing*.cookie == [ruleToDelete]
            it.rules.excess.empty
        }
        and: "Rest switches are not affected by deleting the rule on the srcSwitch"
        allSwitches.findAll { it != swPair.src.dpId }.each { switchId ->
            def validation = switchHelper.validate(switchId)
            assert validation.rules.proper*.cookie.sort() == rulesPerSwitch[switchId]
            assert validation.rules.missing.empty
            assert validation.rules.excess.empty
        }
        when: "Synchronize switch with a missing protected path egress rule"
        with(northbound.synchronizeSwitch(swPair.src.dpId, false)) {
            rules.installed == [ruleToDelete]
        }
        then: "Switch validation no longer shows missing rules"
        verifyAll(switchHelper.validate(swPair.src.dpId)) {
            it.verifyRuleSectionsAreEmpty(["missing", "excess"])
            it.rules.proper*.cookie.sort() == rulesPerSwitch[swPair.src.dpId]
        }
    }

    def "Able to validate and sync a missing 'connected device' #data.descr rule"() {
        given: "A flow with enabled connected devices"
        def swPair = switchPairs.all().random()
        def flow = flowFactory.getBuilder(swPair)
                .withDetectedDevicesOnDst(true, true).build()
                .create()

        expect: "Switch validation puts connected device lldp rule into 'proper' section"
        def deviceCookie = switchRulesFactory.get(swPair.dst.dpId).getRules().find(data.cookieSearchClosure).cookie
        with(switchHelper.validate(flow.destination.switchId)) {
            it.rules.proper*.cookie.contains(deviceCookie)
        }

        when: "Remove the connected device rule"
        switchHelper.deleteSwitchRules(flow.destination.switchId, deviceCookie)

        then: "Switch validation puts connected device rule into 'missing' section"
        verifyAll(switchHelper.validate(flow.destination.switchId)) {
            !it.rules.proper*.cookie.contains(deviceCookie)
            it.rules.missing*.cookie.contains(deviceCookie)
            it.rules.missing*.cookieHex.contains(Long.toHexString(deviceCookie).toUpperCase())
        }

        when: "Synchronize the switch"
        with(switchHelper.synchronize(flow.destination.switchId, false)) {
            it.rules.installed == [deviceCookie]
        }

        then: "Switch validation no longer shows any discrepancies in rules nor meters"
        verifyAll(switchHelper.validate(flow.destination.switchId)) {
            it.rules.proper*.cookie.contains(deviceCookie)
            it.rules.missing.empty
            it.rules.excess.empty
            it.meters.missing.empty
            it.meters.excess.empty
        }

        when: "Delete the flow"
        flow.delete()

        then: "Switch validation is empty"
        verifyAll(switchHelper.validate(flow.destination.switchId)) {
            it.verifyRuleSectionsAreEmpty()
            it.verifyMeterSectionsAreEmpty()
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
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches.findAll { it.ofVersion != "OF_12" }
        def flow = flowFactory.getRandom(srcSwitch, dstSwitch)

        when: "Perform result filtering"
        def srcSwitchValidateInfo = switchHelper.validate(srcSwitch.dpId, include, null)
        def dstSwitchValidateInfo = switchHelper.validate(dstSwitch.dpId)

        and: "Result contains only included sections"
        srcSwitchValidateInfo.verifySectionInSwitchValidationInfo(sectionsToVerifyPresence)
        srcSwitchValidateInfo.verifySectionsAsExpectedFields(sectionsToVerifyPresence)
        dstSwitchValidateInfo.verifySectionInSwitchValidationInfo(sectionsToVerifyPresence)
        dstSwitchValidateInfo.verifySectionsAsExpectedFields(sectionsToVerifyPresence)

        then: "Delete the flow"
        flow.delete()

        and: "Check that the switch validate request returns empty sections"
        Wrappers.wait(WAIT_OFFSET) {
            def srcSwitchValidateInfoAfterDelete = switchHelper.validate(srcSwitch.dpId)
            def dstSwitchValidateInfoAfterDelete = switchHelper.validate(dstSwitch.dpId)
            srcSwitchValidateInfoAfterDelete.verifyRuleSectionsAreEmpty()
            dstSwitchValidateInfoAfterDelete.verifyRuleSectionsAreEmpty()
        }

        where:
        include << ["METERS", "METERS|GROUPS", "METERS|GROUPS|RULES"]
        sectionsToVerifyPresence << [["meters"], ["meters", "groups"], ["meters", "groups", "rules"]]
    }

    @Tags([VIRTUAL, LOW_PRIORITY])
    def "Able to validate switch using #apiVersion API when GRPC is down"() {
        given: "Random switch without LAG feature enabled"
        def aSwitch = topology.getSwitches().find {
            !database.getSwitch(it.getDpId()).getFeatures().contains(SwitchFeature.LAG)
        }

        and: "GRPC container is down"
        def dockerHelper = new DockerHelper(dockerHost)
        def grpcContainerId = dockerHelper.getContainerId(ContainerName.GRPC)
        dockerHelper.pauseContainer(grpcContainerId)

        and: "Switch has a new feature artificially added directly to DB"
        def originalFeatures = database.getSwitch(aSwitch.getDpId()).getFeatures() as Set
        def newFeatures = originalFeatures + SwitchFeature.LAG
        database.setSwitchFeatures(aSwitch.getDpId(), newFeatures)

        when: "Validate switch"
        def validationResult = validate(aSwitch.getDpId())

        then: "Validation is successful"
        validationResult.getLogicalPorts().getError() ==
                "Timeout for waiting response on DumpLogicalPortsRequest() Details: Error in SpeakerWorkerService"

        cleanup:
        dockerHelper.resumeContainer(grpcContainerId)
        database.setSwitchFeatures(aSwitch.getDpId(), originalFeatures)

        where:
        apiVersion | validate
        "V2"       | {switchHelper.validate(it)}
        "V1"       | {switchHelper.validateV1(it)}
    }

    List<Integer> getCreatedMeterIds(SwitchId switchId) {
        return northbound.getAllMeters(switchId).meterEntries.findAll { it.meterId > MAX_SYSTEM_RULE_METER_ID }*.meterId
    }

    List<Long> getCookiesWithMeter(SwitchId switchId) {
        return switchRulesFactory.get(switchId).getRules().findAll {
            !new Cookie(it.cookie).serviceFlag && it.instructions.goToMeter
        }*.cookie.sort()
    }
}
