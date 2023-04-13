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

package org.openkilda.northbound.converter;

import org.openkilda.messaging.command.haflow.HaFlowDto;
import org.openkilda.messaging.command.haflow.HaFlowPartialUpdateRequest;
import org.openkilda.messaging.command.haflow.HaFlowPathsResponse;
import org.openkilda.messaging.command.haflow.HaFlowRequest;
import org.openkilda.messaging.command.haflow.HaSubFlowDto;
import org.openkilda.messaging.command.haflow.HaSubFlowPartialUpdateDto;
import org.openkilda.messaging.command.yflow.FlowPartialUpdateEndpoint;
import org.openkilda.messaging.model.FlowPathDto;
import org.openkilda.messaging.model.FlowPathDto.FlowProtectedPathDto;
import org.openkilda.messaging.payload.flow.DiverseGroupPayload;
import org.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.openkilda.messaging.payload.flow.GroupFlowPathPayload;
import org.openkilda.messaging.payload.flow.OverlappingSegmentsStats;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.northbound.dto.v2.flows.BaseFlowEndpointV2;
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2;
import org.openkilda.northbound.dto.v2.haflows.HaFlow;
import org.openkilda.northbound.dto.v2.haflows.HaFlowCreatePayload;
import org.openkilda.northbound.dto.v2.haflows.HaFlowPatchEndpoint;
import org.openkilda.northbound.dto.v2.haflows.HaFlowPatchPayload;
import org.openkilda.northbound.dto.v2.haflows.HaFlowPath;
import org.openkilda.northbound.dto.v2.haflows.HaFlowPath.HaFlowProtectedPath;
import org.openkilda.northbound.dto.v2.haflows.HaFlowPaths;
import org.openkilda.northbound.dto.v2.haflows.HaFlowSharedEndpoint;
import org.openkilda.northbound.dto.v2.haflows.HaFlowUpdatePayload;
import org.openkilda.northbound.dto.v2.haflows.HaSubFlowCreatePayload;
import org.openkilda.northbound.dto.v2.haflows.HaSubFlowPatchPayload;
import org.openkilda.northbound.dto.v2.haflows.HaSubFlowPath;
import org.openkilda.northbound.dto.v2.haflows.HaSubFlowUpdatePayload;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring",
        uses = {FlowEncapsulationTypeMapper.class, FlowStatusMapper.class, PathComputationStrategyMapper.class,
                TimeMapper.class, PingMapper.class},
        imports = {FlowEndpointPayload.class, FlowEndpointV2.class})
public abstract class HaFlowMapper {
    @Autowired
    PathMapper pathMapper;

    @Mapping(target = "maxLatency", qualifiedByName = "timeNanosToMillis")
    @Mapping(target = "maxLatencyTier2", qualifiedByName = "timeNanosToMillis")
    public abstract HaFlow toHaFlow(HaFlowDto flow);

    @Mapping(target = "vlanId", source = "outerVlanId")
    public abstract HaFlowSharedEndpoint toHaFlowSharedEndpoint(FlowEndpoint endpoint);

    @Mapping(target = "vlanId", source = "outerVlanId")
    public abstract BaseFlowEndpointV2 toBaseEndpoint(FlowEndpoint endpoint);

    @Mapping(target = "type", constant = "CREATE")
    @Mapping(target = "maxLatency", qualifiedByName = "timeMillisToNanos")
    @Mapping(target = "maxLatencyTier2", qualifiedByName = "timeMillisToNanos")
    public abstract HaFlowRequest toHaFlowCreateRequest(HaFlowCreatePayload source);

    @Mapping(target = "type", constant = "UPDATE")
    @Mapping(target = "maxLatency", qualifiedByName = "timeMillisToNanos")
    @Mapping(target = "maxLatencyTier2", qualifiedByName = "timeMillisToNanos")
    public abstract HaFlowRequest toHaFlowUpdateRequest(String haFlowId, HaFlowUpdatePayload source);

    @Mapping(target = "maxLatency", qualifiedByName = "timeMillisToNanos")
    @Mapping(target = "maxLatencyTier2", qualifiedByName = "timeMillisToNanos")
    public abstract HaFlowPartialUpdateRequest toHaFlowPatchRequest(String haFlowId, HaFlowPatchPayload source);

    @Mapping(target = "status", ignore = true)
    @Mapping(target = "flowId", ignore = true)
    @Mapping(target = "timeCreate", ignore = true)
    @Mapping(target = "timeUpdate", ignore = true)
    public abstract HaSubFlowDto toSubFlowDto(HaSubFlowCreatePayload source);

    @Mapping(target = "status", ignore = true)
    @Mapping(target = "timeCreate", ignore = true)
    @Mapping(target = "timeUpdate", ignore = true)
    public abstract HaSubFlowDto toSubFlowDto(HaSubFlowUpdatePayload source);

    @Mapping(target = "status", ignore = true)
    @Mapping(target = "timeCreate", ignore = true)
    @Mapping(target = "timeUpdate", ignore = true)
    public abstract HaSubFlowPartialUpdateDto toSubFlowPatchDto(HaSubFlowPatchPayload source);

    @Mapping(target = "outerVlanId", source = "vlanId")
    @Mapping(target = "trackLldpConnectedDevices", ignore = true)
    @Mapping(target = "trackArpConnectedDevices", ignore = true)
    public abstract FlowEndpoint toFlowEndpoint(BaseFlowEndpointV2 endpoint);

    @Mapping(target = "outerVlanId", source = "vlanId")
    @Mapping(target = "trackLldpConnectedDevices", ignore = true)
    @Mapping(target = "trackArpConnectedDevices", ignore = true)
    public abstract FlowEndpoint toFlowEndpoint(HaFlowSharedEndpoint endpoint);

    public abstract FlowPartialUpdateEndpoint toFlowEndpoint(HaFlowPatchEndpoint endpoint);

    @Mapping(target = "sharedPath", source = "sharedPath", resultType = HaFlowPath.class)
    @Mapping(target = "subFlowPaths",
            expression = "java(toSubFlowPaths(source.getSubFlowPaths(), source.getDiverseWithFlows()))")
    public abstract HaFlowPaths toHaFlowPaths(HaFlowPathsResponse source);

    protected List<HaSubFlowPath> toSubFlowPaths(List<FlowPathDto> subFlowPaths,
                                                 Map<String, List<FlowPathDto>> diverseWithFlows) {
        return subFlowPaths.stream()
                .map(f -> toSubFlowPath(f, diverseWithFlows.get(f.getId())))
                .collect(Collectors.toList());
    }

    @Mapping(target = "forward", source = "forwardPath")
    @Mapping(target = "reverse", source = "reversePath")
    @Mapping(target = "protectedPath", expression = "java(toHaFlowProtectedPath(source.getProtectedPath(), null))")
    @Mapping(target = "diverseGroup", expression = "java(toDiverseGroupPayload(source, null))")
    public abstract HaFlowPath toHaFlowPath(FlowPathDto source);

    @Mapping(target = "forward", source = "source.forwardPath")
    @Mapping(target = "reverse", source = "source.reversePath")
    @Mapping(target = "diverseGroup", expression = "java(toDiverseGroupPayload(source, diverseWithFlows))")
    public abstract HaFlowProtectedPath toHaFlowProtectedPath(FlowProtectedPathDto source,
                                                              List<FlowPathDto> diverseWithFlows);

    @Mapping(target = "flowId", source = "source.id")
    @Mapping(target = "reverse", source = "source.reversePath")
    @Mapping(target = "forward", source = "source.forwardPath")
    @Mapping(target = "protectedPath",
            expression = "java(toHaFlowProtectedPath(source.getProtectedPath(), diverseWithFlows))")
    @Mapping(target = "diverseGroup", expression = "java(toDiverseGroupPayload(source, diverseWithFlows))")
    public abstract HaSubFlowPath toSubFlowPath(FlowPathDto source, List<FlowPathDto> diverseWithFlows);

    protected DiverseGroupPayload toDiverseGroupPayload(FlowPathDto source, List<FlowPathDto> diverseWithFlows) {
        OverlappingSegmentsStats segmentsStats = source.getSegmentsStats();
        if (segmentsStats != null) {
            return DiverseGroupPayload.builder()
                    .overlappingSegments(segmentsStats)
                    .otherFlows(diverseWithFlows != null
                            ? toGroupPaths(diverseWithFlows, FlowPathDto::isPrimaryPathCorrespondStat) : null)
                    .build();
        }
        return null;
    }

    protected DiverseGroupPayload toDiverseGroupPayload(FlowProtectedPathDto source,
                                                        List<FlowPathDto> diverseWithFlows) {
        if (source != null) {
            OverlappingSegmentsStats segmentsStats = source.getSegmentsStats();
            if (segmentsStats != null) {
                return DiverseGroupPayload.builder()
                        .overlappingSegments(segmentsStats)
                        .otherFlows(diverseWithFlows != null
                                ? toGroupPaths(diverseWithFlows, e -> !e.isPrimaryPathCorrespondStat()) : null)
                        .build();
            }
        }
        return null;
    }

    private List<GroupFlowPathPayload> toGroupPaths(List<FlowPathDto> paths, Predicate<FlowPathDto> predicate) {
        return paths.stream()
                .filter(predicate)
                .map(pathMapper::mapGroupFlowPathPayload)
                .collect(Collectors.toList());
    }
}
