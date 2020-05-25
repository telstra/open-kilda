/* Copyright 2020 Telstra Open Source
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

import org.openkilda.model.FlowEndpoint;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class RequestedFlowIncrementalMapper {
    public static final RequestedFlowIncrementalMapper INSTANCE = Mappers.getMapper(
            RequestedFlowIncrementalMapper.class);

    @Mapping(source = "switchId", target = "srcSwitch")
    @Mapping(source = "portNumber", target = "srcPort")
    @Mapping(source = "outerVlanId", target = "srcVlan")
    @Mapping(source = "innerVlanId", target = "srcInnerVlan")
    public abstract void mapSource(@MappingTarget RequestedFlow target, FlowEndpoint endpoint);

    @Mapping(source = "switchId", target = "destSwitch")
    @Mapping(source = "portNumber", target = "destPort")
    @Mapping(source = "outerVlanId", target = "destVlan")
    @Mapping(source = "innerVlanId", target = "destInnerVlan")
    protected abstract void mapDestination(@MappingTarget RequestedFlow target, FlowEndpoint endpoint);
}
