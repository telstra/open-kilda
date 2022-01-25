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

import org.openkilda.adapter.FlowDestAdapter;
import org.openkilda.adapter.FlowSourceAdapter;
import org.openkilda.messaging.command.flow.FlowRequest;
import org.openkilda.model.Flow;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;

import lombok.NonNull;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

// TODO(surabujin): merge with org.openkilda.wfm.topology.flowhs.mapper.RequestedFlowMapper
@Mapper
public abstract class RequestedFlowMapper {

    public static final RequestedFlowMapper INSTANCE = Mappers.getMapper(RequestedFlowMapper.class);

    /**
     * Convert {@link Flow} to {@link FlowRequest}.
     */
    public FlowRequest toFlowRequest(Flow flow) {
        FlowRequest request = generatedMap(flow);
        request.setSource(new FlowSourceAdapter(flow).getEndpoint());
        request.setDestination(new FlowDestAdapter(flow).getEndpoint());
        return request;
    }

    /**
     * Convert {@link FlowRequest} to {@link Flow}.
     */
    @Mapping(target = "srcSwitch", source = "source.switchId")
    @Mapping(target = "srcPort", source = "source.portNumber")
    @Mapping(target = "srcVlan", source = "source.outerVlanId")
    @Mapping(target = "srcInnerVlan", source = "source.innerVlanId")
    @Mapping(target = "destSwitch", source = "destination.switchId")
    @Mapping(target = "destPort", source = "destination.portNumber")
    @Mapping(target = "destVlan", source = "destination.outerVlanId")
    @Mapping(target = "destInnerVlan", source = "destination.innerVlanId")
    @Mapping(target = "detectConnectedDevices", source = "detectConnectedDevices")
    @Mapping(target = "diverseGroupId", ignore = true)
    @Mapping(target = "affinityGroupId", ignore = true)
    @Mapping(target = "statusInfo", ignore = true)
    @Mapping(target = "targetPathComputationStrategy", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "yFlowId", ignore = true)
    @Mapping(target = "yFlow", ignore = true)
    public abstract Flow toFlow(@NonNull FlowRequest request);

    @Mapping(target = "flowId", source = "flowId")
    @Mapping(target = "encapsulationType", source = "encapsulationType")
    @Mapping(target = "bandwidth", source = "bandwidth")
    @Mapping(target = "ignoreBandwidth", source = "ignoreBandwidth")
    @Mapping(target = "periodicPings", source = "periodicPings")
    @Mapping(target = "allocateProtectedPath", source = "allocateProtectedPath")
    @Mapping(target = "description", source = "description")
    @Mapping(target = "maxLatency", source = "maxLatency")
    @Mapping(target = "priority", source = "priority")
    @Mapping(target = "pinned", source = "pinned")
    @Mapping(target = "detectConnectedDevices", source = "detectConnectedDevices")
    @Mapping(target = "loopSwitchId", source = "loopSwitchId")
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "destination", ignore = true)
    @Mapping(target = "transitEncapsulationId", ignore = true)
    @Mapping(target = "diverseFlowId", ignore = true)
    @Mapping(target = "affinityFlowId", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "bulkUpdateFlowIds", ignore = true)
    @Mapping(target = "doNotRevert", ignore = true)
    protected abstract FlowRequest generatedMap(Flow flow);

    public SwitchId map(String value) {
        return value == null ? null : new SwitchId(value);
    }

    protected String mapPathComputationStrategy(PathComputationStrategy strategy) {
        if (strategy == null) {
            return null;
        }
        return strategy.toString().toLowerCase();
    }

    protected PathComputationStrategy mapPathComputationStrategy(String raw) {
        if (raw == null) {
            return null;
        }
        return PathComputationStrategy.valueOf(raw.toUpperCase());
    }

    protected Switch newSwitch(SwitchId switchId) {
        return Switch.builder().switchId(switchId).build();
    }
}
