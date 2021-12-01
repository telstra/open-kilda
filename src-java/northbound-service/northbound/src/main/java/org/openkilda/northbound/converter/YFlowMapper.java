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

package org.openkilda.northbound.converter;

import org.openkilda.messaging.command.yflow.SubFlowDto;
import org.openkilda.messaging.command.yflow.SubFlowPathDto;
import org.openkilda.messaging.command.yflow.SubFlowSharedEndpointEncapsulation;
import org.openkilda.messaging.command.yflow.SubFlowsResponse;
import org.openkilda.messaging.command.yflow.YFlowDto;
import org.openkilda.messaging.command.yflow.YFlowPatchRequest;
import org.openkilda.messaging.command.yflow.YFlowPathsResponse;
import org.openkilda.messaging.command.yflow.YFlowRequest;
import org.openkilda.messaging.command.yflow.YFlowRerouteResponse;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2;
import org.openkilda.northbound.dto.v2.flows.FlowPathV2;
import org.openkilda.northbound.dto.v2.yflows.SubFlow;
import org.openkilda.northbound.dto.v2.yflows.SubFlowPath;
import org.openkilda.northbound.dto.v2.yflows.SubFlowUpdatePayload;
import org.openkilda.northbound.dto.v2.yflows.SubFlowsDump;
import org.openkilda.northbound.dto.v2.yflows.YFlow;
import org.openkilda.northbound.dto.v2.yflows.YFlowCreatePayload;
import org.openkilda.northbound.dto.v2.yflows.YFlowPatchPayload;
import org.openkilda.northbound.dto.v2.yflows.YFlowPath;
import org.openkilda.northbound.dto.v2.yflows.YFlowPaths;
import org.openkilda.northbound.dto.v2.yflows.YFlowRerouteResult;
import org.openkilda.northbound.dto.v2.yflows.YFlowSharedEndpoint;
import org.openkilda.northbound.dto.v2.yflows.YFlowSharedEndpointEncapsulation;
import org.openkilda.northbound.dto.v2.yflows.YFlowUpdatePayload;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.stream.Collectors;

@Mapper(componentModel = "spring",
        uses = {FlowEncapsulationTypeMapper.class, FlowStatusMapper.class, PathComputationStrategyMapper.class,
                InstantMapper.class},
        imports = {FlowEndpointPayload.class, FlowEndpointV2.class})
public abstract class YFlowMapper {

    @Mapping(target = "yFlowId", source = "YFlowId")
    @Mapping(target = "yPoint", source = "YPoint")
    @Mapping(target = "protectedPathYPoint", source = "protectedPathYPoint")
    public abstract YFlow toYFlow(YFlowDto flow);

    public abstract SubFlow toYFlow(SubFlowDto flow);

    @Mapping(target = "vlanId", source = "outerVlanId")
    @Mapping(target = "detectConnectedDevices",
            expression = "java(new DetectConnectedDevicesV2(endpoint.isTrackLldpConnectedDevices(), "
                    + "endpoint.isTrackArpConnectedDevices()))")
    public abstract FlowEndpointV2 toFlowEndpointV2(FlowEndpoint endpoint);

    public abstract YFlowSharedEndpoint toYFlowSharedEndpoint(FlowEndpoint endpoint);

    @Mapping(target = "vlanId", source = "outerVlanId")
    public abstract YFlowSharedEndpointEncapsulation toYFlowSharedEndpointEncapsulation(FlowEndpoint endpoint);

    public abstract YFlowPaths toYFlowPaths(YFlowPathsResponse source);

    @Mapping(target = "nodes", source = "path.path")
    public abstract SubFlowPath toSubFlowPath(SubFlowPathDto flow);

    /**
     * Convert {@link PathInfoData} to {@link YFlowPath}.
     */
    public YFlowPath toYFlowPath(PathInfoData path) {
        if (path != null && path.getPath() != null && !path.getPath().isEmpty()) {
            return YFlowPath.builder()
                    .nodes(path.getPath().stream().map(this::toPathNodeV2).collect(Collectors.toList()))
                    .build();
        }
        return null;
    }

    @Mapping(target = "segmentLatency", source = "segLatency")
    public abstract FlowPathV2.PathNodeV2 toPathNodeV2(PathNode pathNode);

    @Mapping(target = "type", constant = "CREATE")
    @Mapping(target = "yFlowId", ignore = true)
    public abstract YFlowRequest toYFlowCreateRequest(YFlowCreatePayload source);

    @Mapping(target = "type", constant = "UPDATE")
    public abstract YFlowRequest toYFlowUpdateRequest(String yFlowId, YFlowUpdatePayload source);

    public abstract YFlowPatchRequest toYFlowPatchRequest(String yFlowId, YFlowPatchPayload source);

    @Mapping(target = "flowId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "timeCreate", ignore = true)
    @Mapping(target = "timeUpdate", ignore = true)
    public abstract SubFlowDto toSubFlowDto(SubFlowUpdatePayload source);

    @Mapping(target = "outerVlanId", source = "vlanId")
    @Mapping(target = "trackLldpConnectedDevices", source = "detectConnectedDevices.lldp")
    @Mapping(target = "trackArpConnectedDevices", source = "detectConnectedDevices.arp")
    public abstract FlowEndpoint toFlowEndpoint(FlowEndpointV2 endpoint);

    @Mapping(target = "outerVlanId", ignore = true)
    @Mapping(target = "innerVlanId", ignore = true)
    @Mapping(target = "trackLldpConnectedDevices", ignore = true)
    @Mapping(target = "trackArpConnectedDevices", ignore = true)
    public abstract FlowEndpoint toFlowEndpoint(YFlowSharedEndpoint endpoint);

    public abstract SubFlowSharedEndpointEncapsulation toFlowEndpoint(YFlowSharedEndpointEncapsulation endpoint);

    public abstract SubFlowsDump toSubFlowsDump(SubFlowsResponse source);

    public abstract YFlowRerouteResult toRerouteResult(YFlowRerouteResponse source);
}
