package org.openkilda.functionaltests.spec.flows

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SWITCH_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.helpers.model.FlowStatusHistoryEvent.DELETED
import static org.openkilda.functionaltests.helpers.model.FlowStatusHistoryEvent.UP
import static org.openkilda.testing.Constants.NON_EXISTENT_FLOW_ID
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.HistoryMaxCountExpectedError
import org.openkilda.functionaltests.error.InvalidRequestParametersExpectedError
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.factory.FlowFactory
import org.openkilda.functionaltests.helpers.model.FlowActionType
import org.openkilda.functionaltests.helpers.model.FlowEncapsulationType
import org.openkilda.functionaltests.helpers.model.FlowExtended
import org.openkilda.functionaltests.helpers.model.FlowHistory
import org.openkilda.functionaltests.helpers.model.PathComputationStrategy
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.messaging.payload.history.FlowHistoryEntry
import org.openkilda.model.SwitchFeature
import org.openkilda.model.history.FlowEvent
import org.openkilda.northbound.dto.v2.flows.FlowPatchV2
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.tools.SoftAssertions

import com.github.javafaker.Faker
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

@Narrative("""Verify that history records are created for the create/update actions.
History record is created in case the create/update action is completed successfully.""")
@Slf4j

class FlowHistorySpec extends HealthCheckSpecification {
    @Shared
    Long specStartTime

    @Shared
    String flowWithHistory
    @Shared
    List<FlowHistoryEntry> bigHistory
    @Autowired
    @Shared
    FlowFactory flowFactory

    def setupSpec() {
        specStartTime = System.currentTimeSeconds()
        def twoDaysAgo = Instant.now().minus(2, ChronoUnit.DAYS)
        flowWithHistory = new Faker().food().ingredient().replaceAll(/\W/, "") + twoDaysAgo.toEpochMilli()
        log.debug("creating 102 sample history records for $flowWithHistory")
        def events = (0..101).collect {
            FlowEvent.builder()
                     .flowId(flowWithHistory)
                     .action("event$it")
                     .taskId(UUID.randomUUID().toString())
                     .actor("functionalTest")
                     .details("no details")
                     .timestamp(twoDaysAgo.plus(it, ChronoUnit.SECONDS))
                     .build();
        }
        events.each { database.addFlowEvent(it) }
        bigHistory = northbound.getFlowHistory(flowWithHistory, Integer.MAX_VALUE)
        //this additionally proves that max_count can be bigger than the actual amount of results
        assert  bigHistory.size() == 102
    }

    def "History records are created for the create/update actions using custom timeline"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowFactory.getBuilder(srcSwitch, dstSwitch)
        //set non default values
                .withIgnoreBandwidth(true)
                .withPeriodicPing(true)
                .withProtectedPath(true)
                .withEncapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .withPathComputationStrategy(PathComputationStrategy.LATENCY)
                .withMaxLatency(12345678).build().tap {
            it.source.innerVlanId = it.destination.vlanId
            it.destination.innerVlanId = it.source.vlanId
        }.create()

        then: "History record is created"
        Long timestampAfterCreate = System.currentTimeSeconds()
        flow.waitForHistoryEvent(FlowActionType.CREATE)
        def flowHistory = flow.retrieveFlowHistory(specStartTime, timestampAfterCreate)
        assert flowHistory.entries.size() == 1
        checkHistoryCommonStuff(flowHistory, flow.flowId, FlowActionType.CREATE)

        and: "Flow history contains all flow properties in the dump section"
        with(flowHistory.getEntriesByType(FlowActionType.CREATE).first().dumps[0]) { dump ->
            dump.type == "stateAfter"
            dump.bandwidth == flow.maximumBandwidth
            dump.ignoreBandwidth == flow.ignoreBandwidth
            dump.forwardCookie > 0
            dump.reverseCookie > 0
            dump.sourceSwitch == flow.source.switchId.toString()
            dump.destinationSwitch == flow.destination.switchId.toString()
            dump.sourcePort == flow.source.portNumber
            dump.destinationPort == flow.destination.portNumber
            dump.sourceVlan == flow.source.vlanId
            dump.destinationVlan == flow.destination.vlanId
            dump.forwardMeterId > 0
            dump.forwardStatus == "ACTIVE"
            dump.reverseStatus == "ACTIVE"
            dump.reverseMeterId > 0
            dump.sourceInnerVlan == flow.source.innerVlanId
            dump.destinationInnerVlan == flow.destination.innerVlanId
            dump.allocateProtectedPath == flow.allocateProtectedPath
            dump.encapsulationType.toString() == flow.encapsulationType.toString().toUpperCase()
            dump.pinned == flow.pinned
            dump.pathComputationStrategy.toString() == flow.pathComputationStrategy.toString().toUpperCase()
            dump.periodicPings == flow.periodicPings
            dump.maxLatency == flow.maxLatency * 1000000
            //groupId is tested in FlowDiversityV2Spec
            //loop_switch_id is tested in FlowLoopSpec
        }

        when: "Update the created flow"
        def expectedFlowEntity = flow.deepCopy().tap {
            it.maximumBandwidth = flow.maximumBandwidth + 1
            it.maxLatency = flow.maxLatency + 1
            it.pinned = !flow.pinned
            it.allocateProtectedPath = !flow.allocateProtectedPath
            it.periodicPings = !flow.periodicPings
            it.source.vlanId = flow.source.vlanId + 1
            it.destination.vlanId = flow.destination.vlanId + 1
            it.ignoreBandwidth = !it.ignoreBandwidth
            it.pathComputationStrategy = PathComputationStrategy.COST
            it.description = it.description + "updated"
        }
        def updatedFlow = flow.update(expectedFlowEntity)

        then: "History record is created after updating the flow"
        flow.waitForHistoryEvent(FlowActionType.UPDATE)
        Long timestampAfterUpdate = System.currentTimeSeconds()
        def flowHistory1 = updatedFlow.retrieveFlowHistory(specStartTime, timestampAfterUpdate)
        assert flowHistory1.entries.size() == 2
        checkHistoryCommonStuff(flowHistory1, flow.flowId, FlowActionType.UPDATE)

        with (flowHistory1.getEntriesByType(FlowActionType.UPDATE).first().dumps.find { it.type == "stateBefore" }) {
            it.bandwidth == flow.maximumBandwidth
            it.maxLatency == flow.maxLatency * 1000000
            it.pinned == flow.pinned
            it.periodicPings == flow.periodicPings
            it.sourceVlan == flow.source.vlanId
            it.destinationVlan == flow.destination.vlanId
            it.ignoreBandwidth == flow.ignoreBandwidth
            it.pathComputationStrategy.toString() == flow.pathComputationStrategy.toString().toUpperCase()
        }
        with (flowHistory1.getEntriesByType(FlowActionType.UPDATE).first().dumps.find { it.type == "stateAfter" }) {
            it.bandwidth == updatedFlow.maximumBandwidth
            it.maxLatency == updatedFlow.maxLatency * 1000000
            it.pinned == updatedFlow.pinned
            it.periodicPings == updatedFlow.periodicPings
            it.sourceVlan == updatedFlow.source.vlanId
            it.destinationVlan == updatedFlow.destination.vlanId
            it.ignoreBandwidth == updatedFlow.ignoreBandwidth
            it.pathComputationStrategy.toString() == updatedFlow.pathComputationStrategy.toString().toUpperCase()
        }

        while((System.currentTimeSeconds() - timestampAfterUpdate) < 1) {
            TimeUnit.MILLISECONDS.sleep(100)
        }

        when: "Delete the updated flow"
        updatedFlow.delete()

        then: "History is still available for the deleted flow"
        flow.waitForHistoryEvent(FlowActionType.DELETE)
        def flowHistory3 = updatedFlow.retrieveFlowHistory(specStartTime)
        assert flowHistory3.entries.size() == 3
        checkHistoryCommonStuff(flowHistory3, flow.flowId, FlowActionType.CREATE)
        checkHistoryCommonStuff(flowHistory3, flow.flowId, FlowActionType.UPDATE)
        checkHistoryCommonStuff(flowHistory3, flow.flowId, FlowActionType.DELETE)

        and: "Flow history statuses returns all flow statuses for the whole life cycle"
        //create, update, delete
        updatedFlow.retrieveFlowHistoryStatus()*.statusBecome == [UP, UP, DELETED]
        //check custom timeLine and the 'count' option
        updatedFlow.retrieveFlowHistoryStatus(specStartTime, timestampAfterUpdate)*.statusBecome == [UP, UP]
        updatedFlow.retrieveFlowHistoryStatus(1)*.statusBecome == [DELETED]
    }

    def "History records are created for the create/update actions using default timeline"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowFactory.getRandom(srcSwitch, dstSwitch)

        then: "History record is created"
        flow.waitForHistoryEvent(FlowActionType.CREATE)
        def flowHistory = flow.retrieveFlowHistory()
        assert flowHistory.entries.size() == 1
        checkHistoryCommonStuff(flowHistory, flow.flowId, FlowActionType.CREATE)

        when: "Update the created flow"
        flow.update(flow.tap {it.description = it.description + "updated" })

        then: "History record is created after updating the flow"
        flow.waitForHistoryEvent(FlowActionType.UPDATE)
        def flowHistory1 = flow.retrieveFlowHistory()
        assert flowHistory1.entries.size() == 2
        checkHistoryCommonStuff(flowHistory1, flow.flowId, FlowActionType.UPDATE)

        when: "Delete the updated flow"
        flow.delete()

        then: "History is still available for the deleted flow"
        flow.waitForHistoryEvent(FlowActionType.DELETE)
        def flowHistory3 = flow.retrieveFlowHistory()
        assert flowHistory3.entries.size() == 3
        checkHistoryCommonStuff(flowHistory3, flow.flowId, FlowActionType.CREATE)
        checkHistoryCommonStuff(flowHistory3, flow.flowId, FlowActionType.UPDATE)
        checkHistoryCommonStuff(flowHistory3, flow.flowId, FlowActionType.DELETE)
    }

    def "History records are created for the partial update actions #partialUpdateType"() {
        given: "Flow has been created"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowFactory.getBuilder(srcSwitch, dstSwitch)
                .withPathComputationStrategy(PathComputationStrategy.COST_AND_AVAILABLE_BANDWIDTH).build()
                .create()

        when: "Update the created flow"
        def updatedFlow = partialUpdate(flow)

        then: "History record is created after partial flow update"
        flow.waitForHistoryEvent(historyAction)
        def flowHistory = flow.retrieveFlowHistory()
        flowHistory.entries.size() == 2
        checkHistoryCommonStuff(flowHistory, flow.flowId, historyAction)

        and: "History records are with correct flow details in the dump section"
        verifyAll(flowHistory.getEntriesByType(historyAction).last().dumps.find { it.type == "stateBefore" }) {
            it.bandwidth == flow.maximumBandwidth
            it.priority == flow.priority
            it.pinned == flow.pinned
            it.periodicPings == flow.periodicPings
            it.sourceVlan == flow.source.vlanId
            it.destinationVlan == flow.destination.vlanId
            it.ignoreBandwidth == flow.ignoreBandwidth
            it.pathComputationStrategy.toString() == PathComputationStrategy.COST_AND_AVAILABLE_BANDWIDTH.toString().toUpperCase()
//          https://github.com/telstra/open-kilda/issues/5373 (full update: IN_PROGRESS)
//           it.forwardStatus == "ACTIVE"
//           it.reverseStatus == "ACTIVE"
        }

        verifyAll(flowHistory.getEntriesByType(historyAction).last().dumps.find { it.type == "stateAfter" }) {
            it.bandwidth == updatedFlow.maximumBandwidth
            it.priority == updatedFlow.priority
            it.pinned == updatedFlow.pinned
            it.periodicPings == updatedFlow.periodicPings
            it.sourceVlan == updatedFlow.source.vlanId
            it.destinationVlan == updatedFlow.destination.vlanId
            it.ignoreBandwidth == updatedFlow.ignoreBandwidth
            it.pathComputationStrategy.toString() == PathComputationStrategy.COST_AND_AVAILABLE_BANDWIDTH.toString().toUpperCase()
//            https://github.com/telstra/open-kilda/issues/5373 (full update: IN_PROGRESS)
//            it.forwardStatus == "ACTIVE"
//            it.reverseStatus == "ACTIVE"
        }

        where:
        partialUpdateType                | historyAction                            | partialUpdate
        "without the consecutive update" | FlowActionType.PARTIAL_UPDATE_ONLY_IN_DB |
                { FlowExtended flowExtended -> flowExtended.partialUpdate(new FlowPatchV2().tap { priority = 1 }) }

        "with the consecutive update"    | FlowActionType.PARTIAL_UPDATE            |
                { FlowExtended flowExtended -> flowExtended.partialUpdate(new FlowPatchV2().tap { maximumBandwidth = 12345 }) }
    }

    @Tags(LOW_PRIORITY)
    def "History max_count cannot be <1"() {
        when: "Try to get history with max_count 0"
        northbound.getFlowHistory(flowWithHistory, 0)

        then: "Error due to invalid max_count returned"
        def e = thrown(HttpClientErrorException)
        new HistoryMaxCountExpectedError(0).matches(e)    }

    @Tags(LOW_PRIORITY)
    def "Check history: #data.descr"() {
        expect: "#data.descr"
        northbound.getFlowHistory(*data.params) == data.expectedHistory

        where: data << [
                [
                        descr: "No params returns the last 100 results",
                        params: [flowWithHistory],
                        expectedHistory: bigHistory[-100..-1]
                ],
                [
                        descr: "Exact time range without max_count returns all entries for given timeframe(more than 100)",
                        params: [flowWithHistory, bigHistory[0].timestamp, bigHistory[-2].timestamp],
                        expectedHistory: bigHistory[0..-2] //101 entries here
                ],
                [
                        descr: "No time range and max_count returns latest 'max_count' entries",
                        params: [flowWithHistory, 2],
                        expectedHistory: bigHistory[-2, -1]
                ],
                [
                        descr: "Exact time range and max_count returns last 'max_count' entries in given timeframe",
                        params: [flowWithHistory, bigHistory[1].timestamp, bigHistory[-2].timestamp, 2],
                        expectedHistory: bigHistory[-3, -2]
                ],
                [
                        descr: "No timeTo and max_count, present timeFrom returns all available entries from timeFrom to now",
                        params: [flowWithHistory, bigHistory[1].timestamp, null, null],
                        expectedHistory: bigHistory[1..-1] //101
                ],
                [
                        descr: "No timeFrom and max_count, present timeTo returns all available entries until timeTo",
                        params: [flowWithHistory, null, bigHistory[-2].timestamp, null],
                        expectedHistory: bigHistory[0..-2] //101
                ],
                [
                        descr: "Calling history for never existed flow returns empty results",
                        params: [NON_EXISTENT_FLOW_ID],
                        expectedHistory: []
                ]
        ]
    }

    @Tags(LOW_PRIORITY)
    def "Check history: timeBefore > timeAfter returns error"() {
        when: "Request history with timeBefore > timeAfter returns error"
        northbound.getFlowHistory(flowWithHistory, bigHistory[2].timestamp, bigHistory[0].timestamp)

        then: "Error is returned"
        def exc = thrown(HttpClientErrorException)
        new InvalidRequestParametersExpectedError(
                "Invalid 'timeFrom' and 'timeTo' arguments: ${bigHistory[2].timestamp} and ${bigHistory[0].timestamp + 1}",
        ~/'timeFrom' must be less than or equal to 'timeTo'/).matches(exc)
    }

    @Tags([LOW_PRIORITY, SWITCH_RECOVER_ON_FAIL])
    def "Root cause is registered in flow history while rerouting"() {
        given: "An active flow"
        Switch srcSwitch
        Switch dstSwitch
        topology.islsForActiveSwitches.find { isl ->
            srcSwitch = isl.srcSwitch
            dstSwitch = isl.dstSwitch
            [isl.srcSwitch, isl.dstSwitch].any { !it.features.contains(SwitchFeature.NOVIFLOW_COPY_FIELD) }
        } ?: assumeTrue(false, "Wasn't able to find a suitable link")
        def flow = flowFactory.getRandom(srcSwitch, dstSwitch)

        when: "Deactivate the src switch"
        switchHelper.knockoutSwitch(srcSwitch, RW)

        and: "Related ISLs are FAILED"
        def isls = topology.getRelatedIsls(srcSwitch)
        Wrappers.wait(discoveryTimeout + WAIT_OFFSET / 2) {
            def allIsls = northbound.getAllLinks()
            isls.each { assert islUtils.getIslInfo(allIsls, it).get().actualState == IslChangeType.FAILED }
        }

        then: "Flow goes DOWN"
        flow.waitForBeingInState(FlowState.DOWN)
//        https://github.com/telstra/open-kilda/issues/4126
//        assert flow.retrieveDetails().statusInfo == "ValidateFlowAction failed: Flow's $flow.flowId src switch is not active"

        and: "The root cause('Switch is not active') is registered in flow history"
        def failedRerouteEvent = flow.waitForHistoryEvent(FlowActionType.REROUTE_FAILED)
        verifyAll {
            assert failedRerouteEvent.payload[0].action == "Flow rerouting operation has been started."
            assert failedRerouteEvent.payload[1].action == "ValidateFlowAction failed: Flow's $flow.flowId src switch is not active"
        }
    }

    @Tags([LOW_PRIORITY])
    def "History records are created for the create/update actions using custom timeline [v1 api]"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowFactory.getRandomV1(srcSwitch, dstSwitch)
        flow.waitForHistoryEvent(FlowActionType.CREATE)

        then: "History record is created"
        Long timestampAfterCreate = System.currentTimeSeconds()
        verifyAll(flow.retrieveFlowHistory(specStartTime, timestampAfterCreate)) { flowH ->
            flowH.entries.size() == 1
            checkHistoryCommonStuff(flowH, flow.flowId, FlowActionType.CREATE)
        }

        when: "Update the created flow"
        flow.updateV1(flow.tap {it.description = it.description + "updated" })
        flow.waitForHistoryEvent(FlowActionType.UPDATE)

        then: "History record is created after updating the flow"
        Long timestampAfterUpdate = System.currentTimeSeconds()
        verifyAll(flow.retrieveFlowHistory(specStartTime, timestampAfterUpdate)){ flowH ->
            flowH.entries.size() == 2
            checkHistoryCommonStuff(flowH, flow.flowId, FlowActionType.UPDATE)
        }

        while((System.currentTimeSeconds() - timestampAfterUpdate) < 1) {
            TimeUnit.MILLISECONDS.sleep(100);
        }

        when: "Delete the updated flow"
        flow.deleteV1()
        flow.waitForHistoryEvent(FlowActionType.DELETE)

        then: "History is still available for the deleted flow"
        flow.retrieveFlowHistory(specStartTime, timestampAfterUpdate).entries.size() == 2
    }

    void checkHistoryCommonStuff(FlowHistory flowHistory, String flowId, FlowActionType actionType) {
        SoftAssertions softAssertions = new SoftAssertions()
        def historyEvent = flowHistory.getEntriesByType(actionType).first()
        softAssertions.checkSucceeds { assert historyEvent.payload.action[-1] == actionType.payloadLastAction }
        softAssertions.checkSucceeds { assert historyEvent.flowId == flowId }
        softAssertions.checkSucceeds { assert historyEvent.taskId }
        softAssertions.checkSucceeds { assert historyEvent.payload.timestampIso }
        softAssertions.verify()
    }
}
