package org.openkilda.functionaltests.spec.flows

import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE
import static org.openkilda.functionaltests.helpers.thread.FlowHistoryConstants.CREATE_ACTION
import static org.openkilda.functionaltests.helpers.thread.FlowHistoryConstants.CREATE_SUCCESS
import static org.openkilda.functionaltests.helpers.thread.FlowHistoryConstants.UPDATE_ACTION
import static org.openkilda.functionaltests.helpers.thread.FlowHistoryConstants.UPDATE_SUCCESS
import static org.openkilda.testing.Constants.NON_EXISTENT_FLOW_ID

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.messaging.error.MessageError
import org.openkilda.messaging.payload.history.FlowEventPayload
import org.openkilda.model.FlowEncapsulationType
import org.openkilda.model.PathComputationStrategy
import org.openkilda.model.history.FlowEvent
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

import com.github.javafaker.Faker
import groovy.util.logging.Slf4j
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared
import spock.lang.Unroll

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

@Narrative("""Verify that history records are created for the create/update actions.
History record is created in case the create/update action is completed successfully.""")
@Slf4j
class FlowHistoryV2Spec extends HealthCheckSpecification {
    @Shared
    Long specStartTime

    @Shared
    String flowWithHistory
    @Shared
    List<FlowEventPayload> bigHistory

    def setupOnce() {
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

    @Tidy
    def "History records are created for the create/update actions using custom timeline"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        flow.encapsulationType = FlowEncapsulationType.TRANSIT_VLAN
        flow.pathComputationStrategy = PathComputationStrategy.LATENCY
        flow.maxLatency = 12345678
        flowHelperV2.addFlow(flow)

        then: "History record is created"
        Long timestampAfterCreate = System.currentTimeSeconds()
        def flowHistory = northbound.getFlowHistory(flow.flowId, specStartTime, timestampAfterCreate)
        assert flowHistory.size() == 1
        checkHistoryCreateV2Action(flowHistory[0], flow.flowId)

        and: "Flow history contains all flow properties in the dump section"
        with(flowHistory[0].dumps[0]) { dump ->
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
            dump.forwardStatus == "IN_PROGRESS" // issue 3038
            dump.reverseStatus == "IN_PROGRESS"
            dump.reverseMeterId > 0
            dump.allocateProtectedPath == flow.allocateProtectedPath
            dump.encapsulationType.toString() == flow.encapsulationType
            dump.pinned == flow.pinned
            dump.pathComputationStrategy.toString() == flow.pathComputationStrategy
            dump.periodicPings == flow.periodicPings
            dump.maxLatency == flow.maxLatency
        }

        when: "Update the created flow"
        flowHelperV2.updateFlow(flow.flowId, flow.tap { it.description = it.description + "updated" })

        then: "History record is created after updating the flow"
        Long timestampAfterUpdate = System.currentTimeSeconds()
        def flowHistory1 = northbound.getFlowHistory(flow.flowId, specStartTime, timestampAfterUpdate)
        assert flowHistory1.size() == 2
        checkHistoryUpdateAction(flowHistory1[1], flow.flowId)

        while((System.currentTimeSeconds() - timestampAfterUpdate) < 1) {
            TimeUnit.MILLISECONDS.sleep(100);
        }

        when: "Delete the updated flow"
        def deleteResponse = flowHelperV2.deleteFlow(flow.flowId)

        then: "History is still available for the deleted flow"
        def flowHistory3 = northbound.getFlowHistory(flow.flowId, specStartTime, timestampAfterUpdate)
        assert flowHistory3.size() == 2
        checkHistoryDeleteAction(flowHistory3, flow.flowId)

        cleanup:
        !deleteResponse && flowHelperV2.deleteFlow(flow.flowId)
    }

    @Tidy
    @Tags(SMOKE)
    def "History records are created for the create/update actions using custom timeline (v2)"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        flowHelperV2.addFlow(flow)

        then: "History record is created"
        Long timestampAfterCreate = System.currentTimeSeconds()
        verifyAll(northbound.getFlowHistory(flow.flowId, specStartTime, timestampAfterCreate)) { flowH ->
            flowH.size() == 1
            checkHistoryCreateV2Action(flowH[0], flow.flowId)
        }

        when: "Update the created flow"
         def flowInfo = northboundV2.getFlow(flow.flowId)
        flowHelperV2.updateFlow(flowInfo.flowId,
                flowHelperV2.toRequest(flowInfo.tap { it.description = it.description + "updated" }))

        then: "History record is created after updating the flow"
        Long timestampAfterUpdate = System.currentTimeSeconds()
        verifyAll(northbound.getFlowHistory(flow.flowId, specStartTime, timestampAfterUpdate)){ flowH ->
            flowH.size() == 2
            checkHistoryUpdateAction(flowH[1], flow.flowId)
        }

        while((System.currentTimeSeconds() - timestampAfterUpdate) < 1) {
            TimeUnit.MILLISECONDS.sleep(100);
        }

        when: "Delete the updated flow"
        def deleteResponse = flowHelperV2.deleteFlow(flow.flowId)

        then: "History is still available for the deleted flow"
        northbound.getFlowHistory(flow.flowId, specStartTime, timestampAfterUpdate).size() == 2

        cleanup:
        !deleteResponse && flowHelperV2.deleteFlow(flow.flowId)
    }

    @Tidy
    def "History records are created for the create/update actions using default timeline"() {
        when: "Create a flow"
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        flowHelperV2.addFlow(flow)

        then: "History record is created"
        def flowHistory = northbound.getFlowHistory(flow.flowId)
        assert flowHistory.size() == 1
        checkHistoryCreateV2Action(flowHistory[0], flow.flowId)

        when: "Update the created flow"
        flowHelperV2.updateFlow(flow.flowId, flow.tap { it.description = it.description + "updated" })

        then: "History record is created after updating the flow"
        def flowHistory1 = northbound.getFlowHistory(flow.flowId)
        assert flowHistory1.size() == 2
        checkHistoryUpdateAction(flowHistory1[1], flow.flowId)

        when: "Delete the updated flow"
        def deleteResponse = flowHelperV2.deleteFlow(flow.flowId)

        then: "History is still available for the deleted flow"
        def flowHistory3 = northbound.getFlowHistory(flow.flowId)
        assert flowHistory3.size() == 3
        checkHistoryDeleteAction(flowHistory3, flow.flowId)

        cleanup:
        !deleteResponse && flowHelperV2.deleteFlow(flow.flowId)
    }

    @Tidy
    def "History max_count cannot be <1"() {
        when: "Try to get history with max_count 0"
        northbound.getFlowHistory(flowWithHistory, 0)

        then: "Error due to invalid max_count returned"
        def e = thrown(HttpClientErrorException)
        e.statusCode == HttpStatus.BAD_REQUEST
        with(e.responseBodyAsString.to(MessageError)) {
            errorMessage == "Invalid `max_count` argument '0'."
            errorDescription == "`max_count` argument must be positive."
        }
    }

    @Tidy
    @Unroll
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
                        descr: "timeBefore > timeAfter returns empty results",
                        params: [flowWithHistory, bigHistory[2].timestamp, bigHistory[0].timestamp],
                        expectedHistory: []
                ],
                [
                        descr: "Calling history for never existed flow returns empty results",
                        params: [NON_EXISTENT_FLOW_ID],
                        expectedHistory: []
                ]
        ]
    }

    void checkHistoryCreateV2Action(FlowEventPayload flowHistory, String flowId) {
        assert flowHistory.action == CREATE_ACTION
        assert flowHistory.histories.action[-1] == CREATE_SUCCESS
        checkHistoryCommonStuff(flowHistory, flowId)
    }

    void checkHistoryUpdateAction(FlowEventPayload flowHistory, String flowId) {
        assert flowHistory.action == UPDATE_ACTION
        assert flowHistory.histories.action[-1] == UPDATE_SUCCESS
        checkHistoryCommonStuff(flowHistory, flowId)
    }

    void checkHistoryCommonStuff(FlowEventPayload flowHistory, String flowId) {
        assert flowHistory.flowId == flowId
        assert flowHistory.taskId
    }

    /** We pass latest timestamp when changes were done.
     * Just for getting all records from history */
    void checkHistoryDeleteAction(List<FlowEventPayload> flowHistory, String flowId) {
        checkHistoryCreateV2Action(flowHistory[0], flowId)
        checkHistoryUpdateAction(flowHistory[1], flowId)
    }
}
