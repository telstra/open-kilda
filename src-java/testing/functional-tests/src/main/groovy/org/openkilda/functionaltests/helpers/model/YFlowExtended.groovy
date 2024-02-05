package org.openkilda.functionaltests.helpers.model

import static org.openkilda.functionaltests.helpers.FlowHistoryConstants.DELETE_SUCCESS_Y
import static org.openkilda.testing.Constants.FLOW_CRUD_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.FlowEncapsulationType
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v2.yflows.SubFlow
import org.openkilda.northbound.dto.v2.yflows.YFlow
import org.openkilda.northbound.dto.v2.yflows.YFlowPatchPayload
import org.openkilda.northbound.dto.v2.yflows.YFlowPaths
import org.openkilda.northbound.dto.v2.yflows.YFlowSharedEndpoint
import org.openkilda.northbound.dto.v2.yflows.YFlowSyncResult
import org.openkilda.northbound.dto.v2.yflows.YFlowValidationResult
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import groovy.transform.builder.Builder
import groovy.util.logging.Slf4j

@Slf4j
@EqualsAndHashCode(excludes = 'northbound, northboundV2, topologyDefinition')
@Builder
@ToString(includeNames = true, excludes = 'northbound, northboundV2, topologyDefinition')
class YFlowExtended {

    String yFlowId
    FlowState status

    YFlowSharedEndpoint sharedEndpoint

    long maximumBandwidth
    String pathComputationStrategy
    FlowEncapsulationType encapsulationType
    Long maxLatency
    Long maxLatencyTier2
    boolean ignoreBandwidth
    boolean periodicPings
    boolean pinned
    Integer priority
    boolean strictBandwidth
    String description
    boolean allocateProtectedPath

    @JsonProperty("y_point")
    SwitchId yPoint;
    @JsonProperty("protected_path_y_point")
    SwitchId protectedPathYPoint

    Set<String> diverseWithFlows
    @JsonProperty("diverse_with_y_flows")
    Set<String> diverseWithYFlows
    Set<String> diverseWithHaFlows

    List<SubFlow> subFlows

    String timeCreate
    String timeUpdate

    @JsonIgnore
    NorthboundService northbound

    @JsonIgnore
    NorthboundServiceV2 northboundV2

    @JsonIgnore
    TopologyDefinition topologyDefinition

    YFlowExtended(YFlow yFlow, NorthboundService northbound,  NorthboundServiceV2 northboundV2, TopologyDefinition topologyDefinition) {
        this.yFlowId = yFlow.YFlowId
        this.status = FlowState.getByValue(yFlow.status)

        this.maximumBandwidth = yFlow.maximumBandwidth
        this.pathComputationStrategy = yFlow.pathComputationStrategy
        this.encapsulationType = FlowEncapsulationType.getByValue(yFlow.encapsulationType)
        this.maxLatency = yFlow.maxLatency
        this.maxLatencyTier2 = yFlow.maxLatencyTier2
        this.ignoreBandwidth = yFlow.ignoreBandwidth
        this.periodicPings = yFlow.periodicPings
        this.pinned = yFlow.pinned
        this.priority = yFlow.priority
        this.strictBandwidth = yFlow.strictBandwidth
        this.description = yFlow.description
        this.allocateProtectedPath = yFlow.allocateProtectedPath

        this.yPoint = yFlow.YPoint
        this.protectedPathYPoint = yFlow.protectedPathYPoint

        this.sharedEndpoint = yFlow.sharedEndpoint
        this.subFlows = yFlow.subFlows

        this.diverseWithFlows = yFlow.diverseWithFlows
        this.diverseWithYFlows = yFlow.diverseWithYFlows
        this.diverseWithHaFlows = yFlow.diverseWithHaFlows

        this.timeCreate = yFlow.timeCreate
        this.timeUpdate = yFlow.timeUpdate

        this.northbound = northbound
        this.northboundV2 = northboundV2
        this.topologyDefinition = topologyDefinition
    }

    FlowWithSubFlowsEntityPath retrieveAllEntityPaths() {
        YFlowPaths yFlowPaths = northboundV2.getYFlowPaths(yFlowId)
        new FlowWithSubFlowsEntityPath(yFlowPaths, topologyDefinition)
    }

    /*
    This method waits for a flow to be in a desired state.
    As Y-Flow(main flow) is a complex flow that contains two regular flows(sub-flows), its state depends on these regular flows.
    In the case of UP or DOWN state, all three flows should be in the same state.
    As for the IN_PROGRESS and DEGRADED states, only the Y-Flow state is checked.
    Verification of sub-flow states should be in the scope of the main test.
     */
    YFlowExtended waitForBeingInState(FlowState flowState) {
        log.debug("Waiting for Y-Flow '${yFlowId}' to be in $flowState")
        YFlow flowDetails
        Wrappers.wait(WAIT_OFFSET) {
            flowDetails = retrieveDetails()
            if (flowState in [FlowState.UP, FlowState.DOWN]) {
                assert FlowState.getByValue(flowDetails.status) == flowState && flowDetails.subFlows.every {
                    FlowState.getByValue(it.status) == flowState
                }
            } else {
                assert FlowState.getByValue(flowDetails.status) == flowState
            }
        }
        new YFlowExtended(flowDetails, northbound, northboundV2, topologyDefinition)
    }

    YFlow retrieveDetails() {
        log.debug("Get Y-Flow '${yFlowId}' details")
        northboundV2.getYFlow(yFlowId)
    }

    YFlowExtended swap() {
        log.debug("Swap Y-Flow '${yFlowId}'")
        def yFlow = northboundV2.swapYFlowPaths(yFlowId)
        new YFlowExtended(yFlow, northbound, northboundV2, topologyDefinition)
    }

    YFlowValidationResult validate() {
        log.debug("Validate Y-Flow '${yFlowId}'")
        northboundV2.validateYFlow(yFlowId)
    }

    YFlowSyncResult sync() {
        log.debug("Synchronize Y-Flow '${yFlowId}'")
        northboundV2.synchronizeYFlow(yFlowId)
    }

    YFlowExtended partialUpdate(YFlowPatchPayload updateRequest, FlowState flowState = FlowState.UP) {
        log.debug("Update Y-Flow '${yFlowId}'(partial update)")
        northboundV2.partialUpdateYFlow(yFlowId, updateRequest)
        waitForBeingInState(flowState)
    }

    /**
     * Deleting Y-Flow and waits when the flow disappears from the flow list.
     */
    YFlow delete() {
        Wrappers.wait(WAIT_OFFSET * 2) {
            assert northboundV2.getYFlow(yFlowId)?.status != FlowState.IN_PROGRESS.toString()
        }
        log.debug("Deleting Y-Flow '$yFlowId'")
        def response = northboundV2.deleteYFlow(yFlowId)
        Wrappers.wait(FLOW_CRUD_TIMEOUT) {
            assert !northboundV2.getYFlow(yFlowId)
            assert northbound.getFlowHistory(response.YFlowId).any{it.payload.last().action == DELETE_SUCCESS_Y}
        }
        // https://github.com/telstra/open-kilda/issues/3411
        northbound.synchronizeSwitch(response.sharedEndpoint.switchId, true)
        return response
    }
}
