package org.openkilda.functionaltests.spec.flows.haflows

import static org.openkilda.functionaltests.extension.tags.Tag.HA_FLOW
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.extension.tags.Tag.SWITCH_RECOVER_ON_FAIL
import static org.openkilda.messaging.info.event.IslChangeType.FAILED
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW
import static org.openkilda.testing.service.northbound.model.HaFlowActionType.REROUTE_FAIL

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.HistoryMaxCountExpectedError
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.factory.HaFlowFactory
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.HaFlowExtended
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.history.DumpType
import org.openkilda.northbound.dto.v2.haflows.HaFlowPatchPayload
import org.openkilda.testing.service.northbound.model.HaFlowActionType

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.Shared

@Narrative("""Verify that history records are created for the basic actions applied to HA-Flow.""")
@Tags([HA_FLOW])
class HaFlowHistorySpec extends HealthCheckSpecification {

    @Shared
    @Autowired
    HaFlowFactory haFlowFactory

    def "History records with links details are created during link create operations and can be retrieved with timeline"() {
        given: "HA-Flow has been created"
        def swT = switchTriplets.all().first()
        def timeBeforeCreation = System.currentTimeSeconds()
        HaFlowExtended haFlow = haFlowFactory.getRandom(swT)
        haFlow.waitForHistoryEvent(HaFlowActionType.CREATE)

        when: "Request for history records"
        def historyRecord = haFlow.getHistory(timeBeforeCreation, System.currentTimeSeconds() + 2)
                .getEntriesByType(HaFlowActionType.CREATE)

        then: "Correct event appears in HA-Flow history and can be retrieved without specifying timeline"
        assert historyRecord.size() == 1
//        https://github.com/telstra/open-kilda/issues/5366
        historyRecord[0].verifyBasicFields(haFlow.haFlowId, HaFlowActionType.CREATE)

        and: "Flow history contains all flow properties in the 'state_after' dump section"
        historyRecord[0].verifyDumpSection(DumpType.STATE_AFTER, haFlow)
    }

    def "History records with links details are created during link #updateType operations and can be retrieved without timeline"() {
        given: "HA-Flow has been created"
        def swT = switchTriplets.all().first()
        HaFlowExtended haFlow = haFlowFactory.getRandom(swT)

        when: "#type action has been executed"
        HaFlowExtended haFlowAfterUpdating = update(haFlow)
        haFlow.waitForHistoryEvent(updateType)

        then: "Correct event appears in HA-Flow history and can be retrieved without specifying timeline"
        def historyRecords = haFlow.getHistory()
//        partial update with the following full updating is saved as partial and regular update events (orientdb), not applicable to mysql
//        https://github.com/telstra/open-kilda/pull/5376
        if (updateType != HaFlowActionType.PARTIAL_UPDATE_FULL) {
            assert historyRecords.entries.size() == 2
        }

        def updateHistoryRecord = historyRecords.getEntriesByType(updateType)
        updateHistoryRecord.size() == 1
        updateHistoryRecord[0].verifyBasicFields(haFlow.haFlowId, updateType)

        and: "Flow history contains all flow properties in the 'state_before' dump section"
        updateHistoryRecord[0].verifyDumpSection(DumpType.STATE_BEFORE, haFlow)

        and: "Flow history contains all flow properties in the 'state_after' dump section"
        updateHistoryRecord[0].verifyDumpSection(DumpType.STATE_AFTER, haFlowAfterUpdating)

        where:
        updateType                            | update
        HaFlowActionType.UPDATE               | { HaFlowExtended flow ->
            def flowUpdateRequest = flow.clone()
            flowUpdateRequest.maximumBandwidth = 12345
            flow.update(flowUpdateRequest.convertToUpdateRequest())
        }
        HaFlowActionType.PARTIAL_UPDATE_FULL  | { HaFlowExtended flow ->
            flow.partialUpdate(HaFlowPatchPayload.builder().maximumBandwidth(2345).build())
        }
        HaFlowActionType.PARTIAL_UPDATE_IN_DB | { HaFlowExtended flow ->
            flow.partialUpdate(HaFlowPatchPayload.builder().priority(1).build())
        }
        HaFlowActionType.PARTIAL_UPDATE_FULL  | { HaFlowExtended flow ->
            flow.partialUpdate(HaFlowPatchPayload.builder().priority(1).maximumBandwidth(3425).build())
        }
    }

    def "History records without links details are created during link deletion and can be retrieved with timeline"() {
        given: "HA-Flow has been created"
        def swT = switchTriplets.all().findSwitchTripletWithAlternativePaths()
        Long timeBeforeOperation = System.currentTimeSeconds()
        HaFlowExtended haFlow = haFlowFactory.getRandom(swT)

        when: "Delete HA-Flow"
        haFlow.delete()
        haFlow.waitForHistoryEvent(HaFlowActionType.DELETE)

        then: "Correct event appears in HA-Flow history and can be retrieved with specifying timeline"
        def historyRecord = haFlow.getHistory(timeBeforeOperation, System.currentTimeSeconds() + 2)
                .getEntriesByType(HaFlowActionType.DELETE)
        historyRecord.size() == 1

        and: "All basic fields are correct"
        historyRecord[0].verifyBasicFields(haFlow.haFlowId, HaFlowActionType.DELETE)

        and: "Only 'state_before' dump section is present for deletion event"
        historyRecord[0].verifyDumpSection(DumpType.STATE_BEFORE, haFlow)
        historyRecord.dumps.flatten().size() == 1
    }

    @Tags(LOW_PRIORITY)
    def "History records can be retrieved with timeline in milliseconds format"() {
        given: "HA-Flow"
        def swT = switchTriplets.all().first()
        def timeBeforeAction = System.currentTimeMillis()
        HaFlowExtended haFlow = haFlowFactory.getRandom(swT)

        when: "Get timestamp after create event"
        def historyRecord = haFlow.getHistory(timeBeforeAction, System.currentTimeMillis() + 2000)

        then: "The appropriate history record has been returned"
        verifyAll {
            historyRecord.entries.size() == 1
            historyRecord.getEntriesByType(HaFlowActionType.CREATE)
        }
    }

    @Tags([LOW_PRIORITY, SWITCH_RECOVER_ON_FAIL])
    def "History records are created during link unsuccessful rerouting with root cause details and can be retrieved with or without timeline"() {
        given: "HA-Flow has been created"
        def swT = switchTriplets.all().first()
        Long timeBeforeOperation = System.currentTimeSeconds()
        HaFlowExtended haFlow = haFlowFactory.getRandom(swT)

        when: "Deactivate the shared switch"
        swT.shared.knockout(RW)

        and: "Related ISLs are FAILED"
        Wrappers.wait(discoveryTimeout + WAIT_OFFSET / 2) {
            swT.shared.collectForwardAndReverseRelatedLinks().each { assert it.actualState == FAILED }
        }

        and: "HA-Flow goes DOWN"
        haFlow.waitForBeingInState(FlowState.DOWN)

        then: "Correct event appears in HA-Flow history and can be retrieved without specifying timeline"
        haFlow.waitForHistoryEvent(REROUTE_FAIL)

        def historyRecordWithoutTimeline = haFlow.getHistory().getEntriesByType(REROUTE_FAIL)
        historyRecordWithoutTimeline.size() == 1

        and: "All basic fields are correct and rerouting failure details are available"
        historyRecordWithoutTimeline[0].verifyBasicFields(haFlow.haFlowId, REROUTE_FAIL)
        historyRecordWithoutTimeline[0].payloads[-1].details == "ValidateHaFlowAction failed: HA-flow's $haFlow.haFlowId src switch ${swT.shared.switchId} is not active"
        historyRecordWithoutTimeline[0].verifyDumpSection(DumpType.STATE_BEFORE, haFlow)

        and: "Correct event appears in HA-Flow history and can be retrieved with specifying timeline"
        def historyRecordsWithTimeline = haFlow.getHistory(timeBeforeOperation, System.currentTimeSeconds() + 2)
                .getEntriesByType(REROUTE_FAIL)

        and: "Both retrieved history records are identical"
        historyRecordWithoutTimeline == historyRecordsWithTimeline
    }

    @Tags(LOW_PRIORITY)
    def "Empty history returned in case filters return no results"() {
        given: "HA-Flow"
        def swT = switchTriplets.all().nonNeighbouring().random()
        HaFlowExtended haFlow = haFlowFactory.getRandom(swT)
        haFlow.waitForHistoryEvent(HaFlowActionType.CREATE)

        when: "Get timestamp after create event"
        def timestampAfterCreate = System.currentTimeSeconds() + 1

        then: "Check HA-Flow history has no entries"
        assert haFlow.getHistory(timestampAfterCreate, System.currentTimeSeconds() + 1).entries.isEmpty()
    }

    @Tags(LOW_PRIORITY)
    def "Only requested amount of history records are returned"() {
        given: "HA-Flow has been created"
        def swT = switchTriplets.all().first()
        Long timeBeforeOperation = System.currentTimeSeconds()
        HaFlowExtended haFlow = haFlowFactory.getRandom(swT)

        and: "HA-Flow has been updated"
        haFlow.partialUpdate(HaFlowPatchPayload.builder().priority(1).build())

        and: "All history records have been retrieved"
        def allHistoryRecordsWithoutFiltering = haFlow.getHistory()

        when: "Request specific amount of history record"
        def historyRecord = haFlow.getHistory(timeBeforeOperation, null, 1)

        then: "The appropriate number of record has been returned"
        historyRecord.entries.size() == 1
        historyRecord.getEntriesByType(HaFlowActionType.PARTIAL_UPDATE_IN_DB)

        when: "Requested amount of history record is bigger than actual existing records"
        def historyRecords = haFlow.getHistory(timeBeforeOperation, null, allHistoryRecordsWithoutFiltering.entries.size() + 10)

        then: "Only flow-specific records are available"
        historyRecords.entries.size() == allHistoryRecordsWithoutFiltering.entries.size()
    }

    @Tags(LOW_PRIORITY)
    def "History max_count cannot be <1"() {
        when: "Try to get history with max_count 0"
        northboundV2.getHaFlowHistory("random_ha_link", null, null, 0)

        then: "Error due to invalid max_count returned"
        def e = thrown(HttpClientErrorException)
        new HistoryMaxCountExpectedError(0).matches(e)
    }
}
