package org.openkilda.functionaltests.spec.switches

import static org.assertj.core.api.Assertions.assertThat
import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.extension.tags.Tag.SWITCH_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.functionaltests.extension.tags.Tag.VIRTUAL
import static org.openkilda.testing.Constants.NON_EXISTENT_SWITCH_ID
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.SwitchNotFoundExpectedError
import org.openkilda.functionaltests.extension.tags.IterationTag
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowEncapsulationType
import org.openkilda.functionaltests.helpers.model.FlowExtended
import org.openkilda.functionaltests.helpers.model.FlowRuleEntity
import org.openkilda.functionaltests.helpers.model.SwitchExtended
import org.openkilda.functionaltests.model.stats.Direction
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchId
import org.openkilda.model.cookie.Cookie
import org.openkilda.model.cookie.CookieBase.CookieType
import org.openkilda.testing.service.traffexam.TraffExamService

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared

import javax.inject.Provider

@Narrative("""Verify how Kilda behaves with switch rules (either flow rules or default rules) under different 
circumstances: e.g. persisting rules on newly connected switch, installing default rules on new switch etc.""")

class FlowRulesSpec extends HealthCheckSpecification {

    @Autowired
    Provider<TraffExamService> traffExamProvider
    @Autowired
    @Shared
    FlowFactory flowFactory
    @Shared
    SwitchExtended srcSwitch, dstSwitch
    @Shared
    List srcSwDefaultRules
    @Shared
    List dstSwDefaultRules
    @Shared
    int flowRulesCount = 2
    @Shared
    Boolean s42IsEnabledOnSrcSw
    @Shared
    // multiTableFlowRule is an extra rule which is created after creating a flow
    int multiTableFlowRulesCount = 1
    @Shared
    int sharedRulesCount = 1
    @Shared
    int s42FlowRttIngressForwardCount = 1
    @Shared
    int s42FlowRttInput = 1
    @Shared
    int s42QinqOuterVlanCount = 1

    def setupSpec() {
        (srcSwitch, dstSwitch) = switches.all().getSwitches()[0..1]
        s42IsEnabledOnSrcSw = srcSwitch.getProps().server42FlowRtt
        srcSwDefaultRules = srcSwitch.rulesManager.getRules()
        dstSwDefaultRules = dstSwitch.rulesManager.getRules()
    }

    @Tags([VIRTUAL, SMOKE, SWITCH_RECOVER_ON_FAIL])
    def "Pre-installed flow rules are not deleted from a new switch connected to the controller"() {
        given: "A switch with proper flow rules installed (including default) and not connected to the controller"
        flowFactory.getRandom(srcSwitch, dstSwitch)

        def defaultPlusFlowRules = []
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            defaultPlusFlowRules = srcSwitch.rulesManager.getRules()
            def multiTableFlowRules = multiTableFlowRulesCount + sharedRulesCount
            assert defaultPlusFlowRules.size() == srcSwDefaultRules.size() + flowRulesCount + multiTableFlowRules
        }

        def blockData = srcSwitch.knockout(RW)

        when: "Connect the switch to the controller"
        srcSwitch.revive(blockData)

        then: "Previously installed rules are not deleted from the switch"
        def actualRules = srcSwitch.rulesManager.getRules()
        assertThat(actualRules).containsExactlyInAnyOrder(*defaultPlusFlowRules)
    }

    @Tags([SMOKE])
    @IterationTag(tags = [SMOKE_SWITCHES], iterationNameRegex = /delete-action=DROP_ALL\)/)
    def "Able to delete rules from a switch (delete-action=#data.deleteRulesAction)"() {
        given: "A switch with some flow rules installed"
        flowFactory.getRandom(srcSwitch, dstSwitch)

        when: "Delete rules from the switch"
        List<FlowRuleEntity> expectedRules = data.getExpectedRules(srcSwitch, srcSwDefaultRules)
        def deletedRules = srcSwitch.rulesManager.delete(data.deleteRulesAction)

        then: "The corresponding rules are really deleted"
        deletedRules.size() == data.rulesDeleted
        Wrappers.wait(RULES_DELETION_TIME) {
            def actualRules = srcSwitch.rulesManager.getRules()
            assertThat(actualRules).containsExactlyInAnyOrder(*expectedRules)
        }

        where:
        data << [
                [// Drop all rules
                 deleteRulesAction: DeleteRulesAction.DROP_ALL,
                 rulesDeleted: srcSwDefaultRules.size() + flowRulesCount + multiTableFlowRulesCount +
                         sharedRulesCount + (s42IsEnabledOnSrcSw ? s42FlowRttInput + s42QinqOuterVlanCount +
                         s42FlowRttIngressForwardCount : 0),
                 getExpectedRules : { sw, defaultRules -> [] }
                ],
                [// Drop all rules, add back in the base default rules
                 deleteRulesAction: DeleteRulesAction.DROP_ALL_ADD_DEFAULTS,
                 rulesDeleted     : srcSwDefaultRules.size() + flowRulesCount + multiTableFlowRulesCount +
                         sharedRulesCount + (s42IsEnabledOnSrcSw ? s42FlowRttInput + s42QinqOuterVlanCount +
                         s42FlowRttIngressForwardCount : 0),
                 getExpectedRules : { sw, defaultRules ->
                     List<FlowRuleEntity> noDefaultSwRules = srcSwitch.rulesManager.getRules() - defaultRules
                     defaultRules + noDefaultSwRules.findAll { Cookie.isIngressRulePassThrough(it.cookie) } +
                         (s42IsEnabledOnSrcSw ? noDefaultSwRules.findAll {
                             new Cookie(it.cookie).getType() == CookieType.SERVER_42_FLOW_RTT_INPUT } : [])
                 }
                ],
                [// Don't drop the default rules, but do drop everything else
                 deleteRulesAction: DeleteRulesAction.IGNORE_DEFAULTS,
                 rulesDeleted     : flowRulesCount + sharedRulesCount +
                         (s42IsEnabledOnSrcSw ? s42QinqOuterVlanCount + s42FlowRttIngressForwardCount : 0),
                 getExpectedRules : { sw, defaultRules ->
                     List<FlowRuleEntity> noDefaultSwRules = srcSwitch.rulesManager.getRules() - defaultRules
                     defaultRules + noDefaultSwRules.findAll { Cookie.isIngressRulePassThrough(it.cookie) } +
                         (s42IsEnabledOnSrcSw ? noDefaultSwRules.findAll {
                             new Cookie(it.cookie).getType() == CookieType.SERVER_42_FLOW_RTT_INPUT } : [])
                 }
                ],
                [// Drop all non-base rules (ie IGNORE), and add base rules back (eg overwrite)
                 deleteRulesAction: DeleteRulesAction.OVERWRITE_DEFAULTS,
                 rulesDeleted     : srcSwDefaultRules.size() + multiTableFlowRulesCount +
                         (s42IsEnabledOnSrcSw ? s42FlowRttInput : 0),
                 getExpectedRules : { sw, defaultRules -> defaultRules + getFlowRules(sw) +
                         srcSwitch.rulesManager.getRules().findAll {
                             Cookie.isIngressRulePassThrough(it.cookie)
                         }
                 }
                ],
                [// Drop all default rules
                 deleteRulesAction: DeleteRulesAction.REMOVE_DEFAULTS,
                 rulesDeleted     : srcSwDefaultRules.size() + multiTableFlowRulesCount +
                         (s42IsEnabledOnSrcSw ? s42FlowRttInput : 0),
                 getExpectedRules : { sw, defaultRules -> getFlowRules(sw) -
                         (s42IsEnabledOnSrcSw ? srcSwitch.rulesManager.getRules().findAll {
                             new Cookie(it.cookie).getType() == CookieType.SERVER_42_FLOW_RTT_INPUT } : [])
                 }
                ],
                [// Drop the default, add them back
                 deleteRulesAction: DeleteRulesAction.REMOVE_ADD_DEFAULTS,
                 rulesDeleted     : srcSwDefaultRules.size() + multiTableFlowRulesCount +
                         (s42IsEnabledOnSrcSw ? s42FlowRttInput : 0),
                 getExpectedRules : { sw, defaultRules -> defaultRules + getFlowRules(sw) +
                         srcSwitch.rulesManager.getRules().findAll {
                             Cookie.isIngressRulePassThrough(it.cookie)
                         }
                 }
                ]
        ]
    }

    @Tags([SMOKE, SMOKE_SWITCHES])
    def "Able to delete switch rules by cookie/priority #data.identifier"() {
        given: "A switch with some flow rules installed"
        flowFactory.getRandom(srcSwitch, dstSwitch)

        when: "Delete switch rules by #data.identifier"
        //exclude the "SERVER_42_INPUT" rule, this rule has less priority than usual flow rule
        def ruleToDelete = getFlowRules(data.switch).find { !new Cookie(it.cookie).serviceFlag }
        def expectedDeletedRules = data.switch.rulesManager.getRules()
                .findAll { it."$data.identifier" ==  ruleToDelete."$data.identifier" &&
                !new Cookie(it.cookie).serviceFlag }
        def deletedRules = data.switch.rulesManager.delete(ruleToDelete."$data.identifier")

        then: "The requested rules are really deleted"
        deletedRules.size() == expectedDeletedRules.size()
        Wrappers.wait(RULES_DELETION_TIME) {
            def actualRules = data.switch.rulesManager.getRules()
            assert actualRules.findAll { it.cookie in expectedDeletedRules*.cookie }.empty
        }

        where:
        data << [
                [identifier : "cookie",
                  switch      : srcSwitch
                 ],
                 [identifier : "priority",
                  switch      : dstSwitch
                 ]
        ]
    }

    def "Attempt to delete switch rules by supplying non-existing #data.description leaves all rules intact"() {
        given: "A switch with some flow rules installed"
        assumeTrue(data.description != "priority", "https://github.com/telstra/open-kilda/issues/1701")
        flowFactory.getRandom(srcSwitch, dstSwitch)

        def ingressRule = (srcSwitch.rulesManager.getRules() - data.defaultRules).find {
            new Cookie(it.cookie).serviceFlag
        }
        if (ingressRule) {
            data.defaultRules = (data.defaultRules + ingressRule + sharedRulesCount)
        }

        when: "Delete switch rules by non-existing #data.description"
        def deletedRules = data.switch.rulesManager.delete(data.value)

        then: "All rules are kept intact"
        deletedRules.size() == 0
        data.switch.rulesManager.getRules().size() == data.defaultRules.size() + flowRulesCount

        where:
        data << [[description : "cookie",
                  switch      : srcSwitch,
                  defaultRules: srcSwDefaultRules,
                  value       : 0x8000000000000000L
                 ],
                 [description : "priority",
                  switch      : dstSwitch,
                  defaultRules: dstSwDefaultRules,
                  value       : 0
                 ]
        ]
    }

    @Tags(SMOKE_SWITCHES)
    @IterationTag(tags = [SMOKE], iterationNameRegex = /inPort/)
    def "Able to delete switch rules by #data.description"() {
        given: "A switch with some flow rules installed"
        flow.create()
        def cookiesBefore = sw.rulesManager.getRules().cookie.sort()
        def s42IsEnabled = sw.getProps().server42FlowRtt

        when: "Delete switch rules by #data.description"
        def deletedRules = sw.rulesManager.delete(data.inPort, data.inVlan, data.encapsulationType, data.outPort)

        then: "The requested rules are really deleted"
        def amountOfDeletedRules = data.removedRules
        if (s42IsEnabled && data.description == "inVlan") {
            amountOfDeletedRules +=  s42FlowRttIngressForwardCount
        }
        deletedRules.size() == amountOfDeletedRules
        Wrappers.wait(RULES_DELETION_TIME) {
            def actualRules = sw.rulesManager.getRules()
            assert actualRules*.cookie.sort() == cookiesBefore - deletedRules
            assert filterRules(actualRules, data.inPort, data.inVlan, data.outPort).empty
        }

        where:
        data << [[description      : "inPort",
                  flow             : flowFactory.getBuilder(srcSwitch, dstSwitch).build(),
                  switch           : srcSwitch,
                  inVlan           : null,
                  encapsulationType: null,
                  outPort          : null,
                  removedRules     : 3
                 ].tap { inPort = flow.source.portNumber },
                 [description      : "inVlan",
                  flow             : flowFactory.getBuilder(srcSwitch, dstSwitch).build(),
                  switch           : srcSwitch,
                  inPort           : null,
                  encapsulationType: "TRANSIT_VLAN",
                  outPort          : null,
                  removedRules     : 1
                 ].tap { inVlan = flow.source.vlanId },
                 [description      : "inPort and inVlan",
                  flow             : flowFactory.getBuilder(srcSwitch, dstSwitch).build(),
                  switch           : srcSwitch,
                  encapsulationType: "TRANSIT_VLAN",
                  outPort          : null,
                  removedRules     : 1
                 ].tap { inVlan = flow.source.vlanId; inPort = flow.source.portNumber },
                 [description      : "outPort",
                  flow             : flowFactory.getBuilder(srcSwitch, dstSwitch).build(),
                  switch           : dstSwitch,
                  inPort           : null,
                  inVlan           : null,
                  encapsulationType: null,
                  removedRules     : 1
                 ].tap { outPort = flow.destination.portNumber },
        ]
        flow = data.flow as FlowExtended
        sw = data.switch as SwitchExtended
    }

    @IterationTag(tags = [SMOKE], iterationNameRegex = /inVlan/)
    def "Attempt to delete switch rules by supplying non-existing #data.description keeps all rules intact"() {
        given: "A switch with some flow rules installed"
        flowFactory.getRandom(srcSwitch, dstSwitch)
        def originalRules = sw.rulesManager.getRules().cookie.sort()

        when: "Delete switch rules by non-existing #data.description"
        def deletedRules = sw.rulesManager.delete(data.inPort, data.inVlan, data.encapsulationType, data.outPort)

        then: "All rules are kept intact"
        deletedRules.size() == 0
        sw.rulesManager.getRules().cookie.sort() == originalRules

        where:
        data << [[description      : "inPort",
                  switch           : srcSwitch,
                  defaultRules     : srcSwDefaultRules,
                  inPort           : Integer.MAX_VALUE - 1,
                  inVlan           : null,
                  encapsulationType: null,
                  outPort          : null
                 ],
                 [description      : "inVlan",
                  switch           : srcSwitch,
                  defaultRules     : srcSwDefaultRules,
                  inPort           : null,
                  inVlan           : 4096,
                  encapsulationType: "TRANSIT_VLAN",
                  outPort          : null
                 ],
                 [description      : "inPort and inVlan",
                  switch           : srcSwitch,
                  defaultRules     : srcSwDefaultRules,
                  inPort           : Integer.MAX_VALUE - 1,
                  inVlan           : 4096,
                  encapsulationType: "TRANSIT_VLAN",
                  outPort          : null
                 ],
                 [description      : "outPort",
                  switch           : dstSwitch,
                  defaultRules     : dstSwDefaultRules,
                  inPort           : null,
                  inVlan           : null,
                  encapsulationType: null,
                  outPort          : Integer.MAX_VALUE - 1
                 ]
        ]
        sw = data.switch as SwitchExtended
    }

    @Tags([TOPOLOGY_DEPENDENT])
    def "Able to validate and sync missing rules for #description on terminating/transit switches"() {
        given: "Two active not neighboring switches with a long available path"
        def switchPair = switchPairs.all().nonNeighbouring().random()
        def availablePath = switchPair.retrieveAvailablePaths().collect { it.getInvolvedIsls()}
        def longPath = availablePath.max { it.size() }
        availablePath.findAll { it != longPath }.each { islHelper.makePathIslsMorePreferable(longPath, it) }

        and: "Create a transit-switch flow going through these switches"
        def flow = flowFactory.getBuilder(switchPair)
                .withBandwidth(maximumBandwidth)
                .withIgnoreBandwidth(maximumBandwidth ? false : true).build()
                .create()

        and: "Remove flow rules so that they become 'missing'"
        def involvedSwitches = switches.all().findSwitchesInPath(flow.retrieveAllEntityPaths())
        def defaultPlusFlowRulesMap = involvedSwitches.collectEntries { sw ->
            [sw.switchId, sw.rulesManager.getRules()]
        }

        def amountOfRulesMap = involvedSwitches.collectEntries { sw ->
            [sw.switchId, sw.collectFlowRelatedRulesAmount(flow)]
        }
        involvedSwitches.each { sw ->
            sw.rulesManager.delete(DeleteRulesAction.IGNORE_DEFAULTS)
            Wrappers.wait(RULES_DELETION_TIME) {
                assert sw.rulesManager.validate().missingRules.size() == amountOfRulesMap[sw.switchId]
            }
        }

        when: "Synchronize rules on switches"
        def synchronizedRulesMap = involvedSwitches.collectEntries { sw ->
            [sw.switchId, sw.rulesManager.synchronize()]
        }

        then: "The corresponding rules are installed on switches"
        involvedSwitches.each { sw ->
            assert synchronizedRulesMap[sw.switchId].installedRules.size() == amountOfRulesMap[sw.switchId]
            Wrappers.wait(RULES_INSTALLATION_TIME) {
                def actualRules = sw.rulesManager.getRules()
                assertThat(actualRules).containsExactlyInAnyOrder(*defaultPlusFlowRulesMap[sw.switchId])
            }
        }

        and: "No missing rules were found after rules validation"
        involvedSwitches.each { sw ->
            verifyAll(sw.rulesManager.validate()) {
                properRules.findAll { !new Cookie(it).serviceFlag }.size() == amountOfRulesMap[sw.switchId]
                missingRules.empty
                excessRules.empty
            }
        }

        where:
        description         | maximumBandwidth
        "a flow"            | 1000
        "an unmetered flow" | 0
    }

    def "Unable to #action rules on a non-existent switch"() {
        when: "Try to #action rules on a non-existent switch"
        northbound."$method"(NON_EXISTENT_SWITCH_ID)

        then: "An error is received (404 code)"
        def exc = thrown(HttpClientErrorException)
        new SwitchNotFoundExpectedError("Switch '${NON_EXISTENT_SWITCH_ID}' not found", ~/Error in switch validation/).matches(exc)

        where:
        action        | method
        "synchronize" | "synchronizeSwitchRules"
        "validate"    | "validateSwitchRules"
    }

    @Tags([LOW_PRIORITY])//uses legacy 'rules validation', has a switchValidate analog in SwitchValidationSpec
    def "Able to synchronize rules for a flow with protected path"() {
        given: "Two active not neighboring switches"
        def switchPair = switchPairs.all().nonNeighbouring().withAtLeastNNonOverlappingPaths(2).random()

        and: "Create a flow with protected path"
        def flow = flowFactory.getBuilder(switchPair)
                .withProtectedPath(true).build()
                .create()

        def flowPathInfo = flow.retrieveAllEntityPaths()
        def involvedSwitches = switches.all().findSwitchesInPath(flowPathInfo)

        HashMap<SwitchId, List<FlowRuleEntity>> flowInvolvedSwitchesWithRulesBefore = involvedSwitches
                .collectEntries{ [(it.switchId): it.rulesManager.getRules()] } as HashMap<SwitchId, List<FlowRuleEntity>>
        flow.verifyRulesForProtectedFlowOnSwitches(flowInvolvedSwitchesWithRulesBefore)

        def mainPathSwIds = flowPathInfo.getPathNodes(Direction.FORWARD, false).switchId
        def protectedPathSwId = flowPathInfo.getPathNodes(Direction.FORWARD, true).switchId
        List<SwitchId> commonNodeIds = mainPathSwIds.intersect(protectedPathSwId)
        List<SwitchExtended> commonMainAndProtectedPathSws = involvedSwitches.findAll { it.switchId in commonNodeIds }
        List<SwitchExtended> uniqueMainAndProtectedPathSws = involvedSwitches.findAll { !(it.switchId in commonNodeIds) }

        and: "Delete flow rules(for main and protected paths) on involved switches for creating missing rules"
        commonMainAndProtectedPathSws.each { it.rulesManager.delete(DeleteRulesAction.IGNORE_DEFAULTS) }
        uniqueMainAndProtectedPathSws.each { it.rulesManager.delete(DeleteRulesAction.IGNORE_DEFAULTS) }
        commonMainAndProtectedPathSws.each { assert it.rulesManager.validate().missingRules.size() > 0 }
        uniqueMainAndProtectedPathSws.each { assert it.rulesManager.validate().missingRules.size() == 2 }

        when: "Synchronize rules on switches"
        commonMainAndProtectedPathSws.each {
            def response = it.rulesManager.synchronize()
            assert response.missingRules.size() > 0
            assert response.installedRules.sort() == response.missingRules.sort()
            assert response.properRules.findAll { !new Cookie(it).serviceFlag }.empty
            assert response.excessRules.empty
        }
        uniqueMainAndProtectedPathSws.each {
            def response = it.rulesManager.synchronize()
            assert response.missingRules.size() == 2
            assert response.installedRules.sort() == response.missingRules.sort()
            assert response.properRules.findAll { !new Cookie(it).serviceFlag }.empty, it
            assert response.excessRules.empty
        }

        then: "No missing rules were found after rules synchronization"
        commonMainAndProtectedPathSws.each { sw ->
            verifyAll(sw.rulesManager.validate()) {
                properRules.sort() == flowInvolvedSwitchesWithRulesBefore[sw.switchId]*.cookie.sort()
                missingRules.empty
                excessRules.empty
            }
        }
        uniqueMainAndProtectedPathSws.each {
            verifyAll(it.rulesManager.validate()) {
                properRules.findAll { !new Cookie(it).serviceFlag }.size() == 2
                missingRules.empty
                excessRules.empty
            }
        }

        and: "Synced rules are exactly the same as before delete (ignoring irrelevant fields)"
        involvedSwitches.each { sw ->
            def actualRules = sw.rulesManager.getRules()
            assertThat(actualRules).containsExactlyInAnyOrder(*flowInvolvedSwitchesWithRulesBefore[sw.switchId])
        }
    }

    @Tags([SMOKE, SMOKE_SWITCHES, ISL_RECOVER_ON_FAIL])
    def "Traffic counters in ingress rule are reset on flow rerouting(multiTable mode)"() {
        given: "Two active neighboring switches and two possible flow paths at least"
        def swPair = switchPairs.all().withTraffgensOnBothEnds()
                .withAtLeastNPaths(1).withoutOf12Switches().random()

        and: "Create a flow going through these switches"
        def flow = flowFactory.getRandom(swPair)
        def flowInfo = flow.retrieveDetailsFromDB()
        def srcSw = switches.all().findSpecific(swPair.src.dpId)
        def dstSw = switches.all().findSpecific(swPair.dst.dpId)
        def sharedRuleSrcSw = getFlowRules(srcSw).find { new Cookie(it.cookie).getType() == CookieType.SHARED_OF_FLOW &&
                it.match.inPort.toInteger() == flow.source.portNumber }.cookie
        def sharedRuleDstSw = getFlowRules(dstSw).find { new Cookie(it.cookie).getType() == CookieType.SHARED_OF_FLOW &&
                it.match.inPort.toInteger() == flow.destination.portNumber }.cookie

        def ingressSrcSw = flowInfo.forwardPath.cookie.value
        def egressSrcSw = flowInfo.reversePath.cookie.value

        def ingressDstSw = flowInfo.reversePath.cookie.value
        def egressDstSw = flowInfo.forwardPath.cookie.value

        when: "Start traffic examination"
        def traffExam = traffExamProvider.get()
        def examVlanFlow = flow.traffExam(traffExam, 100, 2)
        withPool {
            [examVlanFlow.forward, examVlanFlow.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert traffExam.waitExam(direction).hasTraffic()
            }
        }

        then: "Traffic counters in shared/ingress/egress rule on source and destination switches represent packets movement"
        def rulesAfterPassingTrafficSrcSw = getFlowRules(srcSw)
        def rulesAfterPassingTrafficDstSw = getFlowRules(dstSw)
         //srcSw
         with(rulesAfterPassingTrafficSrcSw.find { it.cookie == sharedRuleSrcSw}) {
            !it.flags
            it.packetCount > 0
            it.byteCount > 0
         }
         with(rulesAfterPassingTrafficSrcSw.find { it.cookie == ingressSrcSw}) {
            it.flags.contains("RESET_COUNTS")
            it.packetCount > 0
            it.byteCount > 0
         }
         with(rulesAfterPassingTrafficSrcSw.find { it.cookie == egressSrcSw}) {
            !it.flags.contains("RESET_COUNTS")
            it.packetCount > 0
            it.byteCount > 0
         }
         //dstSw
         with(rulesAfterPassingTrafficDstSw.find { it.cookie == sharedRuleDstSw}) {
            !it.flags
            it.packetCount > 0
            it.byteCount > 0
         }
         with(rulesAfterPassingTrafficDstSw.find { it.cookie == ingressDstSw}) {
            it.flags.contains("RESET_COUNTS")
            it.packetCount > 0
            it.byteCount > 0
         }
         with(rulesAfterPassingTrafficDstSw.find { it.cookie == egressDstSw}) {
            !it.flags.contains("RESET_COUNTS")
            it.packetCount > 0
            it.byteCount > 0
         }

        when: "Break the flow ISL (bring switch port down) to cause flow rerouting"
        def actualFlowPath = flow.retrieveAllEntityPaths()
        // Switches may have parallel links, so we need to get involved ISLs.
        def islToFail = actualFlowPath.getInvolvedIsls().first()
        islHelper.breakIsl(islToFail)

        then: "The flow was rerouted after reroute timeout"
        def flowInfoAfterReroute
        List<FlowRuleEntity> rulesAfterRerouteSrcSw
        List<FlowRuleEntity> rulesAfterRerouteDstSw
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            assert flow.retrieveFlowStatus().status == FlowState.UP
            assert flow.retrieveAllEntityPaths() != actualFlowPath
            flowInfoAfterReroute = flow.retrieveDetailsFromDB()
            rulesAfterRerouteSrcSw = getFlowRules(srcSw)
            rulesAfterRerouteDstSw = getFlowRules(dstSw)
            //system doesn't reinstall shared rule
            assert rulesAfterRerouteSrcSw.find { new Cookie(it.cookie).getType() == CookieType.SHARED_OF_FLOW &&
                    it.match.inPort.toInteger() == flow.source.portNumber }.cookie == sharedRuleSrcSw
            assert rulesAfterRerouteDstSw.find { new Cookie(it.cookie).getType() == CookieType.SHARED_OF_FLOW &&
                    it.match.inPort.toInteger() == flow.destination.portNumber }.cookie == sharedRuleDstSw
            rulesAfterRerouteSrcSw.findAll { new Cookie(it.cookie).getType() != CookieType.SHARED_OF_FLOW }.cookie.each {
                assert !(it in [ingressSrcSw, egressSrcSw])
            }
            rulesAfterRerouteDstSw.findAll { new Cookie(it.cookie).getType() != CookieType.SHARED_OF_FLOW }.cookie.each {
                assert !(it in [ingressDstSw, egressDstSw])
            }
        }

        and: "Traffic counters in ingress rule on source and destination switches are reset"
        //srcSw
         with(rulesAfterRerouteSrcSw.find { it.cookie == sharedRuleSrcSw}) {
            !it.flags
            it.packetCount > 0
            it.byteCount > 0
         }
         with(rulesAfterRerouteSrcSw.find { it.cookie == flowInfoAfterReroute.forwardPath.cookie.value}) {
            it.flags.contains("RESET_COUNTS")
            it.packetCount == 0
            it.byteCount == 0
         }
         with(rulesAfterRerouteSrcSw.find { it.cookie == flowInfoAfterReroute.reversePath.cookie.value}) {
            !it.flags.contains("RESET_COUNTS")
            it.packetCount == 0
            it.byteCount == 0
         }
         //dstSw
         with(rulesAfterRerouteDstSw.find { it.cookie == sharedRuleDstSw}) {
            !it.flags
            it.packetCount > 0
            it.byteCount > 0
         }
         //ingress
         with(rulesAfterRerouteDstSw.find { it.cookie == flowInfoAfterReroute.reversePath.cookie.value}) {
            it.flags.contains("RESET_COUNTS")
            it.packetCount == 0
            it.byteCount == 0
         }
         //egress
         with(rulesAfterRerouteDstSw.find { it.cookie == flowInfoAfterReroute.forwardPath.cookie.value}) {
            !it.flags.contains("RESET_COUNTS")
            it.packetCount == 0
            it.byteCount == 0
         }
    }

    @Tags([TOPOLOGY_DEPENDENT, LOW_PRIORITY, SMOKE_SWITCHES])//uses legacy 'rules validation', has a switchValidate analog in SwitchValidationSpec
    def "Able to synchronize rules for a flow with VXLAN encapsulation"() {
        given: "Two active not neighboring Noviflow switches"
        def switchPair = switchPairs.all().nonNeighbouring().withBothSwitchesVxLanEnabled().random()

        and: "Create a flow with vxlan encapsulation"
        def flow = flowFactory.getBuilder(switchPair)
                .withEncapsulationType(FlowEncapsulationType.VXLAN).build()
                .create()

        and: "Delete flow rules so that they become 'missing'"
        def flowInfoFromDb = flow.retrieveDetailsFromDB()
        def involvedSwitches = switches.all().findSwitchesInPath(flow.retrieveAllEntityPaths())

        def transitSwitchIds = involvedSwitches.findAll{ !(it.switchId in switchPair.toList().dpId) }
        def defaultPlusFlowRulesMap = involvedSwitches.collectEntries { sw ->
            [sw.switchId, sw.rulesManager.getRules()]
        }

        def rulesCountMap = involvedSwitches.collectEntries { sw ->
            [sw.switchId, sw.collectFlowRelatedRulesAmount(flow)]
        }

        involvedSwitches.each { sw ->
            sw.rulesManager.delete(DeleteRulesAction.IGNORE_DEFAULTS)
            Wrappers.wait(RULES_DELETION_TIME) {
                assert sw.rulesManager.validate().missingRules.size() == rulesCountMap[sw.switchId]
            }
        }

        when: "Synchronize rules on switches"
        def synchronizedRulesMap = involvedSwitches.collectEntries { sw ->
            [sw.switchId, sw.rulesManager.synchronize()]
        }

        then: "The corresponding rules are installed on switches"
        involvedSwitches.each { sw ->
            assert synchronizedRulesMap[sw.switchId].installedRules.size() == rulesCountMap[sw.switchId]
            Wrappers.wait(RULES_INSTALLATION_TIME) {
                def actualRules = sw.rulesManager.getRules()
                assertThat(actualRules).containsExactlyInAnyOrder(*defaultPlusFlowRulesMap[sw.switchId])
            }
        }

        and: "Rules are synced correctly"
        // ingressRule should contain "pushVxlan"
        // egressRule should contain "tunnel-id"
        with(involvedSwitches.find{ it.switchId == switchPair.src.dpId }.rulesManager.getRules()) { rules ->
            assert rules.find { it.cookie == flowInfoFromDb.forwardPath.cookie.value }.instructions.applyActions.pushVxlan
            assert rules.find { it.cookie == flowInfoFromDb.reversePath.cookie.value }.match.tunnelId
        }

        with(involvedSwitches.find{ it.switchId == switchPair.dst.dpId }.rulesManager.getRules()) { rules ->
            assert rules.find { it.cookie == flowInfoFromDb.forwardPath.cookie.value }.match.tunnelId
            assert rules.find { it.cookie == flowInfoFromDb.reversePath.cookie.value }.instructions.applyActions.pushVxlan
        }

        transitSwitchIds.each { sw ->
            with(sw.rulesManager.getRules()) { rules ->
                assert rules.find { it.cookie == flowInfoFromDb.forwardPath.cookie.value }.match.tunnelId
                assert rules.find { it.cookie == flowInfoFromDb.reversePath.cookie.value }.match.tunnelId
            }
        }

        and: "No missing rules were found after rules validation"
        involvedSwitches.each { sw ->
            verifyAll(sw.rulesManager.validate()) {
                properRules.findAll { !new Cookie(it).serviceFlag }.size() == rulesCountMap[sw.switchId]
                missingRules.empty
                excessRules.empty
            }
        }
    }

    List<FlowRuleEntity> filterRules(List<FlowRuleEntity> rules, inPort, inVlan, outPort) {
        if (inPort) {
            rules = rules.findAll { it.match.inPort == inPort.toString() }
        }
        if (inVlan) {
            rules = rules.findAll { it.match.vlanVid == inVlan.toString() }
        }
        if (outPort) {
            rules = rules.findAll { it.instructions?.applyActions?.flowOutput == outPort.toString() }
        }
        return rules
    }

    List<FlowRuleEntity> getFlowRules(SwitchExtended sw) {
        def defaultCookies = sw.collectDefaultCookies()
        sw.rulesManager.getRules().findAll { !(it.cookie in defaultCookies) }.sort()
    }
}
