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
import org.openkilda.messaging.payload.history.HaSubFlowPayload
import org.openkilda.model.FlowEncapsulationType
import org.openkilda.model.SwitchId
import org.openkilda.model.history.DumpType
import org.openkilda.northbound.dto.v2.flows.BaseFlowEndpointV2
import org.openkilda.northbound.dto.v2.haflows.HaFlowPatchPayload
import org.openkilda.testing.service.northbound.model.HaFlowActionType

import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative

@Narrative("""Verify that history records are created for the basic actions applied to Ha-Flow.""")
class HaFlowHistorySpec extends HealthCheckSpecification {

    @Tidy
    def "History records with links details are created during link #type operations and can be retrieved without timeline"() {
        given: "HA-Flow has been created"
        def swT = topologyHelper.switchTriplets[0]
        HaFlowExtended haFlow = HaFlowExtended.build(swT, northboundV2, topology).create()

        when: "#type action has been executed"
        action(haFlow)

        then: "Correct event appears in HA-Flow history and can be retrieved without specifying timeline"
        def historyRecord = haFlow.getHistory().getEntriesByType(type)
//        https://github.com/telstra/open-kilda/issues/5320
        if (type != HaFlowActionType.UPDATE) {
            assert historyRecord.size() == 1
        }

        verifyAll {
            historyRecord[0].haFlowId == haFlow.haFlowId
            historyRecord[0].taskId
            historyRecord[0].timestampIso

//            https://github.com/telstra/open-kilda/issues/5366
//            expectedHistoryRecord[0].payloads.action.find {it == type.getPayloadLastAction()}
            historyRecord[0].payloads.every { it.timestampIso }
        }

        and: "Flow history contains all flow properties in the dump section"
        verifyAll {
            with(historyRecord.first().dumps[0]) { dump ->
                dump.dumpType == DumpType.STATE_AFTER
                dump.haFlowId == haFlow.haFlowId

                dump.maximumBandwidth == haFlow.maximumBandwidth
                dump.ignoreBandwidth == haFlow.ignoreBandwidth
                dump.allocateProtectedPath == haFlow.allocateProtectedPath
                dump.encapsulationType.toString() == FlowEncapsulationType.TRANSIT_VLAN.toString()
                dump.pathComputationStrategy.toString() == haFlow.pathComputationStrategy.toUpperCase()
                haFlow.maxLatency ? dump.maxLatency == haFlow.maxLatency * 1000000 : true
                dump.periodicPings == haFlow.periodicPings

                dump.sharedSwitchId == haFlow.sharedEndpoint.switchId.toString()
                dump.sharedOuterVlan == haFlow.sharedEndpoint.vlanId
                dump.sharedInnerVlan == haFlow.sharedEndpoint.innerVlanId

                getSubFlowsEndpoints(dump.haSubFlows).sort() == haFlow.subFlows.endpoint.sort()

            }
        }

        cleanup:
        haFlow && haFlow.delete()

        where:
        type                    | action
        HaFlowActionType.CREATE | {}
//      https://github.com/telstra/open-kilda/issues/5320
        HaFlowActionType.UPDATE | { HaFlowExtended flow ->
            flow.maximumBandwidth = 12345
            flow.update(flow.convertToUpdateRequest())
        }
        HaFlowActionType.UPDATE | { HaFlowExtended flow ->
            flow.maximumBandwidth = 23455
            flow.partialUpdate(HaFlowPatchPayload.builder().maximumBandwidth(flow.maximumBandwidth).build())
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

        then: "Correct event appears in HA-Flow history and can be retrieved with specifying timeline"
        def historyRecord = haFlow.getHistory(timeBeforeOperation, System.currentTimeSeconds())
                .getEntriesByType(HaFlowActionType.DELETE)
        historyRecord.size() == 1

        and: "All basic fields are correct"
        verifyAll {
            historyRecord[0].haFlowId == haFlow.haFlowId
            historyRecord[0].taskId
            historyRecord[0].timestampIso

//            https://github.com/telstra/open-kilda/issues/5367
//            historyRecordWithoutTimeline[0].payloads.action.find {it == type.getPayloadLastAction()}
            historyRecord[0].payloads.every { it.timestampIso }
            historyRecord.dumps.flatten().isEmpty()
        }

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
        Wrappers.wait(WAIT_OFFSET) {
            assert haFlow.getHistory().getEntriesByType(HaFlowActionType.REROUTE_FAIL)[0].payloads.find {
                it.action == HaFlowActionType.REROUTE_FAIL.payloadLastAction
            }
        }

        def historyRecordWithoutTimeline = haFlow.getHistory().getEntriesByType(HaFlowActionType.REROUTE_FAIL)
        historyRecordWithoutTimeline.size() == 1

        and: "All basic fields are correct and rerouting failure details are available"
        verifyAll {
            historyRecordWithoutTimeline[0].haFlowId == haFlow.haFlowId
            historyRecordWithoutTimeline[0].taskId
            historyRecordWithoutTimeline[0].timestampIso
            historyRecordWithoutTimeline[0].payloads.every { it.timestampIso }
            historyRecordWithoutTimeline.dumps.flatten().isEmpty()
            historyRecordWithoutTimeline[0].payloads[-1].action == HaFlowActionType.REROUTE_FAIL.getPayloadLastAction()

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
        haFlow.partialUpdate(HaFlowPatchPayload.builder().maximumBandwidth(12345).build())

        and: "All history records have been retrieved"
        def allHistoryRecordsWithoutFiltering = haFlow.getHistory()

        when: "Request specific amount of history record"
        def historyRecord = haFlow.getHistory(timeBeforeOperation, null, 1)

        then: "The appropriate number of record has been returned"
        historyRecord.entries.size() == 1
        historyRecord.getEntriesByType(HaFlowActionType.UPDATE)

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

    List<BaseFlowEndpointV2> getSubFlowsEndpoints(List<HaSubFlowPayload> dumpSubFlowsDetails) {
        dumpSubFlowsDetails.collect { it ->
            new BaseFlowEndpointV2(switchId: new SwitchId(it.endpointSwitchId), portNumber: it.endpointPort,
                    vlanId: it.endpointVlan, innerVlanId: it.endpointInnerVlan)
        }
    }
}
