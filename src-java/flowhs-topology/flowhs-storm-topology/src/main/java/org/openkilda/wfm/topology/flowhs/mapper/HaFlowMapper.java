/* Copyright 2023 Telstra Open Source
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

import org.openkilda.messaging.command.haflow.HaFlowDto;
import org.openkilda.messaging.command.haflow.HaFlowRequest;
import org.openkilda.messaging.command.haflow.HaSubFlowDto;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.HaFlowRepository;
import org.openkilda.wfm.topology.flowhs.model.DetectConnectedDevices;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Mapper
public abstract class HaFlowMapper {
    public static final HaFlowMapper INSTANCE = Mappers.getMapper(HaFlowMapper.class);

    @Mapping(target = "status", ignore = true)
    @Mapping(target = "sharedSwitch", source = "sharedEndpoint.switchId")
    @Mapping(target = "sharedPort", source = "sharedEndpoint.portNumber")
    @Mapping(target = "sharedOuterVlan", source = "sharedEndpoint.outerVlanId")
    @Mapping(target = "sharedInnerVlan", source = "sharedEndpoint.innerVlanId")
    @Mapping(target = "affinityGroupId", ignore = true)
    @Mapping(target = "diverseGroupId", ignore = true)
    public abstract HaFlow toHaFlow(HaFlowRequest request);

    @Mapping(target = "timeUpdate", source = "haFlow.timeModify")
    @Mapping(target = "sharedEndpoint", source = "haFlow")
    @Mapping(target = "subFlows", source = "haFlow.haSubFlows")
    public abstract HaFlowDto toHaFlowDto(
            HaFlow haFlow, Set<String> diverseWithFlows, Set<String> diverseWithYFlows, Set<String> diverseWithHaFlows);

    /**
     * Map {@link HaFlow} to {@link HaFlowDto} with completing diverseFlows, diverseYFlows and diverseHaFlows.
     */
    public HaFlowDto toHaFlowDto(HaFlow haFlow, FlowRepository flowRepository, HaFlowRepository haFlowRepository) {
        Collection<Flow> diverseFlows = getDiverseFlows(haFlow.getDiverseGroupId(), flowRepository);
        Set<String> diverseFlowsIds = YFlowMapper.getDiverseFlowIds(diverseFlows);
        Set<String> diverseYFlowsIds = YFlowMapper.getDiverseYFlowIds(diverseFlows);
        Set<String> diverseHaFlowsIds = getDiverseWithHaFlow(
                haFlow.getHaFlowId(), haFlow.getDiverseGroupId(), haFlowRepository);
        return toHaFlowDto(haFlow, diverseFlowsIds, diverseYFlowsIds, diverseHaFlowsIds);
    }

    @Mapping(target = "flowId", source = "haSubFlowId")
    @Mapping(target = "endpoint", source = "haSubFlow")
    @Mapping(target = "timeUpdate", source = "timeModify")
    public abstract HaSubFlowDto toSubFlowDto(HaSubFlow haSubFlow);

    @Mapping(target = "haSubFlowId", source = "flowId")
    @Mapping(target = "endpointSwitch", source = "endpoint.switchId")
    @Mapping(target = "endpointPort", source = "endpoint.portNumber")
    @Mapping(target = "endpointVlan", source = "endpoint.outerVlanId")
    @Mapping(target = "endpointInnerVlan", source = "endpoint.innerVlanId")
    public abstract HaSubFlow toSubFlow(HaSubFlowDto haSubFlow);

    @Mapping(target = "haSubFlowId", source = "subFlowId")
    @Mapping(target = "endpointSwitch", source = "haSubFlow.endpoint.switchId")
    @Mapping(target = "endpointPort", source = "haSubFlow.endpoint.portNumber")
    @Mapping(target = "endpointVlan", source = "haSubFlow.endpoint.outerVlanId")
    @Mapping(target = "endpointInnerVlan", source = "haSubFlow.endpoint.innerVlanId")
    public abstract HaSubFlow toSubFlow(String subFlowId, HaSubFlowDto haSubFlow);

    @Mapping(target = "switchId", source = "endpointSwitchId")
    @Mapping(target = "portNumber", source = "endpointPort")
    @Mapping(target = "outerVlanId", source = "endpointVlan")
    @Mapping(target = "innerVlanId", source = "endpointInnerVlan")
    @Mapping(target = "trackLldpConnectedDevices", ignore = true)
    @Mapping(target = "trackArpConnectedDevices", ignore = true)
    public abstract FlowEndpoint toSubFlowSharedEndpointEncapsulation(HaSubFlow haSubFlow);

    /**
     * Convert {@link SwitchId} to {@link Switch} object.
     */
    public Switch newSwitch(SwitchId switchId) {
        if (switchId == null) {
            return null;
        }
        return Switch.builder().switchId(switchId).build();
    }

    public FlowEndpoint map(HaFlow haFlow) {
        return new FlowEndpoint(haFlow.getSharedSwitchId(), haFlow.getSharedPort(), haFlow.getSharedOuterVlan(),
                haFlow.getSharedInnerVlan());
    }

    /**
     * Converts {@link HaFlowRequest} to a few {@link RequestedFlow}.
     * The methods also ensures that all IDs of all subflows are different.
     * If there are several subflows with equal subFLowIds - only one will be left.
     */
    public Collection<RequestedFlow> toRequestedFlows(HaFlowRequest request) {
        return request.getSubFlows().stream()
                .map(subFlow -> RequestedFlow.builder()
                        .flowId(subFlow.getFlowId())
                        .haFlowId(request.getHaFlowId())
                        .srcSwitch(request.getSharedEndpoint().getSwitchId())
                        .srcPort(request.getSharedEndpoint().getPortNumber())
                        .srcVlan(request.getSharedEndpoint().getOuterVlanId())
                        .srcInnerVlan(request.getSharedEndpoint().getInnerVlanId())
                        .destSwitch(subFlow.getEndpoint().getSwitchId())
                        .destPort(subFlow.getEndpoint().getPortNumber())
                        .destVlan(subFlow.getEndpoint().getOuterVlanId())
                        .destInnerVlan(subFlow.getEndpoint().getInnerVlanId())
                        .detectConnectedDevices(new DetectConnectedDevices())
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

    protected Collection<Flow> getDiverseFlows(String diverseGroup, FlowRepository flowRepository) {
        if (diverseGroup == null) {
            return Collections.emptyList();
        }
        return flowRepository.findByDiverseGroupId(diverseGroup);
    }

    protected Set<String> getDiverseWithHaFlow(
            String haFlowId, String diversityTyGroup, HaFlowRepository haFlowRepository) {
        return haFlowRepository.findHaFlowsIdByDiverseGroupId(diversityTyGroup).stream()
                .filter(id -> !id.equals(haFlowId))
                .collect(Collectors.toSet());
    }
}
