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

package org.openkilda.wfm.share.mappers;

import org.openkilda.messaging.command.flow.FlowRequest;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.Optional;

@Mapper(imports = {FlowEndpoint.class, Switch.class, Optional.class, PathComputationStrategy.class})
public abstract class RequestedFlowMapper {

    public static final RequestedFlowMapper INSTANCE = Mappers.getMapper(RequestedFlowMapper.class);

    /**
     * Convert {@link Flow} to {@link FlowRequest}.
     */
    @Mapping(target = "flowId", source = "flowId")
    @Mapping(target = "source", expression = "java(new FlowEndpoint(flow.getSrcSwitchId(), "
            + "flow.getSrcPort(), flow.getSrcVlan(), flow.getSrcInnerVlan()))")
    @Mapping(target = "destination", expression = "java(new FlowEndpoint(flow.getDestSwitchId(), "
            + "flow.getDestPort(), flow.getDestVlan(), flow.getDestInnerVlan()))")
    @Mapping(target = "encapsulationType", source = "encapsulationType")
    @Mapping(target = "pathComputationStrategy",
            expression = "java(java.util.Optional.ofNullable(flow.getPathComputationStrategy())"
                    + ".map(pcs -> pcs.toString().toLowerCase())"
                    + ".orElse(null))")
    @Mapping(target = "bandwidth", source = "bandwidth")
    @Mapping(target = "ignoreBandwidth", source = "ignoreBandwidth")
    @Mapping(target = "periodicPings", source = "periodicPings")
    @Mapping(target = "allocateProtectedPath", source = "allocateProtectedPath")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "maxLatency", source = "maxLatency")
    @Mapping(target = "priority", source = "priority")
    @Mapping(target = "pinned", source = "pinned")
    @Mapping(target = "detectConnectedDevices", source = "detectConnectedDevices")
    @Mapping(target = "transitEncapsulationId", ignore = true)
    @Mapping(target = "diverseFlowId", ignore = true)
    @Mapping(target = "affinityFlowId", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "bulkUpdateFlowIds", ignore = true)
    @Mapping(target = "doNotRevert", ignore = true)
    public abstract FlowRequest toFlowRequest(Flow flow);

    /**
     * Convert {@link FlowRequest} to {@link Flow}.
     */
    @Mapping(target = "srcSwitch", expression = "java(java.util.Optional.ofNullable(request.getSource())"
            + ".map(FlowEndpoint::getSwitchId).map(id -> Switch.builder().switchId(id).build()).orElse(null))")
    @Mapping(target = "srcPort", expression = "java(java.util.Optional.ofNullable(request.getSource())"
            + ".map(FlowEndpoint::getPortNumber).orElse(null))")
    @Mapping(target = "srcVlan", expression = "java(java.util.Optional.ofNullable(request.getSource())"
            + ".map(FlowEndpoint::getOuterVlanId).orElse(null))")
    @Mapping(target = "srcInnerVlan", expression = "java(java.util.Optional.ofNullable(request.getSource())"
            + ".map(FlowEndpoint::getInnerVlanId).orElse(null))")
    @Mapping(target = "destSwitch", expression = "java(java.util.Optional.ofNullable(request.getDestination())"
            + ".map(FlowEndpoint::getSwitchId).map(id -> Switch.builder().switchId(id).build()).orElse(null))")
    @Mapping(target = "destPort", expression = "java(java.util.Optional.ofNullable(request.getDestination())"
            + ".map(FlowEndpoint::getPortNumber).orElse(null))")
    @Mapping(target = "destVlan", expression = "java(java.util.Optional.ofNullable(request.getDestination())"
            + ".map(FlowEndpoint::getOuterVlanId).orElse(null))")
    @Mapping(target = "destInnerVlan", expression = "java(java.util.Optional.ofNullable(request.getDestination())"
            + ".map(FlowEndpoint::getInnerVlanId).orElse(null))")
    @Mapping(target = "pathComputationStrategy",
            expression = "java(Optional.ofNullable(request.getPathComputationStrategy())"
                    + ".map(String::toUpperCase).map(PathComputationStrategy::valueOf).orElse(null))")
    @Mapping(target = "detectConnectedDevices", source = "detectConnectedDevices")
    @Mapping(target = "diverseGroupId", ignore = true)
    @Mapping(target = "affinityGroupId", ignore = true)
    @Mapping(target = "statusInfo", ignore = true)
    @Mapping(target = "targetPathComputationStrategy", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "yFlowId", ignore = true)
    @Mapping(target = "yFlow", ignore = true)
    public abstract Flow toFlow(FlowRequest request);

    public SwitchId map(String value) {
        return value == null ? null : new SwitchId(value);
    }
}
