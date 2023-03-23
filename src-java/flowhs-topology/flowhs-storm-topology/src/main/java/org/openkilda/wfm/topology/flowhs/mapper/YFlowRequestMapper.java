/* Copyright 2021 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.wfm.topology.flowhs.mapper;

import org.openkilda.messaging.command.yflow.SubFlowDto;
import org.openkilda.messaging.command.yflow.SubFlowSharedEndpointEncapsulation;
import org.openkilda.messaging.command.yflow.YFlowRequest;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.YFlow;
import org.openkilda.model.YSubFlow;
import org.openkilda.wfm.topology.flowhs.model.DetectConnectedDevices;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.Collection;
import java.util.Comparator;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Mapper
public abstract class YFlowRequestMapper {
    public static final YFlowRequestMapper INSTANCE = Mappers.getMapper(YFlowRequestMapper.class);

    @Mapping(target = "yFlowId", source = "YFlowId")
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "yPoint", ignore = true)
    @Mapping(target = "protectedPathYPoint", ignore = true)
    @Mapping(target = "meterId", ignore = true)
    @Mapping(target = "protectedPathMeterId", ignore = true)
    @Mapping(target = "sharedEndpointMeterId", ignore = true)
    public abstract YFlow toYFlow(YFlowRequest request);

    @Mapping(target = "yFlowId", source = "YFlowId")
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "sharedEndpoint.outerVlanId", ignore = true)
    @Mapping(target = "sharedEndpoint.innerVlanId", ignore = true)
    @Mapping(target = "sharedEndpoint.trackLldpConnectedDevices", ignore = true)
    @Mapping(target = "sharedEndpoint.trackArpConnectedDevices", ignore = true)
    @Mapping(target = "diverseFlowId", ignore = true)
    public abstract YFlowRequest toYFlowRequest(YFlow yFlow);

    @Mapping(target = "flowId", source = "subFlowId")
    @Mapping(target = "endpoint", source = "ySubFlow")
    @Mapping(target = "sharedEndpoint", source = "ySubFlow")
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "description", ignore = true)
    @Mapping(target = "timeCreate", ignore = true)
    @Mapping(target = "timeUpdate", ignore = true)
    public abstract SubFlowDto toSubFlowDto(YSubFlow ySubFlow);

    @Mapping(target = "switchId", source = "endpointSwitchId")
    @Mapping(target = "portNumber", source = "endpointPort")
    @Mapping(target = "outerVlanId", source = "endpointVlan")
    @Mapping(target = "innerVlanId", source = "endpointInnerVlan")
    @Mapping(target = "trackLldpConnectedDevices", ignore = true)
    @Mapping(target = "trackArpConnectedDevices", ignore = true)
    public abstract FlowEndpoint toFlowEndpoint(YSubFlow ySubFlow);

    @Mapping(target = "vlanId", source = "sharedEndpointVlan")
    @Mapping(target = "innerVlanId", source = "sharedEndpointInnerVlan")
    public abstract SubFlowSharedEndpointEncapsulation toSubFlowSharedEndpointEncapsulation(YSubFlow ySubFlow);

    /**
     * Convert {@link YFlowRequest} to a few {@link RequestedFlow}.
     */
    public Collection<RequestedFlow> toRequestedFlows(YFlowRequest request) {
        return request.getSubFlows().stream()
                .map(subFlow -> RequestedFlow.builder()
                        .flowId(subFlow.getFlowId())
                        .srcSwitch(request.getSharedEndpoint().getSwitchId())
                        .srcPort(request.getSharedEndpoint().getPortNumber())
                        .srcVlan(subFlow.getSharedEndpoint().getVlanId())
                        .srcInnerVlan(subFlow.getSharedEndpoint().getInnerVlanId())
                        .destSwitch(subFlow.getEndpoint().getSwitchId())
                        .destPort(subFlow.getEndpoint().getPortNumber())
                        .destVlan(subFlow.getEndpoint().getOuterVlanId())
                        .destInnerVlan(subFlow.getEndpoint().getInnerVlanId())
                        .detectConnectedDevices(new DetectConnectedDevices()) //TODO: map it?
                        .description(subFlow.getDescription())
                        .flowEncapsulationType(request.getEncapsulationType())
                        .bandwidth(request.getMaximumBandwidth())
                        .ignoreBandwidth(request.isIgnoreBandwidth())
                        .strictBandwidth(request.isStrictBandwidth())
                        .pinned(request.isPinned())
                        .priority(request.getPriority())
                        .maxLatency(request.getMaxLatency())
                        .maxLatencyTier2(request.getMaxLatencyTier2())
                        .periodicPings(request.isPeriodicPings())
                        .pathComputationStrategy(request.getPathComputationStrategy())
                        .allocateProtectedPath(request.isAllocateProtectedPath())
                        .build())
                .collect(Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(RequestedFlow::getFlowId))));
    }
}
