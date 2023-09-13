package org.openkilda.functionaltests.spec.flows.haflows

import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.HistoryMaxCountExpectedError
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.HaFlowExtended
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.history.DumpType
import org.openkilda.northbound.dto.v2.haflows.HaFlowPatchPayload
import org.openkilda.testing.service.northbound.model.HaFlowActionType

import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative

@Narrative("""Verify that history records are created for the basic actions applied to Ha-Flow.""")
class HaFlowHistorySpec extends HealthCheckSpecification {

    @Tidy
    def "History records with links details are created during link create operations and can be retrieved with timeline"() {
        given: "HA-Flow has been created"
        def swT = topologyHelper.switchTriplets[0]
        def timeBeforeCreation = System.currentTimeSeconds()
        HaFlowExtended haFlow = HaFlowExtended.build(swT, northboundV2, topology).create()
        haFlow.waitForHistoryEvent(HaFlowActionType.CREATE)

        when: "Request for history records"
        def historyRecord = haFlow.getHistory(timeBeforeCreation, System.currentTimeSeconds())
                .getEntriesByType(HaFlowActionType.CREATE)

        then: "Correct event appears in HA-Flow history and can be retrieved without specifying timeline"
        assert historyRecord.size() == 1
//        https://github.com/telstra/open-kilda/issues/5366
        historyRecord[0].verifyBasicFields(haFlow.haFlowId, HaFlowActionType.CREATE)

        and: "Flow history contains all flow properties in the 'state_after' dump section"
        historyRecord[0].verifyDumpSection(DumpType.STATE_AFTER, haFlow)

        cleanup:
        haFlow && haFlow.delete()
    }

    @Tidy
    def "History records with links details are created during link #updateType operations and can be retrieved without timeline"() {
        given: "HA-Flow has been created"
        def swT = topologyHelper.switchTriplets[0]
        HaFlowExtended haFlow = HaFlowExtended.build(swT, northboundV2, topology).create()

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

        cleanup:
        haFlow && haFlow.delete()

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

    @Tidy
    def "History records without links details are created during link deletion and can be retrieved with timeline"() {
        given: "HA-Flow has been created"
        def swT = topologyHelper.findSwitchTripletWithAlternativePaths()
        Long timeBeforeOperation = System.currentTimeSeconds()
        HaFlowExtended haFlow = HaFlowExtended.build(swT, northboundV2, topology).create()
        println(haFlow)

        when: "Delete Ha-Flow"
        def deletedFlow = haFlow.delete()
        haFlow.waitForHistoryEvent(HaFlowActionType.DELETE)

        then: "Correct event appears in HA-Flow history and can be retrieved with specifying timeline"
        def historyRecord = haFlow.getHistory(timeBeforeOperation, System.currentTimeSeconds())
                .getEntriesByType(HaFlowActionType.DELETE)
        historyRecord.size() == 1

        and: "All basic fields are correct"
//        https://github.com/telstra/open-kilda/issues/5367
        historyRecord[0].verifyBasicFields(haFlow.haFlowId, HaFlowActionType.DELETE)
        historyRecord.dumps.flatten().isEmpty()

        cleanup:
        haFlow && !deletedFlow && haFlow.delete()
    }

    @Tags(LOW_PRIORITY)
    @Tidy
    def "History records are created during link unsuccessful rerouting with root cause details and can be retrieved with or without timeline"() {
        given: "HA-Flow has been created"
        def swT = topologyHelper.switchTriplets[0]
        Long timeBeforeOperation = System.currentTimeSeconds()
        HaFlowExtended haFlow = HaFlowExtended.build(swT, northboundV2, topology).create()

        when: "Deactivate the shared switch"
        def blockData = switchHelper.knockoutSwitch(swT.shared, RW)

        and: "Related ISLs are FAILED"
        def isls = topology.getRelatedIsls(swT.shared)
        Wrappers.wait(discoveryTimeout + WAIT_OFFSET / 2) {
            def allIsls = northbound.getAllLinks()
            isls.each { assert islUtils.getIslInfo(allIsls, it).get().actualState == IslChangeType.FAILED }
        }

        and: "Ha-Flow goes DOWN"
        haFlow.waitForBeingInState(FlowState.DOWN)

        then: "Correct event appears in HA-Flow history and can be retrieved without specifying timeline"
        haFlow.waitForHistoryEvent(HaFlowActionType.REROUTE_FAIL)

        def historyRecordWithoutTimeline = haFlow.getHistory().getEntriesByType(HaFlowActionType.REROUTE_FAIL)
        historyRecordWithoutTimeline.size() == 1

        and: "All basic fields are correct and rerouting failure details are available"
        historyRecordWithoutTimeline[0].verifyBasicFields(haFlow.haFlowId, HaFlowActionType.REROUTE_FAIL)
        verifyAll {
            historyRecordWithoutTimeline.dumps.flatten().isEmpty()
            historyRecordWithoutTimeline[0].payloads[-1].details == "ValidateHaFlowAction failed: HA-flow's $haFlow.haFlowId src switch ${swT.shared.dpId} is not active"
        }

        and: "Correct event appears in HA-Flow history and can be retrieved with specifying timeline"
        def historyRecordsWithTimeline = haFlow.getHistory(timeBeforeOperation, System.currentTimeSeconds())
                .getEntriesByType(HaFlowActionType.REROUTE_FAIL)

        and: "Both retrieved history records are identical"
        historyRecordWithoutTimeline == historyRecordsWithTimeline

        cleanup:
        blockData && switchHelper.reviveSwitch(swT.shared, blockData, true)
        haFlow && haFlow.delete()
    }

    @Tidy
    @Tags(LOW_PRIORITY)
    def "Empty history returned in case filters return no results"() {
        given: "HA-Flow"
        def swT = topologyHelper.getAllNotNeighbouringSwitchTriplets().shuffled().first()
        HaFlowExtended haFlow = HaFlowExtended.build(swT, northboundV2, topology).create()

        when: "Get timestamp after create event"
        def timestampAfterCreate = System.currentTimeSeconds() + 1

        then: "Check HA-Flow history has no entries"
        assert haFlow.getHistory(timestampAfterCreate, System.currentTimeSeconds()).entries.isEmpty()

        cleanup:
        haFlow && haFlow.delete()
    }

    @Tidy
    @Tags(LOW_PRIORITY)
    def "Only requested amount of history records are returned"() {
        given: "HA-Flow has been created"
        def swT = topologyHelper.switchTriplets[0]
        Long timeBeforeOperation = System.currentTimeSeconds()
        HaFlowExtended haFlow = HaFlowExtended.build(swT, northboundV2, topology).create()

        and: "Ha-Flow has been updated"
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

        cleanup:
        haFlow && haFlow.delete()
    }

    @Tidy
    @Tags(LOW_PRIORITY)
    def "History max_count cannot be <1"() {
        when: "Try to get history with max_count 0"
        northboundV2.getHaFlowHistory("random_ha_link", null, null, 0)

        then: "Error due to invalid max_count returned"
        def e = thrown(HttpClientErrorException)
        new HistoryMaxCountExpectedError(0).matches(e)
    }
}
