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
import org.openkilda.messaging.command.yflow.YFlowDto;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.YFlow;
import org.openkilda.model.YFlow.SharedEndpoint;
import org.openkilda.model.YSubFlow;
import org.openkilda.persistence.repositories.FlowRepository;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper
public abstract class YFlowMapper {
    public static final YFlowMapper INSTANCE = Mappers.getMapper(YFlowMapper.class);

    @Mapping(target = "timeUpdate", source = "flow.timeModify")
    public abstract YFlowDto toYFlowDto(YFlow flow, Set<String> diverseWithFlows, Set<String> diverseWithYFlows);

    /**
     * Map {@link YFlow} to {@link YFlowDto} with completing diverseFlows and diverseYFlows.
     */
    public YFlowDto toYFlowDto(YFlow yFlow, FlowRepository flowRepository) {
        Optional<Flow> mainAffinityFlow = yFlow.getSubFlows().stream()
                .map(YSubFlow::getFlow)
                .filter(f -> f.getFlowId().equals(f.getAffinityGroupId()))
                .findFirst();
        Set<String> diverseFlows = new HashSet<>();
        Set<String> diverseYFlows = new HashSet<>();
        if (mainAffinityFlow.isPresent()) {
            Collection<Flow> diverseWithFlow = getDiverseWithFlow(mainAffinityFlow.get(), flowRepository);
            diverseFlows = diverseWithFlow.stream()
                    .filter(f -> f.getYFlowId() == null)
                    .map(Flow::getFlowId)
                    .collect(Collectors.toSet());
            diverseYFlows = diverseWithFlow.stream()
                    .map(Flow::getYFlowId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        }

        return YFlowMapper.INSTANCE.toYFlowDto(yFlow, diverseFlows, diverseYFlows);
    }

    @Mapping(target = "outerVlanId", ignore = true)
    @Mapping(target = "innerVlanId", ignore = true)
    @Mapping(target = "trackLldpConnectedDevices", ignore = true)
    @Mapping(target = "trackArpConnectedDevices", ignore = true)
    public abstract FlowEndpoint toEndpoint(SharedEndpoint flow);

    /**
     * Map {@link YSubFlow} to {@link SubFlowDto}.
     */
    public SubFlowDto toSubFlowDto(YSubFlow subFlow) {
        SubFlowSharedEndpointEncapsulation sharedEndpoint = SubFlowSharedEndpointEncapsulation.builder()
                .vlanId(subFlow.getSharedEndpointVlan())
                .innerVlanId(subFlow.getSharedEndpointInnerVlan())
                .build();
        FlowEndpoint endpoint = FlowEndpoint.builder()
                .switchId(subFlow.getEndpointSwitchId())
                .portNumber(subFlow.getEndpointPort())
                .outerVlanId(subFlow.getEndpointVlan())
                .innerVlanId(subFlow.getEndpointInnerVlan())
                .build();

        Optional<Flow> flow = Optional.ofNullable(subFlow.getFlow());
        return SubFlowDto.builder()
                .flowId(subFlow.getSubFlowId())
                .endpoint(endpoint)
                .sharedEndpoint(sharedEndpoint)
                .status(flow.map(Flow::getStatus).orElse(null))
                .description(flow.map(Flow::getDescription).orElse(null))
                .timeCreate(flow.map(Flow::getTimeCreate).orElse(null))
                .timeUpdate(flow.map(Flow::getTimeModify).orElse(null))
                .build();
    }

    protected Collection<Flow> getDiverseWithFlow(Flow flow, FlowRepository flowRepository) {
        return flow.getDiverseGroupId() == null ? Collections.emptyList() :
                flowRepository.findByDiverseGroupId(flow.getDiverseGroupId()).stream()
                        .filter(diverseFlow -> !flow.getFlowId().equals(diverseFlow.getFlowId())
                                || (flow.getYFlowId() != null && !flow.getYFlowId().equals(diverseFlow.getYFlowId())))
                        .collect(Collectors.toSet());
    }
}
