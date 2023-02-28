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
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlow.HaSharedEndpoint;
import org.openkilda.model.HaSubFlow;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public abstract class HaFlowMapper {
    public static final HaFlowMapper INSTANCE = Mappers.getMapper(HaFlowMapper.class);

    @Mapping(target = "status", ignore = true)
    public abstract HaFlow toHaFlow(HaFlowRequest request);

    @Mapping(target = "timeUpdate", source = "flow.timeModify")
    @Mapping(target = "diverseWithFlows", ignore = true)
    @Mapping(target = "diverseWithYFlows", ignore = true)
    @Mapping(target = "diverseWithHaFlows", ignore = true)
    public abstract HaFlowDto toHaFlowDto(HaFlow flow);

    @Mapping(target = "trackLldpConnectedDevices", ignore = true)
    @Mapping(target = "trackArpConnectedDevices", ignore = true)
    public abstract FlowEndpoint toEndpoint(HaSharedEndpoint flow);

    public abstract HaSharedEndpoint toEndpoint(FlowEndpoint flow);

    @Mapping(target = "flowId", source = "haSubFlowId")

    @Mapping(target = "endpoint", source = "haSubFlow")
    @Mapping(target = "timeUpdate", source = "timeModify")
    public abstract HaSubFlowDto toSubFlowDto(HaSubFlow haSubFlow);

    @Mapping(target = "haSubFlowId", source = "flowId")
    @Mapping(target = "endpointSwitchId", source = "endpoint.switchId")
    @Mapping(target = "endpointPort", source = "endpoint.portNumber")
    @Mapping(target = "endpointVlan", source = "endpoint.outerVlanId")
    @Mapping(target = "endpointInnerVlan", source = "endpoint.innerVlanId")
    public abstract HaSubFlow toSubFlow(HaSubFlowDto haSubFlow);

    @Mapping(target = "haSubFlowId", source = "subFlowId")
    @Mapping(target = "endpointSwitchId", source = "haSubFlow.endpoint.switchId")
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
}
