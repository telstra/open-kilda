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
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

@Mapper
public abstract class RequestedFlowMapper {

    public static final RequestedFlowMapper INSTANCE = Mappers.getMapper(RequestedFlowMapper.class);

    /**
     * Convert {@link Flow} to {@link RequestedFlow}.
     */
    public RequestedFlow unpackRequest(FlowRequest request) {
        RequestedFlow flow = toRequestedFlow(request);
        flow.setFlowEncapsulationType(map(request.getEncapsulationType()));
        flow.setPathComputationStrategy(mapComputationStrategy(request.getPathComputationStrategy().toUpperCase()));

        mapRequestedFlowSource(request.getSource(), flow);
        mapRequestedFlowDestination(request.getDestination(), flow);
        return flow;
    }

    protected abstract RequestedFlow toRequestedFlow(FlowRequest request);

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
    public abstract Flow toFlow(RequestedFlow requestedFlow);

    @Mapping(source = "switchId", target = "srcSwitch")
    @Mapping(source = "portNumber", target = "srcPort")
    @Mapping(source = "outerVlanId", target = "srcVlan")
    @Mapping(source = "innerVlanId", target = "srcInnerVlan")
    public abstract void mapRequestedFlowSource(FlowEndpoint endpoint, @MappingTarget RequestedFlow target);

    @Mapping(source = "switchId", target = "destSwitch")
    @Mapping(source = "portNumber", target = "destPort")
    @Mapping(source = "outerVlanId", target = "destVlan")
    @Mapping(source = "innerVlanId", target = "destInnerVlan")
    public abstract void mapRequestedFlowDestination(FlowEndpoint endpoint, @MappingTarget RequestedFlow target);

    public abstract FlowEncapsulationType map(org.openkilda.messaging.payload.flow.FlowEncapsulationType source);

    public abstract PathComputationStrategy mapComputationStrategy(String raw);
}
