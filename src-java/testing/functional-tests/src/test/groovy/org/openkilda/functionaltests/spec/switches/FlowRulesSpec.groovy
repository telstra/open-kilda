package org.openkilda.functionaltests.spec.switches

import static com.shazam.shazamcrest.matcher.Matchers.sameBeanAs
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
import static spock.util.matcher.HamcrestSupport.expect

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.SwitchNotFoundExpectedError
import org.openkilda.functionaltests.extension.tags.IterationTag
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowEncapsulationType
import org.openkilda.functionaltests.helpers.model.FlowExtended
import org.openkilda.functionaltests.helpers.model.SwitchRulesFactory
import org.openkilda.functionaltests.model.stats.Direction
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.info.rule.FlowEntry
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchId
import org.openkilda.model.cookie.Cookie
import org.openkilda.model.cookie.CookieBase.CookieType
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
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
    @Autowired
    @Shared
    SwitchRulesFactory switchRulesFactory
    @Shared
    Switch srcSwitch, dstSwitch
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
        (srcSwitch, dstSwitch) = topology.getActiveSwitches()[0..1]
        s42IsEnabledOnSrcSw = switchHelper.getCachedSwProps(srcSwitch.dpId).server42FlowRtt
        srcSwDefaultRules = northbound.getSwitchRules(srcSwitch.dpId).flowEntries
        dstSwDefaultRules = northbound.getSwitchRules(dstSwitch.dpId).flowEntries
    }

    @Tags([VIRTUAL, SMOKE, SWITCH_RECOVER_ON_FAIL])
    def "Pre-installed flow rules are not deleted from a new switch connected to the controller"() {
        given: "A switch with proper flow rules installed (including default) and not connected to the controller"
        flowFactory.getRandom(srcSwitch, dstSwitch)

        def defaultPlusFlowRules = []
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            defaultPlusFlowRules = northbound.getSwitchRules(srcSwitch.dpId).flowEntries
            def multiTableFlowRules = multiTableFlowRulesCount + sharedRulesCount
            assert defaultPlusFlowRules.size() == srcSwDefaultRules.size() + flowRulesCount + multiTableFlowRules
        }

        def blockData = switchHelper.knockoutSwitch(srcSwitch, RW)

        when: "Connect the switch to the controller"
        switchHelper.reviveSwitch(srcSwitch, blockData)

        then: "Previously installed rules are not deleted from the switch"
        compareRules(northbound.getSwitchRules(srcSwitch.dpId).flowEntries, defaultPlusFlowRules)
    }

    @Tags([SMOKE])
    @IterationTag(tags = [SMOKE_SWITCHES], iterationNameRegex = /delete-action=DROP_ALL\)/)
    def "Able to delete rules from a switch (delete-action=#data.deleteRulesAction)"() {
        given: "A switch with some flow rules installed"
        flowFactory.getRandom(srcSwitch, dstSwitch)

        when: "Delete rules from the switch"
        def expectedRules = data.getExpectedRules(srcSwitch, srcSwDefaultRules)
        def deletedRules = switchHelper.deleteSwitchRules(srcSwitch.dpId, data.deleteRulesAction)

        then: "The corresponding rules are really deleted"
        deletedRules.size() == data.rulesDeleted
        Wrappers.wait(RULES_DELETION_TIME) {
            compareRules(northbound.getSwitchRules(srcSwitch.dpId).flowEntries, expectedRules)
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
                     def noDefaultSwRules = northbound.getSwitchRules(srcSwitch.dpId).flowEntries - defaultRules
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
                     def noDefaultSwRules = northbound.getSwitchRules(srcSwitch.dpId).flowEntries - defaultRules
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
                         northbound.getSwitchRules(srcSwitch.dpId).flowEntries.findAll {
                             Cookie.isIngressRulePassThrough(it.cookie)
                         }
                 }
                ],
                [// Drop all default rules
                 deleteRulesAction: DeleteRulesAction.REMOVE_DEFAULTS,
                 rulesDeleted     : srcSwDefaultRules.size() + multiTableFlowRulesCount +
                         (s42IsEnabledOnSrcSw ? s42FlowRttInput : 0),
                 getExpectedRules : { sw, defaultRules -> getFlowRules(sw) -
                         (s42IsEnabledOnSrcSw ? northbound.getSwitchRules(srcSwitch.dpId).flowEntries.findAll {
                             new Cookie(it.cookie).getType() == CookieType.SERVER_42_FLOW_RTT_INPUT } : [])
                 }
                ],
                [// Drop the default, add them back
                 deleteRulesAction: DeleteRulesAction.REMOVE_ADD_DEFAULTS,
                 rulesDeleted     : srcSwDefaultRules.size() + multiTableFlowRulesCount +
                         (s42IsEnabledOnSrcSw ? s42FlowRttInput : 0),
                 getExpectedRules : { sw, defaultRules -> defaultRules + getFlowRules(sw) +
                         northbound.getSwitchRules(srcSwitch.dpId).flowEntries.findAll {
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
        def expectedDeletedRules = northbound.getSwitchRules(data.switch.dpId).flowEntries
                .findAll { it."$data.identifier" ==  ruleToDelete."$data.identifier" &&
                !new Cookie(it.cookie).serviceFlag }
        def deletedRules = switchHelper.deleteSwitchRules(data.switch.dpId, ruleToDelete."$data.identifier")

        then: "The requested rules are really deleted"
        deletedRules.size() == expectedDeletedRules.size()
        Wrappers.wait(RULES_DELETION_TIME) {
            def actualRules = northbound.getSwitchRules(data.switch.dpId).flowEntries
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

        def ingressRule = (northbound.getSwitchRules(srcSwitch.dpId).flowEntries - data.defaultRules).find {
            new Cookie(it.cookie).serviceFlag
        }
        if (ingressRule) {
            data.defaultRules = (data.defaultRules + ingressRule + sharedRulesCount)
        }

        when: "Delete switch rules by non-existing #data.description"
        def deletedRules = switchHelper.deleteSwitchRules(data.switch.dpId, data.value)

        then: "All rules are kept intact"
        deletedRules.size() == 0
        northbound.getSwitchRules(data.switch.dpId).flowEntries.size() == data.defaultRules.size() + flowRulesCount

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
        def cookiesBefore = northbound.getSwitchRules(data.switch.dpId).flowEntries*.cookie.sort()
        def s42IsEnabled = switchHelper.getCachedSwProps(data.switch.dpId).server42FlowRtt

        when: "Delete switch rules by #data.description"
        def deletedRules = switchHelper.deleteSwitchRules(data.switch.dpId, data.inPort, data.inVlan,
                data.encapsulationType, data.outPort)

        then: "The requested rules are really deleted"
        def amountOfDeletedRules = data.removedRules
        if (s42IsEnabled && data.description == "inVlan") {
            amountOfDeletedRules +=  s42FlowRttIngressForwardCount
        }
        deletedRules.size() == amountOfDeletedRules
        Wrappers.wait(RULES_DELETION_TIME) {
            def actualRules = northbound.getSwitchRules(data.switch.dpId).flowEntries
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
    }

    @IterationTag(tags = [SMOKE], iterationNameRegex = /inVlan/)
    def "Attempt to delete switch rules by supplying non-existing #data.description keeps all rules intact"() {
        given: "A switch with some flow rules installed"
        flowFactory.getRandom(srcSwitch, dstSwitch)
        def originalRules = northbound.getSwitchRules(data.switch.dpId).flowEntries*.cookie.sort()

        when: "Delete switch rules by non-existing #data.description"
        def deletedRules = switchHelper.deleteSwitchRules(data.switch.dpId, data.inPort, data.inVlan,
                data.encapsulationType, data.outPort)

        then: "All rules are kept intact"
        deletedRules.size() == 0
        northbound.getSwitchRules(data.switch.dpId).flowEntries*.cookie.sort() == originalRules

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
        def involvedSwitches = flow.retrieveAllEntityPaths().getInvolvedSwitches()
        def defaultPlusFlowRulesMap = involvedSwitches.collectEntries { switchId ->
            [switchId, northbound.getSwitchRules(switchId).flowEntries]
        }

        def amountOfRulesMap = involvedSwitches.collectEntries { switchId ->
            def swProps = switchHelper.getCachedSwProps(switchId)
            def switchIdInSrcOrDst = (switchId in [switchPair.src.dpId, switchPair.dst.dpId])
            def defaultAmountOfFlowRules = 2 // ingress + egress
            def amountOfServer42Rules = 0
            if(swProps.server42FlowRtt && switchIdInSrcOrDst) {
                amountOfServer42Rules +=1
                switchId == switchPair.src.dpId && flow.source.vlanId && ++amountOfServer42Rules
                switchId == switchPair.dst.dpId && flow.destination.vlanId && ++amountOfServer42Rules
            }

            def rulesCount = defaultAmountOfFlowRules + amountOfServer42Rules + (switchIdInSrcOrDst ? 1 : 0)

            [switchId, (rulesCount)]
        }
        involvedSwitches.each { switchId ->
            switchHelper.deleteSwitchRules(switchId, DeleteRulesAction.IGNORE_DEFAULTS)
            Wrappers.wait(RULES_DELETION_TIME) {
                assert northbound.validateSwitchRules(switchId).missingRules.size() == amountOfRulesMap[switchId]
            }
        }

        when: "Synchronize rules on switches"
        def synchronizedRulesMap = involvedSwitches.collectEntries { switchId ->
            [switchId, northbound.synchronizeSwitchRules(switchId)]
        }

        then: "The corresponding rules are installed on switches"
        involvedSwitches.each { switchId ->
            assert synchronizedRulesMap[switchId].installedRules.size() == amountOfRulesMap[switchId]
            Wrappers.wait(RULES_INSTALLATION_TIME) {
                compareRules(northbound.getSwitchRules(switchId).flowEntries, defaultPlusFlowRulesMap[switchId])
            }
        }

        and: "No missing rules were found after rules validation"
        involvedSwitches.each { switchId ->
            verifyAll(northbound.validateSwitchRules(switchId)) {
                properRules.findAll { !new Cookie(it).serviceFlag }.size() == amountOfRulesMap[switchId]
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

        HashMap<SwitchId, List<FlowEntry>> flowInvolvedSwitchesWithRules = flowPathInfo.getInvolvedSwitches()
                .collectEntries{ [(it): switchRulesFactory.get(it).getRules()] } as HashMap<SwitchId, List<FlowEntry>>
        flow.verifyRulesForProtectedFlowOnSwitches(flowInvolvedSwitchesWithRules)

        def mainFlowPath = flowPathInfo.getPathNodes(Direction.FORWARD, false)
        def protectedFlowPath = flowPathInfo.getPathNodes(Direction.FORWARD, true)
        List<SwitchId> commonNodeIds = mainFlowPath*.switchId.intersect(protectedFlowPath*.switchId)
        List<SwitchId> uniqueNodes = (protectedFlowPath.findAll { !commonNodeIds.contains(it.switchId) } + mainFlowPath.findAll {
            !commonNodeIds.contains(it.switchId)
        })*.switchId.unique()
        def rulesOnSwitchesBefore = (commonNodeIds + uniqueNodes).collectEntries {
            [it, northbound.getSwitchRules(it).flowEntries.sort { it.cookie }]
        }

        and: "Delete flow rules(for main and protected paths) on involved switches for creating missing rules"
        commonNodeIds.each { switchHelper.deleteSwitchRules(it, DeleteRulesAction.IGNORE_DEFAULTS) }
        uniqueNodes.each { switchHelper.deleteSwitchRules(it, DeleteRulesAction.IGNORE_DEFAULTS) }
        commonNodeIds.each { switchId ->
            assert northbound.validateSwitchRules(switchId).missingRules.size() > 0
        }
        uniqueNodes.each { assert northbound.validateSwitchRules(it).missingRules.size() == 2 }

        when: "Synchronize rules on switches"
        commonNodeIds.each {
            def response = northbound.synchronizeSwitchRules(it)
            assert response.missingRules.size() > 0
            assert response.installedRules.sort() == response.missingRules.sort()
            assert response.properRules.findAll { !new Cookie(it).serviceFlag }.empty
            assert response.excessRules.empty
        }
        uniqueNodes.each {
            def response = northbound.synchronizeSwitchRules(it)
            assert response.missingRules.size() == 2
            assert response.installedRules.sort() == response.missingRules.sort()
            assert response.properRules.findAll { !new Cookie(it).serviceFlag }.empty, it
            assert response.excessRules.empty
        }

        then: "No missing rules were found after rules synchronization"
        commonNodeIds.each { switchId ->
            verifyAll(northbound.validateSwitchRules(switchId)) {
                properRules.sort() == rulesOnSwitchesBefore[switchId]*.cookie
                missingRules.empty
                excessRules.empty
            }
        }
        uniqueNodes.each {
            verifyAll(northbound.validateSwitchRules(it)) {
                properRules.findAll { !new Cookie(it).serviceFlag }.size() == 2
                missingRules.empty
                excessRules.empty
            }
        }

        and: "Synced rules are exactly the same as before delete (ignoring irrelevant fields)"
        rulesOnSwitchesBefore.each {
            compareRules(northbound.getSwitchRules(it.key).flowEntries, it.value)
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
        def flowRulesSrcSw = getFlowRules(swPair.src)
        def flowRulesDstSw = getFlowRules(swPair.dst)
        def sharedRuleSrcSw = flowRulesSrcSw.find { new Cookie(it.cookie).getType() == CookieType.SHARED_OF_FLOW &&
                it.match.inPort.toInteger() == flow.source.portNumber }.cookie
        def sharedRuleDstSw = flowRulesDstSw.find { new Cookie(it.cookie).getType() == CookieType.SHARED_OF_FLOW &&
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
        def rulesAfterPassingTrafficSrcSw = getFlowRules(swPair.src)
        def rulesAfterPassingTrafficDstSw = getFlowRules(swPair.dst)
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
        def islToFail = actualFlowPath.flowPath.getInvolvedIsls().first()
        islHelper.breakIsl(islToFail)

        then: "The flow was rerouted after reroute timeout"
        def flowInfoAfterReroute
        List<FlowEntry> rulesAfterRerouteSrcSw
        List<FlowEntry> rulesAfterRerouteDstSw
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            assert flow.retrieveFlowStatus().status == FlowState.UP
            assert flow.retrieveAllEntityPaths() != actualFlowPath
            flowInfoAfterReroute = flow.retrieveDetailsFromDB()
            rulesAfterRerouteSrcSw = getFlowRules(swPair.src)
            rulesAfterRerouteDstSw = getFlowRules(swPair.dst)
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
        def involvedSwitches = flow.retrieveAllEntityPaths().getInvolvedSwitches()
        def transitSwitchIds = involvedSwitches[1..-2]
        def defaultPlusFlowRulesMap = involvedSwitches.collectEntries { switchId ->
            [switchId, northbound.getSwitchRules(switchId).flowEntries]
        }

        def rulesCountMap = involvedSwitches.collectEntries { switchId ->
            def swProps = switchHelper.getCachedSwProps(switchId)
            def switchIdInSrcOrDst = (switchId in [switchPair.src.dpId, switchPair.dst.dpId])
            def defaultAmountOfFlowRules = 2 // ingress + egress
            def amountOfServer42Rules = 0
            if(swProps.server42FlowRtt && switchIdInSrcOrDst) {
                amountOfServer42Rules +=1
                switchId == switchPair.src.dpId && flow.source.vlanId && ++amountOfServer42Rules
                switchId == switchPair.dst.dpId && flow.destination.vlanId && ++amountOfServer42Rules
            }

            def rulesCount = defaultAmountOfFlowRules + amountOfServer42Rules + (switchIdInSrcOrDst ? 1 : 0)
            [switchId, rulesCount]
        }

        involvedSwitches.each { switchId ->
            switchHelper.deleteSwitchRules(switchId, DeleteRulesAction.IGNORE_DEFAULTS)
            Wrappers.wait(RULES_DELETION_TIME) {
                assert northbound.validateSwitchRules(switchId).missingRules.size() == rulesCountMap[switchId]
            }
        }

        when: "Synchronize rules on switches"
        def synchronizedRulesMap = involvedSwitches.collectEntries { switchId ->
            [switchId, northbound.synchronizeSwitchRules(switchId)]
        }

        then: "The corresponding rules are installed on switches"
        involvedSwitches.each { switchId ->
            assert synchronizedRulesMap[switchId].installedRules.size() == rulesCountMap[switchId]
            Wrappers.wait(RULES_INSTALLATION_TIME) {
                compareRules(northbound.getSwitchRules(switchId).flowEntries, defaultPlusFlowRulesMap[switchId])
            }
        }

        and: "Rules are synced correctly"
        // ingressRule should contain "pushVxlan"
        // egressRule should contain "tunnel-id"
        with(northbound.getSwitchRules(switchPair.src.dpId).flowEntries) { rules ->
            assert rules.find {
                it.cookie == flowInfoFromDb.forwardPath.cookie.value
            }.instructions.applyActions.pushVxlan
            assert rules.find {
                it.cookie == flowInfoFromDb.reversePath.cookie.value
            }.match.tunnelId
        }

        with(northbound.getSwitchRules(switchPair.dst.dpId).flowEntries) { rules ->
            assert rules.find {
                it.cookie == flowInfoFromDb.forwardPath.cookie.value
            }.match.tunnelId
            assert rules.find {
                it.cookie == flowInfoFromDb.reversePath.cookie.value
            }.instructions.applyActions.pushVxlan
        }

        transitSwitchIds.each { swId ->
            with(northbound.getSwitchRules(swId).flowEntries) { rules ->
                assert rules.find {
                    it.cookie == flowInfoFromDb.forwardPath.cookie.value
                }.match.tunnelId
                assert rules.find {
                    it.cookie == flowInfoFromDb.reversePath.cookie.value
                }.match.tunnelId
            }
        }

        and: "No missing rules were found after rules validation"
        involvedSwitches.each { switchId ->
            verifyAll(northbound.validateSwitchRules(switchId)) {
                properRules.findAll { !new Cookie(it).serviceFlag }.size() == rulesCountMap[switchId]
                missingRules.empty
                excessRules.empty
            }
        }
    }

    void compareRules(actualRules, expectedRules) {
        assert expect(actualRules.sort { it.cookie }, sameBeanAs(expectedRules.sort { it.cookie })
                .ignoring("byteCount")
                .ignoring("packetCount")
                .ignoring("durationNanoSeconds")
                .ignoring("durationSeconds"))
    }


    List<FlowEntry> filterRules(List<FlowEntry> rules, inPort, inVlan, outPort) {
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

    List<FlowEntry> getFlowRules(Switch sw) {
        switchRulesFactory.get(sw.dpId).getRules().findAll { !(it.cookie in sw.defaultCookies) }.sort()
    }
}
