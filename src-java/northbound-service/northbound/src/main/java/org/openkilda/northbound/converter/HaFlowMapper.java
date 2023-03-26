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
import org.openkilda.messaging.command.haflow.HaFlowRequest;
import org.openkilda.messaging.command.haflow.HaSubFlowDto;
import org.openkilda.messaging.command.haflow.HaSubFlowPartialUpdateDto;
import org.openkilda.messaging.command.yflow.FlowPartialUpdateEndpoint;
import org.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.northbound.dto.v2.flows.BaseFlowEndpointV2;
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2;
import org.openkilda.northbound.dto.v2.haflows.HaFlow;
import org.openkilda.northbound.dto.v2.haflows.HaFlowCreatePayload;
import org.openkilda.northbound.dto.v2.haflows.HaFlowPatchEndpoint;
import org.openkilda.northbound.dto.v2.haflows.HaFlowPatchPayload;
import org.openkilda.northbound.dto.v2.haflows.HaFlowSharedEndpoint;
import org.openkilda.northbound.dto.v2.haflows.HaFlowUpdatePayload;
import org.openkilda.northbound.dto.v2.haflows.HaSubFlowCreatePayload;
import org.openkilda.northbound.dto.v2.haflows.HaSubFlowPatchPayload;
import org.openkilda.northbound.dto.v2.haflows.HaSubFlowUpdatePayload;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

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
    public abstract BaseFlowEndpointV2 toFlowEndpointV2(FlowEndpoint endpoint);

    @Mapping(target = "vlanId", source = "outerVlanId")
    public abstract HaFlowSharedEndpoint toYFlowSharedEndpoint(FlowEndpoint endpoint);

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
}
