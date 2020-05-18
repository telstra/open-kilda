/* Copyright 2019 Telstra Open Source
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

import org.openkilda.messaging.command.flow.FlowRequest;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public abstract class RequestedFlowMapper {

    public static final RequestedFlowMapper INSTANCE = Mappers.getMapper(RequestedFlowMapper.class);

    /**
     * Convert {@link Flow} to {@link RequestedFlow}.
     */
    public RequestedFlow toRequestedFlow(FlowRequest request) {
        RequestedFlow flow = generatedMap(request);
        RequestedFlowIncrementalMapper.INSTANCE.mapSource(flow, request.getSource());
        RequestedFlowIncrementalMapper.INSTANCE.mapDestination(flow, request.getDestination());
        return flow;
    }

    /**
     * Convert {@link Flow} to {@link RequestedFlow}.
     */
    @Mapping(source = "flowId", target = "flowId")
    @Mapping(target = "srcSwitch", expression = "java(flow.getSrcSwitch().getSwitchId())")
    @Mapping(source = "srcPort", target = "srcPort")
    @Mapping(source = "srcVlan", target = "srcVlan")
    @Mapping(target = "destSwitch", expression = "java(flow.getDestSwitch().getSwitchId())")
    @Mapping(source = "destPort", target = "destPort")
    @Mapping(source = "destVlan", target = "destVlan")
    @Mapping(source = "encapsulationType", target = "flowEncapsulationType")
    @Mapping(target = "diverseFlowId", ignore = true)
    public abstract RequestedFlow toRequestedFlow(Flow flow);

    /**
     * Convert {@link RequestedFlow} to {@link Flow}.
     */
    @Mapping(source = "flowId", target = "flowId")
    @Mapping(target = "srcSwitch",
            expression = "java(org.openkilda.model.Switch.builder().switchId(requestedFlow.getSrcSwitch()).build())")
    @Mapping(source = "srcPort", target = "srcPort")
    @Mapping(source = "srcVlan", target = "srcVlan")
    @Mapping(target = "destSwitch",
            expression = "java(org.openkilda.model.Switch.builder().switchId(requestedFlow.getDestSwitch()).build())")
    @Mapping(source = "destPort", target = "destPort")
    @Mapping(source = "destVlan", target = "destVlan")
    @Mapping(source = "flowEncapsulationType", target = "encapsulationType")
    @Mapping(target = "groupId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "timeCreate", ignore = true)
    @Mapping(target = "timeModify", ignore = true)
    @Mapping(target = "targetPathComputationStrategy", ignore = true)
    public abstract Flow toFlow(RequestedFlow requestedFlow);

    @Mapping(source = "encapsulationType", target = "flowEncapsulationType")
    @Mapping(target = "srcSwitch", ignore = true)
    @Mapping(target = "srcPort", ignore = true)
    @Mapping(target = "srcVlan", ignore = true)
    @Mapping(target = "srcInnerVlan", ignore = true)
    @Mapping(target = "destSwitch", ignore = true)
    @Mapping(target = "destPort", ignore = true)
    @Mapping(target = "destVlan", ignore = true)
    @Mapping(target = "destInnerVlan", ignore = true)
    @Mapping(target = "srcWithMultiTable", ignore = true)
    @Mapping(target = "destWithMultiTable", ignore = true)
    protected abstract RequestedFlow generatedMap(FlowRequest request);

    public abstract FlowEncapsulationType map(org.openkilda.messaging.payload.flow.FlowEncapsulationType source);

    public FlowEndpoint mapSource(RequestedFlow flow) {
        return new FlowEndpoint(flow.getSrcSwitch(), flow.getSrcPort(), flow.getSrcVlan(), flow.getSrcInnerVlan());
    }

    public FlowEndpoint mapDest(RequestedFlow flow) {
        return new FlowEndpoint(flow.getDestSwitch(), flow.getDestPort(), flow.getDestVlan(), flow.getDestInnerVlan());
    }

    /**
     * Decode string representation of {@code PathComputationStrategy}.
     */
    public PathComputationStrategy mapComputationStrategy(String raw) {
        if (raw == null) {
            return null;
        }
        return PathComputationStrategy.valueOf(raw.toUpperCase());
    }
}
