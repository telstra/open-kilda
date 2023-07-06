package org.openkilda.functionaltests.spec.flows.haflows

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.extension.tags.Tag.LOW_PRIORITY
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.CREATE_ACTION
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.CREATE_SUCCESS
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.REROUTE_ACTION
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.REROUTE_FAIL
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.UPDATE_ACTION
import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.UPDATE_SUCCESS
import static org.openkilda.testing.Constants.NON_EXISTENT_FLOW_ID
import static org.openkilda.testing.Constants.WAIT_OFFSET
import static org.openkilda.testing.service.floodlight.model.FloodlightConnectMode.RW

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.HistoryMaxCountExpectedError
import org.openkilda.functionaltests.extension.failfast.Tidy
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.HaFlowHelper
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.messaging.payload.history.FlowHistoryEntry
import org.openkilda.model.FlowEncapsulationType
import org.openkilda.model.PathComputationStrategy
import org.openkilda.model.SwitchFeature
import org.openkilda.model.history.FlowEvent
import org.openkilda.northbound.dto.v2.haflows.HaFlow
import org.openkilda.testing.model.topology.TopologyDefinition.Switch

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
class HaFlowHistorySpec extends HealthCheckSpecification {
    @Shared
    Long specStartTime

    @Shared
    HaFlow precreatedHaFlow

    @Shared
    List<FlowHistoryEntry> bigHistory

    @Autowired
    @Shared
    HaFlowHelper haFlowHelper

    def setupSpec() {
        specStartTime = System.currentTimeSeconds()
        def swT = topologyHelper.getAllNotNeighbouringSwitchTriplets().shuffled().first()
        def precreatedHaFlow = haFlowHelper.addHaFlow(haFlowHelper.randomHaFlow(swT))



    }

    @Tidy
    def "History records are created for the create/update actions using custom timeline"() {
        when: "Create a flow"
        assumeTrue(useMultitable, "Multi table is not enabled in kilda configuration")
        def (Switch srcSwitch, Switch dstSwitch) = topology.activeSwitches
        def flow = flowHelperV2.randomFlow(srcSwitch, dstSwitch)
        //set non default values
        flow.ignoreBandwidth = true
        flow.periodicPings = true
        flow.allocateProtectedPath = true
        flow.source.innerVlanId = flow.destination.vlanId
        flow.destination.innerVlanId = flow.source.vlanId
        flow.encapsulationType = FlowEncapsulationType.TRANSIT_VLAN
        flow.pathComputationStrategy = PathComputationStrategy.LATENCY
        flow.maxLatency = 12345678
        flowHelperV2.addFlow(flow)

        Long timestampAfterCreate = System.currentTimeSeconds()

        then: "History record is created"
        def haFlowHistory =  northboundV2.getHaFlowHistory( precreatedHaFlow.haFlowId)


//        def flowHistory = northbound.getFlowHistory(flow.flowId, specStartTime, timestampAfterCreate)
//        assert flowHistory.size() == 1
//        checkHistoryCreateAction(flowHistory[0], flow.flowId)
//
//        and: "Flow history contains all flow properties in the dump section"
//        with(flowHistory[0].dumps[0]) { dump ->
//            dump.type == "stateAfter"
//            dump.bandwidth == flow.maximumBandwidth
//            dump.ignoreBandwidth == flow.ignoreBandwidth
//            dump.forwardCookie > 0
//            dump.reverseCookie > 0
//            dump.sourceSwitch == flow.source.switchId.toString()
//            dump.destinationSwitch == flow.destination.switchId.toString()
//            dump.sourcePort == flow.source.portNumber
//            dump.destinationPort == flow.destination.portNumber
//            dump.sourceVlan == flow.source.vlanId
//            dump.destinationVlan == flow.destination.vlanId
//            dump.forwardMeterId > 0
//            dump.forwardStatus == "IN_PROGRESS" // issue 3038
//            dump.reverseStatus == "IN_PROGRESS"
//            dump.reverseMeterId > 0
//            dump.sourceInnerVlan == flow.source.innerVlanId
//            dump.destinationInnerVlan == flow.destination.innerVlanId
//            dump.allocateProtectedPath == flow.allocateProtectedPath
//            dump.encapsulationType.toString() == flow.encapsulationType
//            dump.pinned == flow.pinned
//            dump.pathComputationStrategy.toString() == flow.pathComputationStrategy
//            dump.periodicPings == flow.periodicPings
//            dump.maxLatency == flow.maxLatency * 1000000
//            //groupId is tested in FlowDiversityV2Spec
//            //loop_switch_id is tested in FlowLoopSpec
//        }
//
//        when: "Update the created flow"
//        def updatedFlow = flow.jacksonCopy().tap {
//            it.maximumBandwidth = flow.maximumBandwidth + 1
//            it.maxLatency = flow.maxLatency + 1
//            it.pinned = !flow.pinned
//            it.allocateProtectedPath = !flow.allocateProtectedPath
//            it.periodicPings = !flow.periodicPings
//            it.source.vlanId = flow.source.vlanId + 1
//            it.destination.vlanId = flow.destination.vlanId + 1
//            it.ignoreBandwidth = !it.ignoreBandwidth
//            it.pathComputationStrategy = PathComputationStrategy.COST.toString()
//            it.description = it.description + "updated"
//        }
//        flowHelperV2.updateFlow(flow.flowId, updatedFlow)

        then: "History record is created after updating the flow"
        Long timestampAfterUpdate = System.currentTimeSeconds()

        assert haFlowHistory.size() == 1
//        checkHistoryUpdateAction(flowHistory1[1], flow.flowId)
//        with (flowHistory1.last().dumps.find { it.type == "stateBefore" }) {
//            it.bandwidth == flow.maximumBandwidth
//            it.maxLatency == flow.maxLatency * 1000000
//            it.pinned == flow.pinned
//            it.periodicPings == flow.periodicPings
//            it.sourceVlan == flow.source.vlanId
//            it.destinationVlan == flow.destination.vlanId
//            it.ignoreBandwidth == flow.ignoreBandwidth
//            it.pathComputationStrategy.toString() == flow.pathComputationStrategy
//        }
//        with (flowHistory1.last().dumps.find { it.type == "stateAfter" }) {
//            it.bandwidth == updatedFlow.maximumBandwidth
//            it.maxLatency == updatedFlow.maxLatency * 1000000
//            it.pinned == updatedFlow.pinned
//            it.periodicPings == updatedFlow.periodicPings
//            it.sourceVlan == updatedFlow.source.vlanId
//            it.destinationVlan == updatedFlow.destination.vlanId
//            it.ignoreBandwidth == updatedFlow.ignoreBandwidth
//            it.pathComputationStrategy.toString() == updatedFlow.pathComputationStrategy
//        }
//
//        while((System.currentTimeSeconds() - timestampAfterUpdate) < 1) {
//            TimeUnit.MILLISECONDS.sleep(100);
//        }
//
//        when: "Delete the updated flow"
//        def deleteResponse = flowHelperV2.deleteFlow(flow.flowId)
//
//        then: "History is still available for the deleted flow"
//        def flowHistory3 = northbound.getFlowHistory(flow.flowId, specStartTime, timestampAfterUpdate)
//        assert flowHistory3.size() == 2
//        checkHistoryDeleteAction(flowHistory3, flow.flowId)
//
//        and: "Flow history statuses returns all flow statuses for the whole life cycle"
//        //create, update, delete
//        northboundV2.getFlowHistoryStatuses(flow.flowId).historyStatuses*.statusBecome == ["UP", "UP", "DELETED"]
//        //check custom timeLine and the 'count' option
//        northboundV2.getFlowHistoryStatuses(flow.flowId, specStartTime, timestampAfterUpdate)
//                .historyStatuses*.statusBecome == ["UP", "UP"]
//        northboundV2.getFlowHistoryStatuses(flow.flowId, 1).historyStatuses*.statusBecome == ["DELETED"]

        cleanup:
        !deleteResponse && flowHelperV2.deleteFlow(flow.flowId)
    }



    void checkHistoryCreateAction(FlowHistoryEntry flowHistory, String flowId) {
        assert flowHistory.action == CREATE_ACTION
        assert flowHistory.payload.action[-1] == CREATE_SUCCESS
        checkHistoryCommonStuff(flowHistory, flowId)
    }

    void checkHistoryUpdateAction(FlowHistoryEntry flowHistory, String flowId) {
        assert flowHistory.action == UPDATE_ACTION
        assert flowHistory.payload.action[-1] == UPDATE_SUCCESS
        checkHistoryCommonStuff(flowHistory, flowId)
    }

    void checkHistoryCommonStuff(FlowHistoryEntry flowHistory, String flowId) {
        assert flowHistory.flowId == flowId
        assert flowHistory.taskId
        assert flowHistory.payload.timestampIso
    }

    /** We pass latest timestamp when changes were done.
     * Just for getting all records from history */
    void checkHistoryDeleteAction(List<FlowHistoryEntry> flowHistory, String flowId) {
        checkHistoryCreateAction(flowHistory[0], flowId)
        checkHistoryUpdateAction(flowHistory[1], flowId)
    }
}
