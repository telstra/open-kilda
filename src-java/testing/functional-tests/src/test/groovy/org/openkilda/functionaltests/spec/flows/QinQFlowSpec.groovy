package org.openkilda.functionaltests.spec.flows

import static groovyx.gpars.GParsPool.withPool
import static org.assertj.core.api.Assertions.assertThat
import static org.junit.jupiter.api.Assumptions.assumeFalse
import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.extension.tags.Tag.TOPOLOGY_DEPENDENT
import static org.openkilda.testing.Constants.RULES_INSTALLATION_TIME
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.flow.FlowNotCreatedExpectedError
import org.openkilda.functionaltests.error.flow.FlowNotCreatedWithConflictExpectedError
import org.openkilda.functionaltests.extension.tags.IterationTag
import org.openkilda.functionaltests.extension.tags.IterationTags
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowActionType
import org.openkilda.functionaltests.helpers.model.FlowEncapsulationType
import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.functionaltests.helpers.model.SwitchRulesFactory
import org.openkilda.messaging.command.switches.DeleteRulesAction
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.cookie.Cookie
import org.openkilda.model.cookie.CookieBase.CookieType
import org.openkilda.northbound.dto.v2.flows.FlowPatchEndpoint
import org.openkilda.northbound.dto.v2.flows.FlowPatchV2
import org.openkilda.testing.service.traffexam.TraffExamService
import org.openkilda.testing.service.traffexam.model.FlowBidirectionalExam

import groovy.transform.Memoized
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Shared

import jakarta.inject.Provider

@Slf4j

class QinQFlowSpec extends HealthCheckSpecification {

    @Autowired
    @Shared
    Provider<TraffExamService> traffExamProvider
    @Autowired
    @Shared
    FlowFactory flowFactory
    @Autowired
    @Shared
    SwitchRulesFactory switchRulesFactory

    @Tags([SMOKE_SWITCHES, TOPOLOGY_DEPENDENT])
    def "System allows to manipulate with QinQ flow\
[srcVlan:#srcVlanId, srcInnerVlan:#srcInnerVlanId, dstVlan:#dstVlanId, dstInnerVlan:#dstInnerVlanId, sw:#swPair.hwSwString()]#trafficDisclaimer"() {
        when: "Create a QinQ flow"
        def qinqFlow = flowFactory.getBuilder(swPair)
                .withSourceVlan(srcVlanId)
                .withSourceInnerVlan(srcInnerVlanId)
                .withDestinationVlan(dstVlanId)
                .withDestinationInnerVlan(dstInnerVlanId)
                .build().sendCreateRequest()

        then: "Response contains correct info about vlanIds"
        qinqFlow.waitForBeingInState(FlowState.UP)
        with(qinqFlow) {
            it.source.vlanId == srcVlanId
            it.source.innerVlanId == srcInnerVlanId
            it.destination.vlanId == dstVlanId
            it.destination.innerVlanId == dstInnerVlanId
        }

        and: "Flow is really created with requested vlanIds"
        with(qinqFlow.retrieveDetails()) {
            it.source.vlanId == srcVlanId
            it.source.innerVlanId == srcInnerVlanId
            it.destination.vlanId == dstVlanId
            it.destination.innerVlanId == dstInnerVlanId
        }

        and: "Flow is valid and pingable"
        qinqFlow.validateAndCollectDiscrepancies().isEmpty()
        qinqFlow.pingAndCollectDiscrepancies().isEmpty()

        and: "The flow allows traffic (if applicable)"
        def traffExam = traffExamProvider.get()
        def examQinQFlow
        if(!trafficDisclaimer) {
            examQinQFlow = qinqFlow.traffExam(traffExam, 1000, 5)
            verifyFlowHasBidirectionalTraffic(examQinQFlow, traffExam)
        }

        and: "Involved switches pass switch validation"
        def involvedSwitchesFlow1 = qinqFlow.retrieveAllEntityPaths().getInvolvedSwitches()
        switchHelper.synchronizeAndCollectFixedDiscrepancies(involvedSwitchesFlow1).isEmpty()

        when: "Create a vlan flow on the same port as QinQ flow"
        def vlanFlow = flowFactory.getBuilder(swPair)
                .withSourcePort(qinqFlow.source.portNumber)
                .withSourceVlan(qinqFlow.source.vlanId + 1)
                .withDestinationPort(qinqFlow.destination.portNumber)
                .withDestinationVlan(qinqFlow.destination.vlanId + 1)
                .build().create()

        then: "Both existing flows are valid"
        [qinqFlow, vlanFlow].each {
            it.validateAndCollectDiscrepancies().isEmpty()
        }

        and: "Involved switches pass switch validation"
        def involvedSwitchesFlow2 = vlanFlow.retrieveAllEntityPaths().getInvolvedSwitches()
        def involvedSwitchesforBothFlows = (involvedSwitchesFlow1 + involvedSwitchesFlow2).unique()
        switchHelper.synchronizeAndCollectFixedDiscrepancies(involvedSwitchesforBothFlows).isEmpty()

        and: "Both flows are pingable"
        [qinqFlow, vlanFlow].each {
            it.pingAndCollectDiscrepancies().isEmpty()
        }

        then: "Both flows allow traffic"
        if(!trafficDisclaimer) {
            def examSimpleFlow = vlanFlow.traffExam(traffExam, 1000, 5)
            verifyFlowHasBidirectionalTraffic(examQinQFlow, traffExam)
            verifyFlowHasBidirectionalTraffic(examSimpleFlow, traffExam)
        }

        when: "Update the QinQ flow(outer/inner vlans)"
        def updateQinqFlowEntity = qinqFlow.tap {
            it.source.vlanId = vlanFlow.source.vlanId
            it.source.innerVlanId = vlanFlow.destination.vlanId
            it.destination.vlanId = vlanFlow.destination.vlanId
            it.destination.innerVlanId = vlanFlow.source.vlanId
        }
        def updatedQinqFlow = qinqFlow.sendUpdateRequest(updateQinqFlowEntity)

        then: "Update response contains correct info about innerVlanIds"
        with(updatedQinqFlow) {
            it.source.vlanId == vlanFlow.source.vlanId
            it.source.innerVlanId == vlanFlow.destination.vlanId
            it.destination.vlanId == vlanFlow.destination.vlanId
            it.destination.innerVlanId == vlanFlow.source.vlanId
        }

        and: "Flow is really updated"
        with(qinqFlow.retrieveDetails()) {
            it.source.vlanId == vlanFlow.source.vlanId
            it.source.innerVlanId == vlanFlow.destination.vlanId
            it.destination.vlanId == vlanFlow.destination.vlanId
            it.destination.innerVlanId == vlanFlow.source.vlanId
        }

        and: "Flow history shows actual info into stateBefore and stateAfter sections"
        def qinqFlowHistoryEntry = qinqFlow.waitForHistoryEvent(FlowActionType.UPDATE)
        with(qinqFlowHistoryEntry.dumps.find { it.type == "stateBefore" }){
            it.sourceVlan == srcVlanId
            it.sourceInnerVlan == srcInnerVlanId
            it.destinationVlan == dstVlanId
            it.destinationInnerVlan ==  dstInnerVlanId
        }
        with(qinqFlowHistoryEntry.dumps.find { it.type == "stateAfter" }){
            it.sourceVlan == vlanFlow.source.vlanId
            it.sourceInnerVlan == vlanFlow.destination.vlanId
            it.destinationVlan == vlanFlow.destination.vlanId
            it.destinationInnerVlan == vlanFlow.source.vlanId
        }

        then: "Both existing flows are still valid and pingable"
        [qinqFlow, vlanFlow].each {
            it.validateAndCollectDiscrepancies().isEmpty()
        }

        [qinqFlow, vlanFlow].each {
            it.pingAndCollectDiscrepancies().isEmpty()
        }

        when: "Delete the flows"
        [qinqFlow, vlanFlow].each { it && it.delete() }

        then: "Flows rules are deleted"
        def allSwitches = topology.activeSwitches
        involvedSwitchesforBothFlows.each { swId ->
            def sw = allSwitches.find { item -> item.dpId == swId }
            def defaultCookies = sw.defaultCookies
            Wrappers.wait(RULES_INSTALLATION_TIME, 1) {
                assertThat(switchRulesFactory.get(swId).getRules()*.cookie.toArray()).as(swId.toString())
                        .containsExactlyInAnyOrder(*defaultCookies)
            }
        }

        and: "Shared rule of flow is deleted"
        [swPair.src.dpId, swPair.dst.dpId].each { swId ->
            assert switchRulesFactory.get(swId).getRules().findAll {
                new Cookie(it.cookie).getType() == CookieType.SHARED_OF_FLOW
            }.empty
        }

        where:
        [srcVlanId, srcInnerVlanId, dstVlanId, dstInnerVlanId, swPair] << [
                [[10, 20, 30, 40],
                [10, 20, 0, 0]],
                getUniqueSwitchPairs()
        ].combinations().collect { it.flatten() }
        trafficDisclaimer = swPair.src.traffGens && swPair.dst.traffGens ? "" : " !WARN: No traffic check!"
    }

    def "System allows to create a single switch QinQ flow\
[srcVlan:#srcVlanId, srcInnerVlan:#srcInnerVlanId, dstVlan:#dstVlanId, dstInnerVlan:#dstInnerVlanId, sw:#swPair.src.hwSwString]#trafficDisclaimer"() {
        given: "Switch default cookies before flow creation have been collected"
        def defaultCookies = swPair.src.defaultCookies

        when: "Create a single switch QinQ flow"
        def qinqFlow = flowFactory.getBuilder(swPair)
                .withSourceVlan(srcVlanId)
                .withSourceInnerVlan(srcInnerVlanId)
                .withDestinationVlan(dstVlanId)
                .withDestinationInnerVlan(dstInnerVlanId)
                .build().create()

        then: "Response contains correct info about vlanIds"
        with(qinqFlow) {
            it.source.vlanId == srcVlanId
            it.source.innerVlanId == srcInnerVlanId
            it.destination.vlanId == dstVlanId
            it.destination.innerVlanId == dstInnerVlanId
        }

        and: "Flow is really created with requested vlanIds"
        with(qinqFlow.retrieveDetails()) {
            it.source.vlanId == srcVlanId
            it.source.innerVlanId == srcInnerVlanId
            it.destination.vlanId == dstVlanId
            it.destination.innerVlanId == dstInnerVlanId
        }

        and: "Flow is valid"
        qinqFlow.validateAndCollectDiscrepancies().isEmpty()

        and: "Unable to ping a one-switch qinq flow"
        verifyAll(qinqFlow.ping()) {
            !it.forward
            !it.reverse
            it.error == "Flow ${qinqFlow.flowId} should not be one-switch flow"
        }

        and: "Involved switches pass switch validation"
        !switchHelper.synchronizeAndCollectFixedDiscrepancies(swPair.src.dpId).isPresent()

        and: "Traffic examination is successful (if possible)"
        if(!trafficDisclaimer) {
            def traffExam = traffExamProvider.get()
            def examQinQFlow = qinqFlow.traffExam(traffExam, 1000, 5)
            verifyFlowHasBidirectionalTraffic(examQinQFlow, traffExam)
        }

        when: "Delete the flow"
        qinqFlow.delete()

        then: "Flow rules are deleted"
        Wrappers.wait(RULES_INSTALLATION_TIME, 1) {
            assertThat(switchRulesFactory.get(swPair.src.dpId).getRules()*.cookie.toArray())
                    .containsExactlyInAnyOrder(*defaultCookies)
        }
        switchRulesFactory.get(swPair.src.dpId).getRules().findAll {
            new Cookie(it.cookie).getType() == CookieType.SHARED_OF_FLOW
        }.empty

        where:
        [srcVlanId, srcInnerVlanId, dstVlanId, dstInnerVlanId, swPair] << [
                [[10, 20, 30, 40],
                 [10, 20, 0, 0]],
                getUniqueSwitchPairs(switchPairs.singleSwitch().getSwitchPairs())
        ].combinations().collect { it.flatten() }
        trafficDisclaimer = swPair.src.traffGens.size() > 1 ? "" : " !WARN: No traffic check!"
    }

    def "System doesn't allow to create a QinQ flow with incorrect innerVlanIds\
(src:#srcInnerVlanId, dst:#dstInnerVlanId)"() {
        given: "A switch pair with enabled multi table mode"
        def swP = switchPairs.all().neighbouring().random()

        when: "Try to create a QinQ flow with incorrect innerVlanId"
        flowFactory.getBuilder(swP)
                .withSourceInnerVlan(srcInnerVlanId)
                .withDestinationInnerVlan(dstInnerVlanId)
                .build().create()

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowNotCreatedExpectedError(expectedErrorDescription).matches(exc)

        where:
        srcInnerVlanId | dstInnerVlanId | expectedErrorDescription
        4096           | 10             | ~/Errors: InnerVlanId must be less than 4096/
        10             | -1             | ~/Errors: InnerVlanId must be non-negative/
    }

    /** System doesn't allow to create a flow with innerVlan and without vlan at the same time.
     * for e.g.: when you create a flow with the following params:
     * vlan == 0 and innerVlan != 0,
     * then flow will be created with vlan != 0 and innerVlan == 0
     */
    def "Flow with innerVlan and vlanId=0 is transformed into a regular vlan flow without innerVlan"() {
        when: "Create a flow with vlanId=0 and innerVlanId!=0"
        def swP = switchPairs.all().random()
        def flowEntity = flowFactory.getBuilder(swP)
                .withSourceVlan(0)
                .withSourceInnerVlan(123)
                .build()
        def flow = flowEntity.create()

        then: "Flow is created but with vlanId!=0 and innerVlanId==0"
        with(flow.retrieveDetails()) {
            it.source.vlanId == flowEntity.source.innerVlanId
            it.source.innerVlanId  == 0
        }

        and: "Flow is valid"
        flow.validateAndCollectDiscrepancies().isEmpty()
    }

    def "System allow to create/update/delete a protected QinQ flow via APIv1"() {
        given: "Two switches with enabled multi table mode"
        def swP = switchPairs.all().withAtLeastNNonOverlappingPaths(2).random()
        def srcDefaultCookies = swP.src.defaultCookies
        def dstDefaultCookies = swP.dst.defaultCookies

        when: "Create a QinQ flow"
        def flowEntity = flowFactory.getBuilder(swP)
                .withSourceInnerVlan(234)
                .withDestinationInnerVlan(432)
                .build()
        def flow = flowEntity.createV1()

        then: "Flow is really created with requested innerVlanId"
        with(flow.retrieveDetailsV1()) {
            it.source.innerVlanId == flowEntity.source.innerVlanId
            it.destination.innerVlanId == flowEntity.destination.innerVlanId
        }

        when: "Update the flow(innerVlan/vlanId) via partialUpdate"
        def newDstVlanId = flow.destination.vlanId + 1
        def newDstInnerVlanId = flow.destination.innerVlanId + 1
        def updateRequest = new FlowPatchV2(
                destination: new FlowPatchEndpoint(
                        innerVlanId: newDstInnerVlanId,
                        vlanId: newDstVlanId
                )
        )
        def response = flow.sendPartialUpdateRequest(updateRequest)

        then: "Partial update response reflects the changes"
        flow.waitForBeingInState(FlowState.UP)
        response.destination.vlanId == newDstVlanId
        response.destination.innerVlanId == newDstInnerVlanId

        and: "Flow is really updated with requested innerVlanId/vlanId"
        with(flow.retrieveDetailsV1()) {
            it.destination.vlanId == newDstVlanId
            it.destination.innerVlanId == newDstInnerVlanId
        }

        and: "Flow is valid and pingable"
        flow.validateAndCollectDiscrepancies().isEmpty()
        flow.pingAndCollectDiscrepancies().isEmpty()

        when: "Delete the flow via APIv1"
        flow.deleteV1()

        then: "Flows rules are deleted"
        Wrappers.wait(RULES_INSTALLATION_TIME, 1) {
            assertThat(switchRulesFactory.get(swP.src.dpId).getRules()*.cookie.toArray())
                    .containsExactlyInAnyOrder(*srcDefaultCookies)
            assertThat(switchRulesFactory.get(swP.dst.dpId).getRules()*.cookie.toArray())
                    .containsExactlyInAnyOrder(*dstDefaultCookies)

        }

        and: "Shared rule of flow is deleted"
        [swP.src.dpId, swP.dst.dpId].each { swId ->
            assert switchRulesFactory.get(swId).getRules().findAll {
                new Cookie(it.cookie).getType() == CookieType.SHARED_OF_FLOW
            }.empty
        }
    }

    def "System allows to create QinQ flow and vlan flow with the same vlan on the same port"() {
        given: "Two switches with enabled multi table mode"
        def swP = switchPairs.all().neighbouring().withTraffgensOnBothEnds().random()

        when: "Create a QinQ flow"
        def flowWithQinQ = flowFactory.getBuilder(swP)
                .withSourceInnerVlan(234)
                .withDestinationInnerVlan(432)
                .build().create()

        and: "Create a flow without QinQ"
        def flowWithoutQinQ = flowFactory.getBuilder(swP)
                .withSourceVlan(0)
                .withSourceInnerVlan(flowWithQinQ.source.vlanId)
                .build().create()

        then: "Both flows allow traffic"
        def traffExam = traffExamProvider.get()
        def examFlowWithtQinQ = flowWithQinQ.traffExam(traffExam, 1000, 5)
        def examFlowWithoutQinQ = flowWithoutQinQ.traffExam(traffExam, 1000, 5)
        verifyFlowHasBidirectionalTraffic(examFlowWithtQinQ, traffExam)
        verifyFlowHasBidirectionalTraffic(examFlowWithoutQinQ, traffExam)
    }

    def "System detects conflict QinQ flows(oVlan: #conflictVlan, iVlan: #conflictInnerVlanId)"() {
        given: "Two switches with enabled multi table mode"
        def swP = switchPairs.all().neighbouring().random()

        when: "Create a first flow"
        def flow = flowFactory.getBuilder(swP)
                .withSourceVlan(vlan)
                .withSourceInnerVlan(innerVlan)
                .build()
        flow.create()

        and: "Try to create a flow which conflicts(vlan) with first flow"
        def conflictFlow = flowFactory.getBuilder(swP)
                .withSourceVlan(conflictVlan)
                .withSourceInnerVlan(conflictInnerVlanId)
                .withSourcePort(flow.source.portNumber)
                .build()
        conflictFlow.create()

        then: "Human readable error is returned"
        def exc = thrown(HttpClientErrorException)
        new FlowNotCreatedWithConflictExpectedError(~/Requested flow \'${conflictFlow.getFlowId()}\' conflicts with\
 existing flow \'${flow.getFlowId()}\'./).matches(exc)

        where:
        vlan | innerVlan | conflictVlan | conflictInnerVlanId
        10   | 100       | 10           | 100
        10   | 0         | 0            | 10
    }

    def "System allows to create more than one QinQ flow on the same port and with the same vlan"() {
        given: "Two switches connected to traffgen and enabled multiTable mode"
        def swP = switchPairs.all().neighbouring().withTraffgensOnBothEnds().random()

        when: "Create a first QinQ flow"
        def flow1 = flowFactory.getBuilder(swP)
                .withSourceInnerVlan(300)
                .withDestinationInnerVlan(400)
                .build().create()

        and: "Create a second QinQ flow"
        def flow2 = flowFactory.getBuilder(swP)
                .withSourceVlan(flow1.source.vlanId)
                .withSourceInnerVlan(flow1.destination.innerVlanId)
                .withDestinationVlan(flow1.destination.vlanId)
                .withDestinationInnerVlan(flow1.source.innerVlanId)
                .build().create()

        then: "Both flow are valid and pingable"
        [flow1, flow2].each { flow ->
            flow.validateAndCollectDiscrepancies().isEmpty()
            flow.pingAndCollectDiscrepancies().isEmpty()
        }

        and: "Flows allow traffic"
        def traffExam = traffExamProvider.get()
        def exam1 = flow1.traffExam(traffExam, 1000, 5)
        def exam2 = flow2.traffExam(traffExam, 1000, 5)
        verifyFlowHasBidirectionalTraffic(exam1, traffExam)
        verifyFlowHasBidirectionalTraffic(exam2, traffExam)

        when: "Delete the second flow"
        flow2.delete()

        then: "The first flow is still valid and pingable"
        flow1.validateAndCollectDiscrepancies().isEmpty()
        flow1.pingAndCollectDiscrepancies().isEmpty()

        and: "The first flow still allows traffic"
        verifyFlowHasBidirectionalTraffic(exam1, traffExam)
    }

    def "System allows to create a single-switch-port QinQ flow\
(srcVlanId: #srcVlanId, srcInnerVlanId: #srcInnerVlanId, dstVlanId: #dstVlanId, dstInnerVlanId: #dstInnerVlanId)"() {
        given: "A switch with enabled multiTable mode"
        def sw = topology.activeSwitches[0]
        def defaultCookies = sw.defaultCookies

        when: "Create a single switch QinQ flow"
        def qinqFlow = flowFactory.getBuilder(sw, sw, false)
                .withSourceVlan(srcVlanId)
                .withSourceInnerVlan(srcInnerVlanId)
                .withDestinationVlan(dstVlanId)
                .withDestinationInnerVlan(dstInnerVlanId)
                .build().create()

        then: "Response contains correct info about vlanIds"
        with(qinqFlow) {
            it.source.vlanId == srcVlanId
            it.source.innerVlanId == srcInnerVlanId
            it.destination.vlanId == dstVlanId
            it.destination.innerVlanId == dstInnerVlanId
        }

        and: "Flow is really created with requested vlanIds"
        with(qinqFlow.retrieveDetails()) {
            it.source.vlanId == srcVlanId
            it.source.innerVlanId == srcInnerVlanId
            it.destination.vlanId == dstVlanId
            it.destination.innerVlanId == dstInnerVlanId
        }

        and: "Flow is valid"
        qinqFlow.validateAndCollectDiscrepancies().isEmpty()

        and: "Involved switches pass switch validation"
        def involvedSwitches = qinqFlow.retrieveAllEntityPaths().getInvolvedSwitches()
        switchHelper.synchronizeAndCollectFixedDiscrepancies(involvedSwitches).isEmpty()

        when: "Delete the flow"
        qinqFlow.delete()

        then: "Flow rules are deleted"
        Wrappers.wait(RULES_INSTALLATION_TIME, 1) {
            assertThat(switchRulesFactory.get(sw.dpId).getRules()*.cookie.toArray()).as(sw.dpId.toString())
                    .containsExactlyInAnyOrder(*defaultCookies)
        }

        where:
        srcVlanId | srcInnerVlanId | dstVlanId | dstInnerVlanId
        10        | 20             | 30        | 40
        10        | 20             | 0         | 0
    }

    @Tags(HARDWARE) //https://github.com/telstra/open-kilda/issues/4783
    @IterationTags([
            @IterationTag(tags=[SMOKE_SWITCHES],
                    iterationNameRegex = /srcVlan:10, srcInnerVlan:20, dstVlan:30, dstInnerVlan:40/)
    ])
    def "System allows to manipulate with QinQ vxlan flow\
[srcVlan:#srcVlanId, srcInnerVlan:#srcInnerVlanId, dstVlan:#dstVlanId, dstInnerVlan:#dstInnerVlanId, sw:#swPair.hwSwString()]#trafficDisclaimer"() {
        when: "Create QinQ vxlan flow"
        def qinqFlow = flowFactory.getBuilder(swPair)
                .withEncapsulationType(FlowEncapsulationType.VXLAN)
                .withSourceVlan(srcVlanId)
                .withSourceInnerVlan(srcInnerVlanId)
                .withDestinationVlan(dstVlanId)
                .withDestinationInnerVlan(dstInnerVlanId)
                .build()
        def response = qinqFlow.create()

        then: "Response contains correct info about vlanIds"
        /** System doesn't allow to create a flow with innerVlan and without vlan at the same time.
         * for e.g.: when you create a flow with the following params:
         * vlan == 0 and innerVlan != 0,
         * then flow will be created with vlan != 0 and innerVlan == 0
         */
        with(response) {
            it.source.vlanId == srcVlanId
            it.source.innerVlanId == srcInnerVlanId
            it.destination.vlanId == dstVlanId
            it.destination.innerVlanId == dstInnerVlanId
        }

        and: "Flow is really created with requested vlanIds"
        with(qinqFlow.retrieveDetails()) {
            it.source.vlanId == srcVlanId
            it.source.innerVlanId == srcInnerVlanId
            it.destination.vlanId == dstVlanId
            it.destination.innerVlanId == dstInnerVlanId
        }

        and: "Flow is valid and pingable"
        qinqFlow.validateAndCollectDiscrepancies().isEmpty()
        qinqFlow.pingAndCollectDiscrepancies().isEmpty()

        and: "The flow allows traffic (if applicable)"
        def traffExam = traffExamProvider.get()
        def examQinQFlow
        if(!trafficDisclaimer) {
            examQinQFlow = qinqFlow.traffExam(traffExam, 1000, 5)
            verifyFlowHasBidirectionalTraffic(examQinQFlow, traffExam)
        }

        and: "Involved switches pass switch validation"
        def involvedSwitchesFlow1 = qinqFlow.retrieveAllEntityPaths().getInvolvedSwitches()
        switchHelper.synchronizeAndCollectFixedDiscrepancies(involvedSwitchesFlow1).isEmpty()

        when: "Create a vlan flow on the same port as QinQ flow"
        def vlanFlow = flowFactory.getBuilder(swPair)
                .withSourcePort(qinqFlow.source.portNumber)
                .withSourceVlan(qinqFlow.source.vlanId + 1)
                .withDestinationPort(qinqFlow.destination.portNumber)
                .withDestinationVlan(qinqFlow.destination.vlanId + 1)
                .build()
        vlanFlow.create()

        then: "Both existing flows are valid"
        [qinqFlow, vlanFlow].each {
            it.validateAndCollectDiscrepancies().isEmpty()
        }

        and: "Involved switches pass switch validation"
        def involvedSwitchesFlow2 = vlanFlow.retrieveAllEntityPaths().getInvolvedSwitches()
        def involvedSwitchesforBothFlows = (involvedSwitchesFlow1 + involvedSwitchesFlow2).unique()
        switchHelper.synchronizeAndCollectFixedDiscrepancies(involvedSwitchesforBothFlows).isEmpty()

        and: "Both flows are pingable"
        qinqFlow.pingAndCollectDiscrepancies().isEmpty()
        vlanFlow.pingAndCollectDiscrepancies().isEmpty()


        then: "Both flows allow traffic"
        if(!trafficDisclaimer) {
            def examSimpleFlow = vlanFlow.traffExam(traffExam, 1000, 5)
            verifyFlowHasBidirectionalTraffic(examQinQFlow, traffExam)
            verifyFlowHasBidirectionalTraffic(examSimpleFlow, traffExam)
        }

        when: "Update the QinQ flow(outer/inner vlans)"
        def updateRequest = qinqFlow.tap {
            it.source.vlanId = vlanFlow.source.vlanId
            it.source.innerVlanId = vlanFlow.destination.vlanId
            it.destination.vlanId = vlanFlow.destination.vlanId
            it.destination.innerVlanId = vlanFlow.source.vlanId
        }
        def updateResponse = qinqFlow.update(updateRequest)

        then: "Update response contains correct info about innerVlanIds"
        with(updateResponse) {
            it.source.vlanId == vlanFlow.source.vlanId
            it.source.innerVlanId == vlanFlow.destination.vlanId
            it.destination.vlanId == vlanFlow.destination.vlanId
            it.destination.innerVlanId == vlanFlow.source.vlanId
        }

        and: "Flow is really updated"
        with(qinqFlow.retrieveDetails()) {
            it.source.vlanId == vlanFlow.source.vlanId
            it.source.innerVlanId == vlanFlow.destination.vlanId
            it.destination.vlanId == vlanFlow.destination.vlanId
            it.destination.innerVlanId == vlanFlow.source.vlanId
        }

        then: "Both existing flows are still valid and pingable"
        [qinqFlow, vlanFlow].each {
            it.validateAndCollectDiscrepancies().isEmpty()
        }
        [qinqFlow, vlanFlow].each {
            it.pingAndCollectDiscrepancies().isEmpty()
        }

        when: "Delete the flows"
        [qinqFlow, vlanFlow].each { it.delete() }

        then: "Flows rules are deleted"
        involvedSwitchesforBothFlows.each { swId ->
            def sw = topology.getActiveSwitches().find { it.dpId == swId }
            def defaultCookies = sw.defaultCookies
            Wrappers.wait(RULES_INSTALLATION_TIME, 1) {
                assertThat(switchRulesFactory.get(swId).getRules()*.cookie.toArray()).as(swId.toString())
                        .containsExactlyInAnyOrder(*defaultCookies)
            }
        }

        and: "Shared rule of flow is deleted"
        [swPair.src.dpId, swPair.dst.dpId].each { swId ->
            assert switchRulesFactory.get(swId).getRules().findAll {
                new Cookie(it.cookie).getType() == CookieType.SHARED_OF_FLOW
            }.empty
        }

        where:
        [srcVlanId, srcInnerVlanId, dstVlanId, dstInnerVlanId, swPair] << [
                [[10, 20, 30, 40],
                 [10, 20, 0, 0]],
                getUniqueSwitchPairs(switchPairs.all()
                        .withTraffgensOnBothEnds()
                        .withBothSwitchesVxLanEnabled()
                        .getSwitchPairs())
        ].combinations().collect { it.flatten() }
        trafficDisclaimer = swPair.src.traffGens && swPair.dst.traffGens ? "" : " !WARN: No traffic check!"
    }

    def "System is able to synchronize switch(flow rules)"() {
        given: "Two switches connected to traffgen and enabled multiTable mode"
        def swP = switchPairs.all().neighbouring().withTraffgensOnBothEnds().random()

        and: "A QinQ flow on the given switches"
        def flow = flowFactory.getBuilder(swP)
                .withBandwidth(100)
                .withSourceInnerVlan(600)
                .withDestinationInnerVlan(700)
                .build().create()

        when: "Delete all flow rules(ingress/egress/shared) on the src switch"
        switchHelper.deleteSwitchRules(swP.src.dpId, DeleteRulesAction.DROP_ALL_ADD_DEFAULTS)

        then: "System detects missing rules on the src switch"
        def amountOfServer42Rules = switchHelper.getCachedSwProps(swP.src.dpId).server42FlowRtt ? 2 : 0
        with(switchHelper.synchronizeAndCollectFixedDiscrepancies(swP.src.dpId).get().rules) {
            it.excess.empty
            it.missing.size() == 3 + amountOfServer42Rules //ingress, egress, shared, server42
        }

        and: "Flow is valid"
        flow.validateAndCollectDiscrepancies().isEmpty()

        and: "The flow allows traffic"
        def traffExam = traffExamProvider.get()
        def examFlow = flow.traffExam(traffExam, 100, 5)
        verifyFlowHasBidirectionalTraffic(examFlow, traffExam)
    }

    def "System doesn't rebuild flow path to more preferable path while updating innerVlanId"() {
        given: "Two active switches connected to traffgens with two possible paths at least"
        def switchPair = switchPairs.all().neighbouring()
                .withTraffgensOnBothEnds()
                .withAtLeastNPaths(2)
                .random()

        and: "A flow"
        def flowEntity = flowFactory.getBuilder(switchPair).build().tap {
            it.source.innerVlanId = it.source.vlanId
            it.destination.innerVlanId = it.destination.vlanId
        }
        def flow = flowEntity.create()

        when: "Make the current path less preferable than alternatives"
        def initialPathIsls = flow.retrieveAllEntityPaths().getInvolvedIsls()
        switchPair.retrieveAvailablePaths().collect { it.getInvolvedIsls() }.findAll { it != initialPathIsls }
                .each { islHelper.makePathIslsMorePreferable(it, initialPathIsls) }

        and: "Update the flow: port number and vlanId on the src/dst endpoints"
        def updatedFlow = flow.deepCopy().tap {
            it.source.innerVlanId = flow.destination.vlanId
            it.destination.innerVlanId = flow.source.vlanId
        }
        flow.update(updatedFlow)

        then: "Flow is really updated"
        with(flow.retrieveDetails()) {
            it.source.innerVlanId == updatedFlow.source.innerVlanId
            it.destination.innerVlanId == updatedFlow.destination.innerVlanId
        }

        and: "Flow is not rerouted"
        Wrappers.timedLoop(rerouteDelay + WAIT_OFFSET / 2) {
            assert flow.retrieveAllEntityPaths().getInvolvedIsls() == initialPathIsls
        }

        and: "System allows traffic on the flow"
        def traffExam = traffExamProvider.get()
        def examFlow = updatedFlow.traffExam(traffExam, 100, 5)
        verifyFlowHasBidirectionalTraffic(examFlow, traffExam)

        and: "Flow is valid"
        flow.validateAndCollectDiscrepancies().isEmpty()

        and: "All involved switches pass switch validation"
        def involvedSwitches = flow.retrieveAllEntityPaths().getInvolvedSwitches()
        switchHelper.synchronizeAndCollectFixedDiscrepancies(involvedSwitches).isEmpty()
    }

    @Memoized
    List<SwitchPair> getUniqueSwitchPairs(List<SwitchPair> suitablePairs = switchPairs.all().getSwitchPairs()) {
        def unpickedUniqueSwitches = topology.activeSwitches.collect { it.hwSwString }.unique(false)
        def unpickedSuitableSwitches = unpickedUniqueSwitches.intersect(
                suitablePairs.collectMany { [it.src.hwSwString, it.dst.hwSwString] }.unique(false))
        def untestedSwitches = unpickedUniqueSwitches - unpickedSuitableSwitches
        if (untestedSwitches) {
            log.warn("Switches left untested: ${untestedSwitches.inspect()}")
        }
        assumeFalse(unpickedSuitableSwitches.empty, "No switches that match required conditions") //not possible?
        def result = []
        while (!unpickedSuitableSwitches.empty) {
            def pairs = suitablePairs.sort(false) { swPair ->
                def score = 0
                swPair.src.hwSwString in unpickedSuitableSwitches && score++
                swPair.dst.hwSwString in unpickedSuitableSwitches && score++
                if (swPair.src.dpId == swPair.dst.dpId) {
                    if (swPair.src.traffGens.size() > 1) score++
                } else {
                    if (swPair.src.traffGens && swPair.dst.traffGens) score++
                }
                return score
            }
            //pick a highest score pair, update list of unpicked switches, re-run
            def pair = pairs.last()
            result << pair
            unpickedSuitableSwitches = unpickedSuitableSwitches - pair.src.hwSwString - pair.dst.hwSwString
        }
        return result
    }

    def verifyFlowHasBidirectionalTraffic(FlowBidirectionalExam examFlow, TraffExamService traffExam) {
        withPool {
            [examFlow.forward, examFlow.reverse].eachParallel { direction ->
                def resources = traffExam.startExam(direction)
                direction.setResources(resources)
                assert traffExam.waitExam(direction).hasTraffic()
            }
        }
    }
}
