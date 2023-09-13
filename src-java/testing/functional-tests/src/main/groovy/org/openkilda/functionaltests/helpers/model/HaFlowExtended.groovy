package org.openkilda.functionaltests.helpers.model

import static org.openkilda.testing.Constants.FLOW_CRUD_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.builder.HaFlowBuilder
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.northbound.dto.v2.haflows.HaFlow
import org.openkilda.northbound.dto.v2.haflows.HaFlowPatchPayload
import org.openkilda.northbound.dto.v2.haflows.HaFlowPaths
import org.openkilda.northbound.dto.v2.haflows.HaFlowRerouteResult
import org.openkilda.northbound.dto.v2.haflows.HaFlowSharedEndpoint
import org.openkilda.northbound.dto.v2.haflows.HaFlowUpdatePayload
import org.openkilda.northbound.dto.v2.haflows.HaSubFlow
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.openkilda.testing.service.northbound.model.HaFlowHistoryEntry

import com.fasterxml.jackson.annotation.JsonIgnore
import groovy.transform.AutoClone
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import groovy.transform.builder.Builder
import groovy.util.logging.Slf4j

/* This class represents any kind of interactions with HA flow
This class has to replace *HaFlowHelper in future
 */
@Slf4j
@EqualsAndHashCode(excludes = 'northboundV2, topologyDefinition')
@Builder
@AutoClone
@ToString(includeNames = true, excludes = 'northboundV2, topologyDefinition')
class HaFlowExtended {
    String haFlowId
    FlowState status
    String statusInfo

    HaFlowSharedEndpoint sharedEndpoint

    long maximumBandwidth
    String pathComputationStrategy
    String encapsulationType
    Long maxLatency
    Long maxLatencyTier2
    boolean ignoreBandwidth
    boolean periodicPings
    boolean pinned
    Integer priority
    boolean strictBandwidth
    String description
    boolean allocateProtectedPath

    Set<String> diverseWithFlows
    Set<String> diverseWithYFlows
    Set<String> diverseWithHaFlows

    List<HaSubFlow> subFlows

    String timeCreate
    String timeUpdate

    @JsonIgnore
    NorthboundServiceV2 northboundV2

    @JsonIgnore
    TopologyDefinition topologyDefinition

    HaFlowExtended(HaFlow haFlow, NorthboundServiceV2 northboundV2, TopologyDefinition topologyDefinition) {
        this.haFlowId = haFlow.haFlowId
        this.status = FlowState.getByValue(haFlow.status)
        this.statusInfo = haFlow.statusInfo
        this.sharedEndpoint = haFlow.sharedEndpoint
        this.maximumBandwidth = haFlow.maximumBandwidth
        this.pathComputationStrategy = haFlow.pathComputationStrategy
        this.encapsulationType = haFlow.encapsulationType
        this.maxLatency = haFlow.maxLatency
        this.maxLatencyTier2 = haFlow.maxLatencyTier2
        this.ignoreBandwidth = haFlow.ignoreBandwidth
        this.periodicPings = haFlow.periodicPings
        this.pinned = haFlow.pinned
        this.priority = haFlow.priority
        this.strictBandwidth = haFlow.strictBandwidth
        this.description = haFlow.description
        this.allocateProtectedPath = haFlow.allocateProtectedPath

        this.diverseWithFlows = haFlow.diverseWithFlows
        this.diverseWithYFlows = haFlow.diverseWithYFlows
        this.diverseWithHaFlows = haFlow.diverseWithHaFlows
        this.subFlows = haFlow.subFlows
        this.timeCreate = haFlow.timeCreate
        this.timeUpdate = haFlow.timeUpdate

        this.northboundV2 = northboundV2
        this.topologyDefinition = topologyDefinition
    }

    HaFlowAllEntityPaths getAllEntityPaths() {
        HaFlowPaths haFlowPaths = northboundV2.getHaFlowPaths(haFlowId)
        new HaFlowAllEntityPaths(haFlowPaths, topologyDefinition)
    }

    static HaFlowBuilder build(SwitchTriplet swT, NorthboundServiceV2 northboundV2, TopologyDefinition topologyDefinition,
                               boolean useTraffgenPorts = true, List<Integer> busyEndpoints = []) {
        return new HaFlowBuilder(swT, northboundV2, topologyDefinition, useTraffgenPorts, busyEndpoints)
    }

    HaFlowUpdatePayload convertToUpdateRequest() {
        def haFlowCopy = this.clone()
        def builder = HaFlowUpdatePayload.builder()
        HaFlowUpdatePayload.class.getDeclaredFields()*.name.each {
            builder.diverseFlowId(retrieveAnyDiverseFlow())
            if (haFlowCopy.class.declaredFields*.name.contains(it)) {
                builder."$it" = haFlowCopy."$it"
            }
        }
        return builder.build()
    }

    HaFlowHistory getHistory(Long timeFrom = null, Long timeTo = null, Integer maxCount = null) {
        List<HaFlowHistoryEntry> historyRecords =  northboundV2.getHaFlowHistory(haFlowId, timeFrom, timeTo, maxCount)
        return new HaFlowHistory (historyRecords)
    }

    HaFlow waitForBeingInState(FlowState flowState) {
        def flowDetails
        Wrappers.wait(WAIT_OFFSET) {
            flowDetails = northboundV2.getHaFlow(haFlowId)
            assert FlowState.getByValue(flowDetails.status) == flowState && flowDetails.subFlows.every {
                FlowState.getByValue(it.status) == flowState
            }
        }
        flowDetails
    }

    HaFlowExtended partialUpdate(HaFlowPatchPayload updateRequest) {
        log.debug("Updating ha-flow '${haFlowId}'(partial update)")
        northboundV2.partialUpdateHaFlow(haFlowId, updateRequest)
        def haFlow = waitForBeingInState(FlowState.UP)
        new HaFlowExtended(haFlow, northboundV2, topologyDefinition)
    }

    HaFlowExtended update(HaFlowUpdatePayload updateRequest) {
        log.debug("Updating ha-flow '${haFlowId}'")
        northboundV2.updateHaFlow(haFlowId, updateRequest)
        def haFlow = waitForBeingInState(FlowState.UP)
        new HaFlowExtended(haFlow, northboundV2, topologyDefinition)
    }

    HaFlowRerouteResult reroute() {
        northboundV2.rerouteHaFlow(haFlowId)
    }

    HaFlow delete() {
        Wrappers.wait(WAIT_OFFSET * 2) {
            assert FlowState.getByValue(northboundV2.getHaFlow(haFlowId)?.status) != FlowState.IN_PROGRESS
        }
        log.debug("Deleting ha-flow '$haFlowId'")
        def response = northboundV2.deleteHaFlow(haFlowId)
        Wrappers.wait(FLOW_CRUD_TIMEOUT) {
            assert !northboundV2.getHaFlow(haFlowId)
        }
        return response
    }

    String retrieveAnyDiverseFlow() {
        if (diverseWithFlows) {
            return diverseWithFlows[0]
        } else if (diverseWithYFlows) {
            return diverseWithYFlows[0]
        } else if (diverseWithHaFlows) {
            return diverseWithHaFlows[0]
        } else {
            return null;
        }
    }
}
