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

import org.openkilda.messaging.command.flow.FlowMirrorPointCreateRequest;
import org.openkilda.messaging.command.flow.FlowRequest;
import org.openkilda.messaging.command.flow.FlowRequest.Type;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.flow.FlowMirrorPointResponse;
import org.openkilda.messaging.info.flow.FlowPingResponse;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.messaging.info.flow.FlowValidationResponse;
import org.openkilda.messaging.info.flow.UniFlowPingResponse;
import org.openkilda.messaging.model.DetectConnectedDevicesDto;
import org.openkilda.messaging.model.FlowDto;
import org.openkilda.messaging.model.FlowPatch;
import org.openkilda.messaging.model.MirrorPointStatusDto;
import org.openkilda.messaging.model.PatchEndpoint;
import org.openkilda.messaging.model.SwapFlowDto;
import org.openkilda.messaging.nbtopology.response.FlowLoopDto;
import org.openkilda.messaging.nbtopology.response.FlowMirrorPointsDumpResponse;
import org.openkilda.messaging.nbtopology.response.FlowMirrorPointsDumpResponse.FlowMirrorPoint;
import org.openkilda.messaging.payload.flow.DetectConnectedDevicesPayload;
import org.openkilda.messaging.payload.flow.FlowCreatePayload;
import org.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowReroutePayload;
import org.openkilda.messaging.payload.flow.FlowResponsePayload;
import org.openkilda.messaging.payload.flow.FlowStatusDetails;
import org.openkilda.messaging.payload.flow.FlowUpdatePayload;
import org.openkilda.messaging.payload.history.FlowStatusTimestampsEntry;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.northbound.dto.v1.flows.FlowPatchDto;
import org.openkilda.northbound.dto.v1.flows.FlowValidationDto;
import org.openkilda.northbound.dto.v1.flows.PingOutput;
import org.openkilda.northbound.dto.v1.flows.UniFlowPingOutput;
import org.openkilda.northbound.dto.v2.flows.DetectConnectedDevicesV2;
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2;
import org.openkilda.northbound.dto.v2.flows.FlowHistoryStatus;
import org.openkilda.northbound.dto.v2.flows.FlowLoopResponse;
import org.openkilda.northbound.dto.v2.flows.FlowMirrorPointPayload;
import org.openkilda.northbound.dto.v2.flows.FlowMirrorPointResponseV2;
import org.openkilda.northbound.dto.v2.flows.FlowMirrorPointsResponseV2;
import org.openkilda.northbound.dto.v2.flows.FlowPatchEndpoint;
import org.openkilda.northbound.dto.v2.flows.FlowPatchV2;
import org.openkilda.northbound.dto.v2.flows.FlowPathV2;
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2;
import org.openkilda.northbound.dto.v2.flows.FlowRerouteResponseV2;
import org.openkilda.northbound.dto.v2.flows.FlowResponseV2;
import org.openkilda.northbound.dto.v2.flows.MirrorPointStatus;
import org.openkilda.northbound.dto.v2.flows.PathStatus;
import org.openkilda.northbound.dto.v2.flows.SwapFlowPayload;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = {
        FlowEncapsulationTypeMapper.class, FlowStatusMapper.class, PathComputationStrategyMapper.class,
        KildaTypeMapper.class, TimeMapper.class, PingMapper.class})
public abstract class FlowMapper {
    /**
     * Map {@link FlowDto} into {@link FlowPayload}.
     */
    public FlowPayload toFlowOutput(FlowDto f) {
        FlowPayload result = new FlowPayload();
        generatedMap(result, f);
        mapFlowResponseEndpoints(result, f);
        return result;
    }

    /**
     * Map {@link FlowDto} into {@link FlowResponsePayload}.
     */
    public FlowResponsePayload toFlowResponseOutput(FlowDto f) {
        FlowResponsePayload result = new FlowResponsePayload();
        generatedMap(result, f);
        generatedFlowResponsePayloadMap(result, f);
        mapFlowResponseEndpoints(result, f);
        return result;
    }

    /**
     * Map {@link FlowDto} into {@link FlowResponseV2}.
     */
    public FlowResponseV2 toFlowResponseV2(FlowDto flowDto) {
        DetectConnectedDevicesDto connectedDevices = flowDto.getDetectConnectedDevices();

        FlowEndpointV2 source = generatedFlowSourceEndpointMap(flowDto);
        source.setDetectConnectedDevices(generatedFlowSourceEndpointConnectedDevicesMap(connectedDevices));

        FlowEndpointV2 destination = generatedFlowDestinationEndpointMap(flowDto);
        destination.setDetectConnectedDevices(generatedFlowDestinationEndpointConnectedDevicesMap(connectedDevices));

        FlowResponseV2 result = generatedMap(flowDto, source, destination);
        result.setSource(source);
        result.setDestination(destination);

        return result;
    }

    @Mapping(target = "flowId", ignore = true)
    @Mapping(target = "maxLatency", qualifiedByName = "timeMillisToNanos")
    @Mapping(target = "priority", source = "priority")
    @Mapping(target = "periodicPings", source = "periodicPings")
    @Mapping(target = "targetPathComputationStrategy", source = "targetPathComputationStrategy")
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "destination", ignore = true)
    @Mapping(target = "bandwidth", ignore = true)
    @Mapping(target = "allocateProtectedPath", ignore = true)
    @Mapping(target = "diverseFlowId", ignore = true)
    @Mapping(target = "affinityFlowId", ignore = true)
    @Mapping(target = "pathComputationStrategy", ignore = true)
    @Mapping(target = "pinned", ignore = true)
    @Mapping(target = "ignoreBandwidth", ignore = true)
    @Mapping(target = "strictBandwidth", ignore = true)
    @Mapping(target = "description", ignore = true)
    @Mapping(target = "encapsulationType", ignore = true)
    @Mapping(target = "maxLatencyTier2", ignore = true)
    @Mapping(target = "vlanStatistics", ignore = true)
    public abstract FlowPatch toFlowPatch(FlowPatchDto flowPatchDto);

    @Mapping(target = "flowId", ignore = true)
    @Mapping(target = "bandwidth", source = "maximumBandwidth")
    @Mapping(target = "allocateProtectedPath", source = "allocateProtectedPath")
    @Mapping(target = "maxLatency", qualifiedByName = "timeMillisToNanos")
    @Mapping(target = "maxLatencyTier2", qualifiedByName = "timeMillisToNanos")
    @Mapping(target = "priority", source = "priority")
    @Mapping(target = "periodicPings", source = "periodicPings")
    @Mapping(target = "diverseFlowId", source = "diverseFlowId")
    @Mapping(target = "vlanStatistics", source = "statistics.vlans")
    public abstract FlowPatch toFlowPatch(FlowPatchV2 flowPatchDto);

    @Mapping(target = "trackLldpConnectedDevices", source = "detectConnectedDevices.lldp")
    @Mapping(target = "trackArpConnectedDevices", source = "detectConnectedDevices.arp")
    public abstract PatchEndpoint toPatchEndpoint(FlowPatchEndpoint flowPatchEndpoint);

    @Mapping(target = "bandwidth", source = "maximumBandwidth")
    @Mapping(target = "detectConnectedDevices", source = "request")
    @Mapping(target = "transitEncapsulationId", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "bulkUpdateFlowIds", ignore = true)
    @Mapping(target = "doNotRevert", ignore = true)
    @Mapping(target = "maxLatency", qualifiedByName = "timeMillisToNanos")
    @Mapping(target = "maxLatencyTier2", qualifiedByName = "timeMillisToNanos")
    @Mapping(target = "loopSwitchId", ignore = true)
    @Mapping(target = "vlanStatistics", source = "statistics.vlans")
    public abstract FlowRequest toFlowRequest(FlowRequestV2 request);

    @Mapping(target = "flowId", source = "id")
    @Mapping(target = "bandwidth", source = "maximumBandwidth")
    @Mapping(target = "detectConnectedDevices", ignore = true)
    @Mapping(target = "transitEncapsulationId", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "bulkUpdateFlowIds", ignore = true)
    @Mapping(target = "doNotRevert", ignore = true)
    @Mapping(target = "diverseFlowId", ignore = true)
    @Mapping(target = "affinityFlowId", ignore = true)
    @Mapping(target = "maxLatency", qualifiedByName = "timeMillisToNanos")
    @Mapping(target = "maxLatencyTier2", ignore = true)
    @Mapping(target = "loopSwitchId", ignore = true)
    @Mapping(target = "strictBandwidth", ignore = true)
    @Mapping(target = "vlanStatistics", ignore = true)
    public abstract FlowRequest toFlowRequest(FlowPayload payload);

    @Mapping(target = "outerVlanId", source = "vlanId")
    @Mapping(target = "trackLldpConnectedDevices", source = "detectConnectedDevices.lldp")
    @Mapping(target = "trackArpConnectedDevices", source = "detectConnectedDevices.arp")
    public abstract FlowEndpoint mapFlowEndpoint(FlowEndpointV2 input);

    @Mapping(target = "switchId", source = "datapath")
    @Mapping(target = "outerVlanId", source = "vlanId")
    @Mapping(target = "trackLldpConnectedDevices", source = "detectConnectedDevices.lldp")
    @Mapping(target = "trackArpConnectedDevices", source = "detectConnectedDevices.arp")
    public abstract FlowEndpoint mapFlowEndpoint(FlowEndpointPayload input);

    public FlowRequest toFlowCreateRequest(FlowRequestV2 source) {
        return toFlowRequest(source).toBuilder().type(Type.CREATE).build();
    }

    /**
     * Map FlowCreatePayload.
     *
     * @param source {@link FlowCreatePayload} instance.
     * @return {@link FlowRequest} instance.
     */
    public FlowRequest toFlowCreateRequest(FlowCreatePayload source) {
        FlowRequest target = toFlowRequest(source).toBuilder()
                .diverseFlowId(source.getDiverseFlowId())
                .type(Type.CREATE)
                .build();
        if (source.getSource().getDetectConnectedDevices() != null) {
            DetectConnectedDevicesPayload srcDevs = source.getSource().getDetectConnectedDevices();
            target.getDetectConnectedDevices().setSrcArp(srcDevs.isArp());
            target.getDetectConnectedDevices().setSrcLldp(srcDevs.isLldp());
        }
        if (source.getDestination().getDetectConnectedDevices() != null) {
            DetectConnectedDevicesPayload dstDevs = source.getDestination().getDetectConnectedDevices();
            target.getDetectConnectedDevices().setDstArp(dstDevs.isArp());
            target.getDetectConnectedDevices().setDstLldp(dstDevs.isLldp());
        }
        return target;
    }

    /**
     * Map FlowUpdatePayload.
     *
     * @param source {@link FlowUpdatePayload} instance.
     * @return {@link FlowRequest} instance.
     */
    public FlowRequest toFlowUpdateRequest(FlowUpdatePayload source) {
        FlowRequest target = toFlowRequest(source).toBuilder()
                .diverseFlowId(source.getDiverseFlowId())
                .type(Type.UPDATE)
                .build();
        if (source.getSource().getDetectConnectedDevices() != null) {
            DetectConnectedDevicesPayload srcDevs = source.getSource().getDetectConnectedDevices();
            target.getDetectConnectedDevices().setSrcArp(srcDevs.isArp());
            target.getDetectConnectedDevices().setSrcLldp(srcDevs.isLldp());
        }
        if (source.getDestination().getDetectConnectedDevices() != null) {
            DetectConnectedDevicesPayload dstDevs = source.getDestination().getDetectConnectedDevices();
            target.getDetectConnectedDevices().setDstArp(dstDevs.isArp());
            target.getDetectConnectedDevices().setDstLldp(dstDevs.isLldp());
        }
        return target;
    }

    public abstract PingOutput toPingOutput(FlowPingResponse response);

    @Mapping(source = "flowId", target = "id")
    @Mapping(source = "path", target = "path")
    @Mapping(source = "rerouted", target = "rerouted")
    public abstract FlowReroutePayload toReroutePayload(String flowId, PathInfoData path, boolean rerouted);

    @Mapping(source = "path", target = "path")
    public abstract FlowRerouteResponseV2 toRerouteResponseV2(String flowId, PathInfoData path, boolean rerouted);

    @Mapping(source = "path", target = "nodes")
    public abstract FlowPathV2 toFlowPathV2(PathInfoData path);

    @Mapping(target = "segmentLatency", ignore = true)
    public abstract FlowPathV2.PathNodeV2 toPathNodeV2(PathNode pathNode);

    @Mapping(source = "flowId", target = "id")
    @Mapping(source = "state", target = "status")
    public abstract FlowIdStatusPayload toFlowIdStatusPayload(FlowDto flow);

    @Mapping(target = "latency", source = "meters.networkLatency")
    public abstract UniFlowPingOutput toUniFlowPing(UniFlowPingResponse response);

    /**
     * Map {@link FlowDto} into {@link SwapFlowPayload}.
     */
    public SwapFlowPayload toSwapOutput(FlowDto flowDto) {
        DetectConnectedDevicesDto connectedDevices = flowDto.getDetectConnectedDevices();

        FlowEndpointV2 source = generatedFlowSourceEndpointMap(flowDto);
        source.setDetectConnectedDevices(generatedFlowSourceEndpointConnectedDevicesMap(connectedDevices));
        FlowEndpointV2 destination = generatedFlowDestinationEndpointMap(flowDto);
        destination.setDetectConnectedDevices(generatedFlowDestinationEndpointConnectedDevicesMap(connectedDevices));

        SwapFlowPayload result = generatedSwapFlowPayloadMap(flowDto);
        result.setSource(source);
        result.setDestination(destination);

        return result;
    }

    @Mapping(target = "sourceSwitch", source = "source.switchId")
    @Mapping(target = "destinationSwitch", source = "destination.switchId")
    @Mapping(target = "sourcePort", source = "source.portNumber")
    @Mapping(target = "destinationPort", source = "destination.portNumber")
    @Mapping(target = "sourceVlan", source = "source.vlanId")
    @Mapping(target = "destinationVlan", source = "destination.vlanId")
    public abstract SwapFlowDto toSwapFlowDto(SwapFlowPayload request);

    public abstract FlowValidationDto toFlowValidationDto(FlowValidationResponse response);

    @Mapping(target = "maximumBandwidth", source = "f.bandwidth")
    @Mapping(target = "status", source = "f.state")
    @Mapping(target = "created", source = "f.createdTime")
    @Mapping(target = "statusDetails", source = "f.flowStatusDetails")
    @Mapping(target = "diverseWith", source = "f.diverseWith")
    @Mapping(target = "source", source = "source")
    @Mapping(target = "destination", source = "destination")
    @Mapping(target = "maxLatency", qualifiedByName = "timeNanosToMillis")
    @Mapping(target = "maxLatencyTier2", qualifiedByName = "timeNanosToMillis")
    @Mapping(target = "loopSwitchId", source = "f.loopSwitchId")
    @Mapping(target = "forwardPathLatencyNs", source = "f.forwardLatency")
    @Mapping(target = "reversePathLatencyNs", source = "f.reverseLatency")
    @Mapping(target = "yFlowId", source = "f.YFlowId")
    @Mapping(target = "statistics.vlans", source = "f.vlanStatistics")
    protected abstract FlowResponseV2 generatedMap(FlowDto f, FlowEndpointV2 source, FlowEndpointV2 destination);

    @Mapping(target = "id", source = "flowId")
    @Mapping(target = "maximumBandwidth", source = "bandwidth")
    @Mapping(target = "ignoreBandwidth", source = "ignoreBandwidth")
    @Mapping(target = "maxLatency", qualifiedByName = "timeNanosToMillis")
    @Mapping(target = "status", source = "state")
    @Mapping(target = "created", source = "createdTime")
    @Mapping(target = "pinned", source = "pinned")
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "destination", ignore = true)
    protected abstract void generatedMap(@MappingTarget FlowPayload target, FlowDto f);

    @Mapping(target = "diverseWith", source = "diverseWith")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "maximumBandwidth", ignore = true)
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "destination", ignore = true)
    @Mapping(target = "created", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "maxLatency", qualifiedByName = "timeNanosToMillis")
    protected abstract void generatedFlowResponsePayloadMap(@MappingTarget FlowResponsePayload target, FlowDto f);

    @Mapping(target = "flowId", source = "flowId")
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "destination", ignore = true)
    protected abstract SwapFlowPayload generatedSwapFlowPayloadMap(FlowDto f);

    @Mapping(target = "switchId", source = "sourceSwitch")
    @Mapping(target = "portNumber", source = "sourcePort")
    @Mapping(target = "vlanId", source = "sourceVlan")
    @Mapping(target = "innerVlanId", source = "sourceInnerVlan")
    @Mapping(target = "detectConnectedDevices", ignore = true)
    protected abstract FlowEndpointV2 generatedFlowSourceEndpointMap(FlowDto flow);

    @Mapping(target = "switchId", source = "destinationSwitch")
    @Mapping(target = "portNumber", source = "destinationPort")
    @Mapping(target = "vlanId", source = "destinationVlan")
    @Mapping(target = "innerVlanId", source = "destinationInnerVlan")
    @Mapping(target = "detectConnectedDevices", ignore = true)
    protected abstract FlowEndpointV2 generatedFlowDestinationEndpointMap(FlowDto flow);

    @Mapping(target = "lldp", source = "srcLldp")
    @Mapping(target = "arp", source = "srcArp")
    protected abstract DetectConnectedDevicesV2 generatedFlowSourceEndpointConnectedDevicesMap(
            DetectConnectedDevicesDto connectedDevices);

    @Mapping(target = "lldp", source = "dstLldp")
    @Mapping(target = "arp", source = "dstArp")
    protected abstract DetectConnectedDevicesV2 generatedFlowDestinationEndpointConnectedDevicesMap(
            DetectConnectedDevicesDto connectedDevices);

    protected void mapFlowResponseEndpoints(FlowPayload target, FlowDto source) {
        target.setSource(flowDtoSourceFlowResponsePayload(source));
        target.setDestination(flowDtoDestinationFlowResponsePayload(source));
    }

    protected FlowEndpointPayload flowDtoSourceFlowResponsePayload(FlowDto f) {
        return new FlowEndpointPayload(
                f.getSourceSwitch(), f.getSourcePort(), f.getSourceVlan(), f.getSourceInnerVlan(),
                new DetectConnectedDevicesPayload(f.getDetectConnectedDevices().isSrcLldp(),
                        f.getDetectConnectedDevices().isSrcArp()));
    }

    protected FlowEndpointPayload flowDtoDestinationFlowResponsePayload(FlowDto f) {
        return new FlowEndpointPayload(
                f.getDestinationSwitch(), f.getDestinationPort(), f.getDestinationVlan(), f.getDestinationInnerVlan(),
                new DetectConnectedDevicesPayload(
                        f.getDetectConnectedDevices().isDstLldp(), f.getDetectConnectedDevices().isDstArp()));
    }

    @Mapping(target = "mainPath", source = "mainFlowPathStatus")
    @Mapping(target = "protectedPath", source = "protectedFlowPathStatus")
    public abstract PathStatus map(FlowStatusDetails flowStatusDetails);

    public abstract FlowLoopResponse toFlowLoopResponse(FlowLoopDto payload);

    @Mapping(target = "flowId", source = "payload.flowId")
    @Mapping(target = "switchId", source = "payload.loopSwitchId")
    public abstract FlowLoopResponse toFlowLoopResponse(FlowResponse response);

    @Mapping(target = "timestamp", source = "statusChangeTimestamp")
    public abstract FlowHistoryStatus toFlowHistoryStatus(FlowStatusTimestampsEntry entry);

    public abstract FlowMirrorPointCreateRequest toFlowMirrorPointCreateRequest(String flowId,
                                                                                FlowMirrorPointPayload payload);

    public abstract FlowMirrorPointResponseV2 toFlowMirrorPointResponseV2(FlowMirrorPointResponse response);

    public abstract FlowMirrorPointsResponseV2 toFlowMirrorPointsResponseV2(FlowMirrorPointsDumpResponse response);

    public abstract FlowMirrorPointPayload toFlowMirrorPointPayload(FlowMirrorPoint flowMirrorPoint);

    /**
     * Convert {@link String} to {@link FlowPathDirection}.
     */
    public FlowPathDirection mapFlowPathDirection(String direction) {
        if (direction == null) {
            return null;
        }

        return FlowPathDirection.valueOf(direction.toUpperCase());
    }

    /**
     * Convert {@link FlowEndpoint} to {@link FlowEndpointV2}.
     */
    public FlowEndpointV2 mapFlowEndpointV2(FlowEndpoint input) {
        return FlowEndpointV2.builder()
                .switchId(input.getSwitchId())
                .portNumber(input.getPortNumber())
                .vlanId(input.getOuterVlanId())
                .innerVlanId(input.getInnerVlanId())
                .detectConnectedDevices(new DetectConnectedDevicesV2(input.isTrackLldpConnectedDevices(),
                        input.isTrackArpConnectedDevices()))
                .build();
    }

    public abstract MirrorPointStatus toMirrorPointStatus(MirrorPointStatusDto dto);

    @Mapping(target = "srcLldp", source = "source.detectConnectedDevices.lldp")
    @Mapping(target = "srcArp", source = "source.detectConnectedDevices.arp")
    @Mapping(target = "dstLldp", source = "destination.detectConnectedDevices.lldp")
    @Mapping(target = "dstArp", source = "destination.detectConnectedDevices.arp")
    @Mapping(target = "srcSwitchLldp", ignore = true)
    @Mapping(target = "srcSwitchArp", ignore = true)
    @Mapping(target = "dstSwitchLldp", ignore = true)
    @Mapping(target = "dstSwitchArp", ignore = true)
    public abstract DetectConnectedDevicesDto toDetectConnectedDevicesDto(FlowRequestV2 request);
}
