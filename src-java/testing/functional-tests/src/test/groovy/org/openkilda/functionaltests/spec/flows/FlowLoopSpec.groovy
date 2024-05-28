package org.openkilda.functionaltests.spec.flows

import static groovyx.gpars.GParsPool.withPool
import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.extension.tags.Tag.SWITCH_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.testing.Constants.NON_EXISTENT_FLOW_ID
import static org.openkilda.testing.Constants.NON_EXISTENT_SWITCH_ID
import static org.openkilda.testing.Constants.PROTECTED_PATH_INSTALLATION_TIME
import static org.openkilda.testing.Constants.RULES_DELETION_TIME
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.flow.FlowNotFoundExpectedError
import org.openkilda.functionaltests.error.flow.FlowNotUpdatedExpectedError
import org.openkilda.functionaltests.error.flowloop.FlowLoopNotCreatedExpectedError
import org.openkilda.functionaltests.extension.tags.IterationTag
import org.openkilda.functionaltests.extension.tags.IterationTags
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowActionType
import org.openkilda.functionaltests.helpers.model.FlowEncapsulationType
import org.openkilda.functionaltests.helpers.model.FlowExtended
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.functionaltests.helpers.model.SwitchRulesFactory
import org.openkilda.functionaltests.model.stats.Direction
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v2.flows.FlowLoopPayload
import org.openkilda.testing.service.traffexam.TraffExamService

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.See
import spock.lang.Shared

import javax.inject.Provider

@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/flow-loop")
@Narrative("""Flow loop feature designed for flow path testing. Loop provides additional flow rules on one of the 
terminating switch so any flow traffic is returned to switch-port where it was received. Such flow has 'looped=true'
flag and supports all flow operations. When the loop removed system should restore the original flow rules.
Enabling flowLoop in flow history is registered as the 'update' operation.""")

class FlowLoopSpec extends HealthCheckSpecification {

    @Autowired
    @Shared
    Provider<TraffExamService> traffExamProvider
    @Autowired
    @Shared
    FlowFactory flowFactory
    @Autowired
    @Shared
    SwitchRulesFactory switchRulesFactory

    @IterationTags([
            @IterationTag(tags = [SMOKE_SWITCHES, TOPOLOGY_DEPENDENT], iterationNameRegex = /protected/),
            //https://github.com/telstra/open-kilda/issues/4774
            @IterationTag(tags = [HARDWARE], iterationNameRegex = /vxlan/)
    ])
    def "Able to create flowLoop for a #data.flowDescription flow"() {
        given: "An active and valid  #data.flowDescription flow"
        SwitchPair switchPair = data.switchPair
        FlowExtended flow = data.expectedFlowEntity(switchPair).create()

        when: "Create flowLoop on the src switch"
        def createResponse = flow.createFlowLoop(switchPair.src.dpId)

        then: "Create flowLoop response contains flowId and src switchId"
        assert createResponse.flowId == flow.flowId
        assert createResponse.switchId == switchPair.src.dpId

        and: "Flow is UP and valid"
        Wrappers.wait(WAIT_OFFSET) { assert flow.retrieveFlowStatus().status == FlowState.UP }
        flow.validateAndCollectDiscrepancies().isEmpty()

        and: "FlowLoop is really created on the src switch"
        flow.retrieveDetails().loopSwitchId == switchPair.src.dpId

        and: "FlowLoop rules are created on the src switch"
        def flowLoopRules // [forward, reverse]
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            flowLoopRules = getFlowLoopRules(switchPair.src.dpId)
            assert flowLoopRules.size() == 2
            assert flowLoopRules*.packetCount.every { it == 0 }
        }

        and: "FlowLoop rules are not created on the dst switch"
        getFlowLoopRules(switchPair.dst.dpId).empty

        and: "The src switch is valid"
        switchHelper.synchronizeAndCollectFixedDiscrepancies([switchPair.getSrc().getDpId()]).isEmpty()

        when: "Send traffic via flow in the forward direction"
        def traffExam = traffExamProvider.get()
        def exam = flow.traffExam(traffExam, 1000, 5)
        exam.forward.setResources(traffExam.startExam(exam.forward))

        then: "Flow doesn't allow traffic, because it is grubbed by flowLoop rules"
        !traffExam.waitExam(exam.forward).hasTraffic()
        //rtretiak: sometimes we receive an additional packet after exam is finished. wait for it for better stability
        sleep(1000)

        and: "Counter only on the forward flowLoop rule is increased"
        def flowInfo = database.getFlow(flow.flowId)
        def forwardCookie = flowInfo.forwardPath.cookie.value
        def reverseCookie = flowInfo.reversePath.cookie.value
        def rulesOnSrcSw = switchRulesFactory.get(switchPair.src.dpId).getRules()
        def forwardLoopPacketCount = rulesOnSrcSw.find { it.cookie in flowLoopRules[0].cookie }.packetCount
        forwardLoopPacketCount > 0
        //reverseLoopPacketCount localEnv = 0, stageEnv = 8
        def reverseLoopPacketCount = rulesOnSrcSw.find { it.cookie in flowLoopRules[1].cookie }.packetCount
        forwardLoopPacketCount > reverseLoopPacketCount

        and: "Counter on the simple(ingress/egress) flow rules is not increased on the src switch"
        rulesOnSrcSw.findAll { it.cookie in [forwardCookie, reverseCookie] }*.packetCount.every { it == 0 }

        and: "Counter on the simple(ingress/egress) flow rules on the dst switch is the same as reverseLoopPacketCount"
        switchRulesFactory.get(switchPair.dst.dpId).getRules().findAll {
            it.cookie in [forwardCookie, reverseCookie]
        }*.packetCount.every { it == reverseLoopPacketCount }

        when: "Send traffic via flow in the reverse direction"
        exam.reverse.setResources(traffExam.startExam(exam.reverse))

        then: "Flow doesn't allow traffic, because it is grubbed by flowLoop rules"
        !traffExam.waitExam(exam.reverse).hasTraffic()
        //rtretiak: sometimes we receive an additional packet after exam is finished. wait for it for better stability
        sleep(1000)

        and: "Counter only on the reverse flowLoop rule is increased"
        def rulesOnSrcSw_2 = switchRulesFactory.get(switchPair.src.dpId).getRules()
        def reverseLoopPacketCount_2 = rulesOnSrcSw_2.find { it.cookie in flowLoopRules[1].cookie }.packetCount
        reverseLoopPacketCount_2 > reverseLoopPacketCount
        rulesOnSrcSw_2.find { it.cookie in flowLoopRules[0].cookie }.packetCount == forwardLoopPacketCount

        and: "Counter on the simple(ingress/egress) flow rules is not increased on the src switch"
        rulesOnSrcSw_2.findAll { it.cookie in [forwardCookie, reverseCookie] }*.packetCount.every { it == 0 }

        and: "Counter on the simple(ingress/egress) flow rules is increased on the dst switch"
        with(switchRulesFactory.get(switchPair.dst.dpId).getRules()) {
            it.findAll { it.cookie in [forwardCookie, reverseCookie] }*.packetCount.each {
                assert it == reverseLoopPacketCount_2
            }
        }

        when: "Delete flowLoop"
        def deleteResponse = flow.deleteFlowLoop()

        then: "The delete flowLoop response contains the flowId"
        deleteResponse.flowId == flow.flowId

        and: "FlowLoop is really deleted"
        !flow.retrieveDetails().loopSwitchId

        and: "Flow is UP and valid"
        flow.waitForBeingInState(FlowState.UP)
        flow.validateAndCollectDiscrepancies().isEmpty()

        and: "FlowLoop rules are deleted from the src switch"
        Wrappers.wait(RULES_DELETION_TIME) {
            assert getFlowLoopRules(switchPair.src.dpId).empty
        }

        and: "The src switch is valid"
        !switchHelper.synchronizeAndCollectFixedDiscrepancies(switchPair.getSrc().getDpId()).isPresent()

        and: "Flow allows traffic"
        withPool {
            [exam.forward, exam.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert traffExam.waitExam(direction).hasTraffic()
            }
        }

        where:
        data << [[
                         flowDescription   : "pinned",
                         switchPair        : switchPairs.all().withTraffgensOnBothEnds().random(),
                         expectedFlowEntity: { SwitchPair swPair ->
                             flowFactory.getBuilder(swPair).withPinned(true).build()
                         }
                 ],
                 [
                         flowDescription   : "default",
                         switchPair        : switchPairs.all().withTraffgensOnBothEnds().random(),
                         expectedFlowEntity: { SwitchPair swPair ->
                             flowFactory.getBuilder(swPair).withSourceVlan(0).withDestinationVlan(0).build()
                         }
                 ],
                 [
                         flowDescription   : "protected",
                         switchPair        : switchPairs.all()
                                 .withTraffgensOnBothEnds()
                                 .withAtLeastNNonOverlappingPaths(2)
                                 .random(),
                         expectedFlowEntity: { SwitchPair swPair ->
                             flowFactory.getBuilder(swPair).withProtectedPath(true).build()
                         }
                 ],
                 [
                         flowDescription   : "vxlan",
                         switchPair        : switchPairs.all()
                                 .withTraffgensOnBothEnds()
                                 .withBothSwitchesVxLanEnabled()
                                 .random(),
                         expectedFlowEntity: { SwitchPair swPair ->
                             flowFactory.getBuilder(swPair).withEncapsulationType(FlowEncapsulationType.VXLAN)
                         }
                 ],
                 [
                         flowDescription   : "qinq",
                         switchPair        : switchPairs.all()
                                 .withTraffgensOnBothEnds()
                                 .random(),
                         expectedFlowEntity: { SwitchPair swPair ->
                             flowFactory.getBuilder(swPair)
                                     .withSourceVlan(10)
                                     .withSourceInnerVlan(100)
                                     .withDestinationVlan(20)
                                     .withDestinationInnerVlan(200).build()
                         }
                 ]
        ]
    }

    @Tags(LOW_PRIORITY)
    def "Able to delete a flow with created flowLoop on it"() {
        given: "A active multi switch flow"
        def switchPair = switchPairs.all().random()
        def flow = flowFactory.getRandom(switchPair)

        when: "Create flowLoop on the dst switch"
        flow.createFlowLoop(switchPair.dst.dpId)

        then: "FlowLoop is created on the dst switch"
        def flowLoopOnSwitch = flow.retrieveFlowLoop(switchPair.dst.dpId)
        flowLoopOnSwitch.size() == 1
        with(flowLoopOnSwitch.first()) {
            it.flowId == flow.flowId
            it.switchId == switchPair.dst.dpId
        }

        and: "FlowLoop rules are created"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            assert getFlowLoopRules(switchPair.dst.dpId).size() == 2
        }

        and: "Flow history contains info about flowLoop"
        Wrappers.wait(WAIT_OFFSET / 2) {
            def flowHistory = flow.retrieveFlowHistory().entries.last()
            assert !flowHistory.dumps.find { it.type == "stateBefore" }.loopSwitchId
            assert flowHistory.dumps.find { it.type == "stateAfter" }.loopSwitchId == switchPair.dst.dpId
        }

        when: "Delete the flow"
        flow.delete()

        then: "FlowLoop rules are deleted from the dst switch"
        Wrappers.wait(RULES_DELETION_TIME) {
            assert getFlowLoopRules(switchPair.dst.dpId).empty
        }

        and: "Both switches are valid"
        switchHelper.synchronizeAndCollectFixedDiscrepancies(switchPair.toList()*.getDpId()).isEmpty()
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "System is able to reroute a flow when flowLoop is created on it"() {
        given: "A multi switch flow with one alternative path at least"
        def switchPair = switchPairs.all()
                .nonNeighbouring()
                .withTraffgensOnBothEnds()
                .withAtLeastNNonOverlappingPaths(2)
                .random()
        def flow = flowFactory.getRandom(switchPair)
        def flowPath = flow.retrieveAllEntityPaths()

        and: "FlowLoop is created on the dst switch"
        flow.createFlowLoop(switchPair.dst.dpId)
        def flowLoopOnSwitch = flow.retrieveFlowLoop()
        assert flowLoopOnSwitch.size() == 1
        with(flowLoopOnSwitch.first()) {
            it.flowId == flow.flowId
            it.switchId == switchPair.dst.dpId
        }

        when: "Fail a flow ISL (bring switch port down)"
        def islToFail = flowPath.flowPath.getInvolvedIsls().last()
        islHelper.breakIsl(islToFail)

        then: "The flow was rerouted"
        Wrappers.wait(rerouteDelay + WAIT_OFFSET) {
            assert flow.retrieveDetails().status == FlowState.UP
            assert flow.retrieveAllEntityPaths() != flowPath
        }

        and: "Flow is UP and valid"
        Wrappers.wait(WAIT_OFFSET) { assert flow.retrieveFlowStatus().status == FlowState.UP }
        flow.validateAndCollectDiscrepancies().isEmpty()

        and: "FlowLoop is still present on the dst switch"
        flow.retrieveDetails().loopSwitchId == switchPair.dst.dpId

        and: "FlowLoop rules ar still present on the dst switch"
        getFlowLoopRules(switchPair.dst.dpId).size() == 2

        and: "The src switch is valid"
        !switchHelper.synchronizeAndCollectFixedDiscrepancies(switchPair.getSrc().getDpId()).isPresent()

        and: "Flow doesn't allow traffic, because it is grubbed by flowLoop rules"
        def traffExam = traffExamProvider.get()
        def exam = flow.traffExam(traffExam, 1000, 5)
        withPool {
            [exam.forward, exam.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert !traffExam.waitExam(direction).hasTraffic()
            }
        }
        getFlowLoopRules(switchPair.src.dpId)*.packetCount.every { it > 0 }
    }

    def "System is able to detect and sync missing flowLoop rules"() {
        given: "An active flow with created flowLoop on the src switch"
        def switchPair = switchPairs.all().neighbouring().random()
        def sourceSwitchId = switchPair.getSrc().getDpId()
        def flow = flowFactory.getRandom(switchPair)
        flow.createFlowLoop(switchPair.src.dpId)
        def flowLoopRules
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            flowLoopRules = getFlowLoopRules(switchPair.src.dpId)*.cookie
            assert flowLoopRules.size() == 2
        }

        when: "Delete flowLoop rules"
        flowLoopRules.each { switchHelper.deleteSwitchRules(sourceSwitchId, it) }

        then: "System detects missing flowLoop rules"
        Wrappers.wait(RULES_DELETION_TIME) {
            assert switchHelper.validate(sourceSwitchId).rules.missing*.cookie.sort() == flowLoopRules.sort()
        }

        when: "Sync the src switch"
        def syncResponse = switchHelper.synchronizeAndCollectFixedDiscrepancies(sourceSwitchId).get()

        then: "Sync response contains flowLoop rules into the installed section"
        syncResponse.rules.installed.sort() == flowLoopRules.sort()

        then: "FlowLoop rules are synced"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            assert getFlowLoopRules(switchPair.src.dpId).size() == 2
        }
        !switchHelper.synchronizeAndCollectFixedDiscrepancies(switchPair.src.dpId).isPresent()
    }

    @Tags(LOW_PRIORITY)
    def "Systems allows to get all flowLoops that goes through a switch"() {
        given: "Two active switches"
        def switchPair = switchPairs.all().withAtLeastNNonOverlappingPaths(2).random()
        and: "Three multi switch flows"
        def flow1 = flowFactory.getBuilder(switchPair).withProtectedPath(true).build().create()
        def flow2 = flowFactory.getRandom(switchPair, false, FlowState.UP, flow1.occupiedEndpoints())
        def flow3 = flowFactory.getRandom(switchPair, false, FlowState.UP, flow1.occupiedEndpoints() + flow2.occupiedEndpoints())

        when: "Get all flowLoops from the src switch"
        then: "There is no flowLoop because it is not created yet"
        northboundV2.getFlowLoop(switchPair.src.dpId).empty

        when: "Create flowLoop for flow1 and flow2 on the src switch"
        flow1.createFlowLoop(switchPair.src.dpId)
        flow2.createFlowLoop(switchPair.src.dpId)

        and: "Create flowLoop for the flow3 on the dst switch"
        flow3.createFlowLoop(switchPair.dst.dpId)

        and: "Get all flowLoops from the src switch"
        def flowLoopSrcSw = northboundV2.getFlowLoop(switchPair.src.dpId)

        then: "The created flowLoops for flow1/flow2 are returned in the response list from the src switch"
        flowLoopSrcSw.size() == 2
        flowLoopSrcSw*.flowId.sort() == [flow1, flow2]*.flowId.sort()
        flowLoopSrcSw*.switchId.unique() == [switchPair.src.dpId]

        when: "Get all flowLoops from the dst switch"
        def flowLoopDstSw = northboundV2.getFlowLoop(switchPair.dst.dpId)

        then: "Only flow3 is in the response list from the dst switch"
        flowLoopDstSw.size() == 1
        flowLoopDstSw.first().flowId == flow3.flowId
        flowLoopDstSw.first().switchId == switchPair.dst.dpId
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "System is able to autoSwapPath for a protected flow when flowLoop is created on it"() {
        given: "Two active switches with three diverse paths at least"
        def switchPair = switchPairs.all()
                .withAtLeastNNonOverlappingPaths(3)
                .withTraffgensOnBothEnds()
                .random()

        and: "A protected unmetered flow with flowLoop on the src switch"
        def flow = flowFactory.getBuilder(switchPair)
                .withBandwidth(0)
                .withIgnoreBandwidth(true)
                .withProtectedPath(true).build()
                .create()

        flow.createFlowLoop(switchPair.src.dpId)

        flow.waitForHistoryEvent(FlowActionType.UPDATE)
        assert flow.retrieveFlowStatus().status == FlowState.UP

        when: "Break ISL on the main path (bring port down) to init auto swap"
        def flowPathInfo = flow.retrieveAllEntityPaths()
        def initialProtectedPath = flowPathInfo.getPathNodes(Direction.FORWARD, true)
        def islToBreak = flowPathInfo.flowPath.path.forward.getInvolvedIsls().first()
        islHelper.breakIsl(islToBreak)

        then: "Flow is switched to protected path"
        Wrappers.wait(PROTECTED_PATH_INSTALLATION_TIME) {
            assert flow.retrieveFlowStatus().status == FlowState.UP
            assert flow.validateAndCollectDiscrepancies().isEmpty()
            assert flow.retrieveAllEntityPaths().getPathNodes(Direction.FORWARD, false) == initialProtectedPath
        }

        and: "FlowLoop is still present on the src switch"
        flow.retrieveDetails().loopSwitchId == switchPair.src.dpId

        and: "FlowLoop rules are still exist on the src switch"
        and: "The src switch is valid"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            def flowLoopRules = getFlowLoopRules(switchPair.src.dpId)
            assert flowLoopRules.size() == 2
            assert flowLoopRules*.packetCount.every { it == 0 }
            assert !switchHelper.validateAndCollectFoundDiscrepancies(switchPair.getSrc().getDpId()).isPresent()
        }

        when: "Send traffic via flow"
        def traffExam = traffExamProvider.get()
        def exam = flow.traffExam(traffExam, 1000, 5)

        then: "Flow doesn't allow traffic, because it is grubbed by flowLoop rules"
        withPool {
            [exam.forward, exam.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert !traffExam.waitExam(direction).hasTraffic()
            }
        }

        and: "Counter on the flowLoop rules are increased"
        getFlowLoopRules(switchPair.src.dpId)*.packetCount.every { it > 0 }
    }

    @Tags(LOW_PRIORITY)
    def "Able to create flowLoop for a singleSwitch flow"() {
        given: "An active singleSwitch flow"
        def sw = topology.activeSwitches.first()
        def flow = flowFactory.getRandom(sw, sw)

        when: "Create flowLoop on the sw switch"
        def createResponse = flow.createFlowLoop(sw.dpId)

        then: "Create flowLoop response contains flowId and src switchId"
        assert createResponse.flowId == flow.flowId
        assert createResponse.switchId == sw.dpId

        and: "Flow is UP and valid"
        Wrappers.wait(WAIT_OFFSET) { assert flow.retrieveFlowStatus().status == FlowState.UP }
        flow.validateAndCollectDiscrepancies().isEmpty()

        and: "FlowLoop is really created on the switch"
        def flowLoopOnSwitch = flow.retrieveFlowLoop()
        flowLoopOnSwitch.size() == 1
        with(flowLoopOnSwitch.first()) {
            it.flowId == flow.flowId
            it.switchId == sw.dpId
        }

        and: "FlowLoop rules are created"
        and: "The switch is valid"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            assert getFlowLoopRules(sw.dpId).size() == 2
            assert !switchHelper.validateAndCollectFoundDiscrepancies(sw.getDpId()).isPresent()
        }

        when: "Delete flowLoop"
        flow.deleteFlowLoop()

        then: "FlowLoop is deleted"
        !flow.retrieveDetails().loopSwitchId

        and: "FlowLoop rules are deleted from the switch"
        Wrappers.wait(RULES_DELETION_TIME) {
            assert getFlowLoopRules(sw.dpId).empty
        }

        and: "Flow is UP and valid"
        Wrappers.wait(WAIT_OFFSET) { assert flow.retrieveFlowStatus().status == FlowState.UP }
        flow.validateAndCollectDiscrepancies().isEmpty()

        and: "The switch is valid"
        !switchHelper.synchronizeAndCollectFixedDiscrepancies(sw.getDpId()).isPresent()
    }

    @Tags(LOW_PRIORITY)
    def "Able to create flowLoop for a singleSwitchSinglePort flow"() {
        given: "An active singleSwitchSinglePort flow"
        def sw = topology.activeSwitches.first()
        def flow = flowFactory.getBuilder(sw, sw).withSamePortOnSourceAndDestination().build().create()

        when: "Create flowLoop on the sw switch"
        def createResponse = flow.createFlowLoop(sw.dpId)

        then: "Create flowLoop response contains flowId and src switchId"
        assert createResponse.flowId == flow.flowId
        assert createResponse.switchId == sw.dpId

        and: "Flow is UP and valid"
        Wrappers.wait(WAIT_OFFSET) { assert flow.retrieveFlowStatus().status == FlowState.UP }
        flow.validateAndCollectDiscrepancies().isEmpty()

        and: "FlowLoop is really created on the switch"
        flow.retrieveDetails().loopSwitchId == sw.dpId

        and: "FlowLoop rules are created"
        and: "The switch is valid"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            assert getFlowLoopRules(sw.dpId).size() == 2
        }
        !switchHelper.synchronizeAndCollectFixedDiscrepancies(sw.getDpId()).isPresent()

        when: "Delete the flow with created flowLoop"
        flow.delete()

        then: "FlowLoop rules are deleted from the switch"
        getFlowLoopRules(sw.dpId).empty

        and: "The switch is valid"
        !switchHelper.synchronizeAndCollectFixedDiscrepancies(sw.getDpId()).isPresent()
    }

    @Tags(LOW_PRIORITY)
    def "Attempt to create the exact same flowLoop twice just reinstalls the rules"() {
        given: "An active multi switch flow with created flowLoop on the src switch"
        def switchPair = switchPairs.all().neighbouring().random()
        def flow = flowFactory.getRandom(switchPair)

        flow.createFlowLoop(switchPair.src.dpId)
        Wrappers.wait(WAIT_OFFSET) { assert flow.retrieveFlowStatus().status == FlowState.UP }

        when: "Try to create flowLoop on the src switch again"
        flow.createFlowLoop(switchPair.src.dpId)

        then: "FlowLoop is still present for the src switch"
        flow.retrieveDetails().loopSwitchId == switchPair.src.dpId

        and: "The src/dst switches are valid"
        Wrappers.wait(RULES_INSTALLATION_TIME) {
            assert switchHelper.validateAndCollectFoundDiscrepancies(switchPair.toList()*.getDpId()).isEmpty()
        }

        and: "No extra rules are created on the src/dst switches"
        getFlowLoopRules(switchPair.src.dpId).size() == 2
        getFlowLoopRules(switchPair.dst.dpId).empty

        when: "Delete the flow with created flowLoop"
        flow.delete()

        then: "FlowLoop rules are deleted"
        Wrappers.wait(RULES_DELETION_TIME) {
            assert getFlowLoopRules(switchPair.src.dpId).empty
        }
    }

    @Tags([LOW_PRIORITY, SWITCH_RECOVER_ON_FAIL])
    def "Unable to create flowLoop when a switch is deactivated"() {
        given: "An active flow"
        def switchPair = switchPairs.all().neighbouring().random()
        def flow = flowFactory.getRandom(switchPair)

        and: "Deactivated the src switch"
        switchHelper.knockoutSwitch(switchPair.src, RW)

        when: "Try to create flowLoop on th src switch(deactivated)"
        flow.createFlowLoop(switchPair.src.dpId)

        then: "Human readable error is returned"
        def e = thrown(HttpClientErrorException)
        new FlowNotUpdatedExpectedError(~/Source switch $switchPair.src.dpId is not connected to the controller/).matches(e)
        and: "FlowLoop is not created"
        !flow.retrieveDetails().loopSwitchId

        when: "Try to create flowLoop on th dst switch(activated)"
        flow.createFlowLoop(switchPair.dst.dpId)

        then: "Human readable error is returned" //system can't update the flow when it is down
        def exc = thrown(HttpClientErrorException)
        new FlowNotUpdatedExpectedError(~/Source switch $switchPair.src.dpId is not connected to the controller/).matches(exc)

        then: "FlowLoop is not created"
        !flow.retrieveDetails().loopSwitchId

        and: "FlowLoop rules are not created on the dst switch"
        getFlowLoopRules(switchPair.dst.dpId).empty
    }

    @Tags(LOW_PRIORITY)
    def "Unable to create flowLoop on the src switch when it is already created on the dst switch"() {
        given: "An active flow with created flowLoop on the src switch"
        def switchPair = switchPairs.all().neighbouring().random()
        def flow = flowFactory.getRandom(switchPair)

        flow.createFlowLoop(switchPair.src.dpId)

        when: "Try to create flowLoop on the dst switch"
        flow.createFlowLoop(switchPair.dst.dpId)

        then: "FlowLoop is not created on the dst switch"
        def exc = thrown(HttpClientErrorException)
        new FlowLoopNotCreatedExpectedError(flow.getFlowId(),
                ~/Flow is already looped on switch \'$switchPair.src.dpId\'/).matches(exc)
    }

    @Tags(LOW_PRIORITY)
    def "Unable to create flowLoop on a transit switch"() {
        given: "An active multi switch flow with transit switch"
        def switchPair = switchPairs.all().nonNeighbouring().random()
        def flow = flowFactory.getRandom(switchPair)

        def transitSwId = flow.retrieveAllEntityPaths().getInvolvedSwitches()[1]

        when: "Try to create flowLoop on the transit switch"
        flow.createFlowLoop(transitSwId)

        then: "Human readable error is returned" //flowLoop is no allowed on a transit switch
        def exc = thrown(HttpClientErrorException)
        new FlowNotUpdatedExpectedError(~/Loop switch is not terminating in flow path/).matches(exc)
        then: "FlowLoop is not created"
        !flow.retrieveDetails().loopSwitchId

        and: "FlowLoop rules are not created on the transit switch"
        getFlowLoopRules(transitSwId).empty
    }

    @Tags(LOW_PRIORITY)
    def "Unable to create flowLoop for a non existent flow"() {
        when: "Try to create flowLoop on the transit switch"
        def sw = topology.activeSwitches.first()
        northboundV2.createFlowLoop(NON_EXISTENT_FLOW_ID, new FlowLoopPayload(sw.dpId))

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowNotFoundExpectedError(NON_EXISTENT_FLOW_ID).matches(exc)

        and: "FlowLoop rules are not created on the switch"
        getFlowLoopRules(sw.dpId).empty

        and: "The switch is valid"
        !switchHelper.synchronizeAndCollectFixedDiscrepancies(sw.getDpId()).isPresent()
    }

    @Tags(LOW_PRIORITY)
    def "Unable to create flowLoop on a non existent switch"() {
        given: "An active multi switch flow"
        def swP = switchPairs.all().neighbouring().random()
        def flow = flowFactory.getRandom(swP)

        when: "Try to create flowLoop on a non existent switch"
        flow.createFlowLoop(NON_EXISTENT_SWITCH_ID)

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowNotUpdatedExpectedError(~/Loop switch is not terminating in flow path/).matches(exc)

        and: "FlowLoop rules are not created for the flow"
        !flow.retrieveDetails().loopSwitchId
    }

    def getFlowLoopRules(SwitchId switchId) {
        def forwardLoopRule
        def reverseLoopRule
        switchRulesFactory.get(switchId).getRules().findAll {
            def hexCookie = Long.toHexString(it.cookie)
            if (hexCookie.startsWith("40080000")) {
                forwardLoopRule = it
            } else if (hexCookie.startsWith("20080000")) {
                reverseLoopRule = it
            }
        }
        return forwardLoopRule && reverseLoopRule ? [forwardLoopRule, reverseLoopRule] : []
    }
}
