package org.openkilda.functionaltests.helpers.model

import org.openkilda.functionaltests.helpers.builder.HaFlowBuilder
import org.openkilda.northbound.dto.v2.haflows.HaFlow
import org.openkilda.northbound.dto.v2.haflows.HaFlowPaths
import org.openkilda.northbound.dto.v2.haflows.HaFlowSharedEndpoint
import org.openkilda.northbound.dto.v2.haflows.HaSubFlow
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.northbound.NorthboundServiceV2

import com.fasterxml.jackson.annotation.JsonIgnore
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import groovy.transform.builder.Builder

/* This class represents any kind of interactions with HA flow
This class has to replace *HaFlowHelper in future
 */
@EqualsAndHashCode(excludes = 'northboundV2, topologyDefinition')
@Builder
@ToString(includeNames = true, excludes = 'northboundV2, topologyDefinition')
class HaFlowExtended {
    String haFlowId
    String status
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
        this.status = haFlow.status
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
}