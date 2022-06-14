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

import org.openkilda.adapter.FlowDestAdapter;
import org.openkilda.adapter.FlowSourceAdapter;
import org.openkilda.messaging.command.flow.FlowRequest;
import org.openkilda.messaging.model.DetectConnectedDevicesDto;
import org.openkilda.messaging.model.SwapFlowDto;
import org.openkilda.model.DetectConnectedDevices;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.server42.control.messaging.flowrtt.ActivateFlowMonitoringInfoData;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;

import lombok.NonNull;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.Optional;

@Mapper(imports = {FlowEndpoint.class})
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
    @Mapping(source = "srcSwitchId", target = "srcSwitch")
    @Mapping(source = "destSwitchId", target = "destSwitch")
    @Mapping(source = "encapsulationType", target = "flowEncapsulationType")
    @Mapping(target = "diverseFlowId", ignore = true)
    @Mapping(target = "affinityFlowId", ignore = true)
    @Mapping(source = "YFlowId", target = "yFlowId")
    public abstract RequestedFlow toRequestedFlow(Flow flow);

    /**
     * Convert {@link SwapFlowDto} to {@link RequestedFlow}.
     */
    @Mapping(source = "sourceSwitch", target = "srcSwitch")
    @Mapping(source = "sourcePort", target = "srcPort")
    @Mapping(source = "sourceVlan", target = "srcVlan")
    @Mapping(source = "destinationSwitch", target = "destSwitch")
    @Mapping(source = "destinationPort", target = "destPort")
    @Mapping(source = "destinationVlan", target = "destVlan")
    @Mapping(target = "priority", ignore = true)
    @Mapping(target = "pinned", ignore = true)
    @Mapping(target = "allocateProtectedPath", ignore = true)
    @Mapping(target = "diverseFlowId", ignore = true)
    @Mapping(target = "affinityFlowId", ignore = true)
    @Mapping(target = "description", ignore = true)
    @Mapping(target = "bandwidth", ignore = true)
    @Mapping(target = "ignoreBandwidth", ignore = true)
    @Mapping(target = "strictBandwidth", ignore = true)
    @Mapping(target = "periodicPings", ignore = true)
    @Mapping(target = "maxLatency", ignore = true)
    @Mapping(target = "maxLatencyTier2", ignore = true)
    @Mapping(target = "flowEncapsulationType", ignore = true)
    @Mapping(target = "pathComputationStrategy", ignore = true)
    @Mapping(target = "detectConnectedDevices", ignore = true)
    @Mapping(target = "srcInnerVlan", ignore = true)
    @Mapping(target = "destInnerVlan", ignore = true)
    @Mapping(target = "loopSwitchId", ignore = true)
    @Mapping(target = "yFlowId", ignore = true)
    @Mapping(target = "vlanStatistics", ignore = true)
    public abstract RequestedFlow toRequestedFlow(SwapFlowDto flow);

    /**
     * Convert {@link RequestedFlow} to {@link Flow}.
     */
    @Mapping(source = "flowEncapsulationType", target = "encapsulationType")
    @Mapping(target = "diverseGroupId", ignore = true)
    @Mapping(target = "affinityGroupId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "statusInfo", ignore = true)
    @Mapping(target = "targetPathComputationStrategy", ignore = true)
    @Mapping(target = "yFlowId", ignore = true)
    @Mapping(target = "yFlow", ignore = true)
    @Mapping(target = "vlanStatistics", source = "vlanStatistics")
    public abstract Flow toFlow(RequestedFlow requestedFlow);

    /**
     * Convert {@link Flow} to {@link FlowRequest}.
     */
    public FlowRequest toFlowRequest(@NonNull Flow flow) {
        FlowRequest request = generatedMap(flow);
        request.setSource(new FlowSourceAdapter(flow).getEndpoint());
        request.setDestination(new FlowDestAdapter(flow).getEndpoint());
        if (flow.getPathComputationStrategy() != null) {
            request.setPathComputationStrategy(flow.getPathComputationStrategy().toString().toLowerCase());
        }
        return request;
    }

    @Mapping(source = "encapsulationType", target = "flowEncapsulationType")
    @Mapping(target = "srcSwitch", ignore = true)
    @Mapping(target = "srcPort", ignore = true)
    @Mapping(target = "srcVlan", ignore = true)
    @Mapping(target = "srcInnerVlan", ignore = true)
    @Mapping(target = "destSwitch", ignore = true)
    @Mapping(target = "destPort", ignore = true)
    @Mapping(target = "destVlan", ignore = true)
    @Mapping(target = "destInnerVlan", ignore = true)
    @Mapping(target = "yFlowId", ignore = true)
    @Mapping(target = "vlanStatistics", source = "vlanStatistics")
    protected abstract RequestedFlow generatedMap(FlowRequest request);

    /**
     * Convert {@link Flow} to {@link FlowRequest}.
     */
    @Mapping(target = "pathComputationStrategy", ignore = true)
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "destination", ignore = true)
    @Mapping(target = "transitEncapsulationId", ignore = true)
    @Mapping(target = "diverseFlowId", ignore = true)
    @Mapping(target = "affinityFlowId", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "bulkUpdateFlowIds", ignore = true)
    @Mapping(target = "doNotRevert", ignore = true)
    @Mapping(target = "vlanStatistics", source = "vlanStatistics")
    protected abstract FlowRequest generatedMap(Flow flow);

    public abstract FlowEncapsulationType map(org.openkilda.messaging.payload.flow.FlowEncapsulationType source);

    /**
     * Convert {@link RequestedFlow} to source {@link FlowEndpoint}.
     */
    public FlowEndpoint mapSource(RequestedFlow flow) {
        org.openkilda.wfm.topology.flowhs.model.DetectConnectedDevices detectConnectedDevices
                = Optional.ofNullable(flow.getDetectConnectedDevices())
                .orElse(new org.openkilda.wfm.topology.flowhs.model.DetectConnectedDevices());
        return new FlowEndpoint(flow.getSrcSwitch(), flow.getSrcPort(), flow.getSrcVlan(), flow.getSrcInnerVlan(),
                detectConnectedDevices.isSrcLldp() || detectConnectedDevices.isSrcSwitchLldp(),
                detectConnectedDevices.isSrcArp() || detectConnectedDevices.isSrcSwitchArp());
    }

    /**
     * Convert {@link RequestedFlow} to destination {@link FlowEndpoint}.
     */
    public FlowEndpoint mapDest(RequestedFlow flow) {
        org.openkilda.wfm.topology.flowhs.model.DetectConnectedDevices detectConnectedDevices
                = Optional.ofNullable(flow.getDetectConnectedDevices())
                .orElse(new org.openkilda.wfm.topology.flowhs.model.DetectConnectedDevices());
        return new FlowEndpoint(flow.getDestSwitch(), flow.getDestPort(), flow.getDestVlan(), flow.getDestInnerVlan(),
                detectConnectedDevices.isDstLldp() || detectConnectedDevices.isDstSwitchLldp(),
                detectConnectedDevices.isDstArp() || detectConnectedDevices.isDstSwitchArp());
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

    /**
     * Convert {@link DetectConnectedDevices} to {@link DetectConnectedDevicesDto}.
     */
    public abstract DetectConnectedDevicesDto toDetectConnectedDevices(DetectConnectedDevices detectConnectedDevices);

    /**
     * Convert {@link RequestedFlow} to {@link ActivateFlowMonitoringInfoData}.
     */
    @Mapping(target = "source.datapath", source = "srcSwitch")
    @Mapping(target = "source.portNumber", source = "srcPort")
    @Mapping(target = "source.vlanId", source = "srcVlan")
    @Mapping(target = "source.innerVlanId", source = "srcInnerVlan")
    @Mapping(target = "destination.datapath", source = "destSwitch")
    @Mapping(target = "destination.portNumber", source = "destPort")
    @Mapping(target = "destination.vlanId", source = "destVlan")
    @Mapping(target = "destination.innerVlanId", source = "destInnerVlan")
    public abstract ActivateFlowMonitoringInfoData toActivateFlowMonitoringInfoData(RequestedFlow flow);

    protected Switch newSwitch(SwitchId switchId) {
        return Switch.builder().switchId(switchId).build();
    }

    public SwitchId mapSwitchId(String value) {
        return value == null ? null : new SwitchId(value);
    }
}
