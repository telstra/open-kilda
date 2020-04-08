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

package org.openkilda.northbound.converter;

import org.openkilda.messaging.command.flow.FlowRequest;
import org.openkilda.messaging.command.flow.FlowRequest.Type;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.flow.FlowPingResponse;
import org.openkilda.messaging.info.flow.FlowReadResponse;
import org.openkilda.messaging.info.flow.UniFlowPingResponse;
import org.openkilda.messaging.model.DetectConnectedDevicesDto;
import org.openkilda.messaging.model.FlowDto;
import org.openkilda.messaging.model.Ping;
import org.openkilda.messaging.model.SwapFlowDto;
import org.openkilda.messaging.nbtopology.response.FlowValidationResponse;
import org.openkilda.messaging.payload.flow.DetectConnectedDevicesPayload;
import org.openkilda.messaging.payload.flow.FlowEncapsulationType;
import org.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowReroutePayload;
import org.openkilda.messaging.payload.flow.FlowResponsePayload;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.messaging.payload.flow.FlowStatusDetails;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.northbound.dto.v1.flows.FlowPatchDto;
import org.openkilda.northbound.dto.v1.flows.FlowValidationDto;
import org.openkilda.northbound.dto.v1.flows.PingOutput;
import org.openkilda.northbound.dto.v1.flows.UniFlowPingOutput;
import org.openkilda.northbound.dto.v2.flows.DetectConnectedDevicesV2;
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2;
import org.openkilda.northbound.dto.v2.flows.FlowPathV2;
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2;
import org.openkilda.northbound.dto.v2.flows.FlowRerouteResponseV2;
import org.openkilda.northbound.dto.v2.flows.FlowResponseV2;
import org.openkilda.northbound.dto.v2.flows.PathStatus;
import org.openkilda.northbound.dto.v2.flows.SwapFlowPayload;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring",
        imports = {FlowEndpointPayload.class, FlowEndpointV2.class, DetectConnectedDevicesPayload.class,
                DetectConnectedDevicesV2.class, DetectConnectedDevicesDto.class})
public interface FlowMapper {
    @Mapping(target = "id", source = "flowId")
    @Mapping(target = "source",
            expression = "java(new FlowEndpointPayload(f.getSourceSwitch(), f.getSourcePort(), f.getSourceVlan(), "
                    + "new DetectConnectedDevicesPayload(f.getDetectConnectedDevices().isSrcLldp(), "
                    + "f.getDetectConnectedDevices().isSrcArp())))")
    @Mapping(target = "destination",
            expression = "java(new FlowEndpointPayload(f.getDestinationSwitch(), f.getDestinationPort(), "
                    + "f.getDestinationVlan(), new DetectConnectedDevicesPayload("
                    + "f.getDetectConnectedDevices().isDstLldp(), f.getDetectConnectedDevices().isDstArp())))")
    @Mapping(target = "maximumBandwidth", source = "bandwidth")
    @Mapping(target = "ignoreBandwidth", source = "ignoreBandwidth")
    @Mapping(target = "status", source = "state")
    @Mapping(target = "created", source = "createdTime")
    @Mapping(target = "pinned", source = "pinned")
    FlowPayload toFlowOutput(FlowDto f);

    /**
     * Map FlowReadResponse.
     *
     * @param r  {@link FlowReadResponse} instance.
     * @return {@link FlowResponsePayload} instance.
     */
    default FlowResponsePayload toFlowResponseOutput(FlowReadResponse r) {
        FlowResponsePayload response = toFlowResponseOutput(r.getPayload());
        response.setDiverseWith(r.getDiverseWith());
        return response;
    }

    @Mapping(target = "id", source = "flowId")
    @Mapping(target = "source",
            expression = "java(new FlowEndpointPayload(f.getSourceSwitch(), f.getSourcePort(), f.getSourceVlan(), "
                    + "new DetectConnectedDevicesPayload(f.getDetectConnectedDevices().isSrcLldp(), "
                    + "f.getDetectConnectedDevices().isSrcArp())))")
    @Mapping(target = "destination",
            expression = "java(new FlowEndpointPayload(f.getDestinationSwitch(), f.getDestinationPort(), "
                    + "f.getDestinationVlan(), new DetectConnectedDevicesPayload("
                    + "f.getDetectConnectedDevices().isDstLldp(), f.getDetectConnectedDevices().isDstArp())))")
    @Mapping(target = "maximumBandwidth", source = "bandwidth")
    @Mapping(target = "ignoreBandwidth", source = "ignoreBandwidth")
    @Mapping(target = "status", source = "state")
    @Mapping(target = "created", source = "createdTime")
    @Mapping(target = "pinned", source = "pinned")
    @Mapping(target = "diverseWith", source = "diverseWith")
    FlowResponsePayload toFlowResponseOutput(FlowDto f);

    @Mapping(target = "source",
            expression = "java(new FlowEndpointV2(f.getSourceSwitch(), f.getSourcePort(), f.getSourceVlan(), "
                    + "new DetectConnectedDevicesV2("
                    + "f.getDetectConnectedDevices().isSrcLldp(), f.getDetectConnectedDevices().isSrcArp())))")
    @Mapping(target = "destination",
            expression = "java(new FlowEndpointV2(f.getDestinationSwitch(), f.getDestinationPort(), "
                    + "f.getDestinationVlan(), new DetectConnectedDevicesV2("
                    + "f.getDetectConnectedDevices().isDstLldp(), f.getDetectConnectedDevices().isDstArp())))")
    @Mapping(target = "maximumBandwidth", source = "bandwidth")
    @Mapping(target = "status", source = "state")
    @Mapping(target = "created", source = "createdTime")
    @Mapping(target = "statusDetails", source = "flowStatusDetails")
    @Mapping(target = "diverseWith", source = "diverseWith")
    FlowResponseV2 toFlowResponseV2(FlowDto f);

    @Mapping(target = "flowId", ignore = true)
    @Mapping(target = "bandwidth", ignore = true)
    @Mapping(target = "ignoreBandwidth", ignore = true)
    @Mapping(target = "allocateProtectedPath", ignore = true)
    @Mapping(target = "cookie", ignore = true)
    @Mapping(target = "description", ignore = true)
    @Mapping(target = "createdTime", ignore = true)
    @Mapping(target = "lastUpdated", ignore = true)
    @Mapping(target = "sourceSwitch", ignore = true)
    @Mapping(target = "destinationSwitch", ignore = true)
    @Mapping(target = "sourcePort", ignore = true)
    @Mapping(target = "destinationPort", ignore = true)
    @Mapping(target = "sourceVlan", ignore = true)
    @Mapping(target = "destinationVlan", ignore = true)
    @Mapping(target = "meterId", ignore = true)
    @Mapping(target = "transitEncapsulationId", ignore = true)
    @Mapping(target = "state", ignore = true)
    @Mapping(target = "flowStatusDetails", ignore = true)
    @Mapping(target = "pinned", ignore = true)
    @Mapping(target = "encapsulationType", ignore = true)
    @Mapping(target = "detectConnectedDevices", ignore = true)
    @Mapping(target = "pathComputationStrategy", ignore = true)
    @Mapping(target = "diverseWith", ignore = true)
    FlowDto toFlowDto(FlowPatchDto flowPatchDto);

    @Mapping(target = "sourceSwitch", expression = "java(request.getSource().getSwitchId())")
    @Mapping(target = "destinationSwitch", expression = "java(request.getDestination().getSwitchId())")
    @Mapping(target = "sourcePort", expression = "java(request.getSource().getPortNumber())")
    @Mapping(target = "destinationPort", expression = "java(request.getDestination().getPortNumber())")
    @Mapping(target = "sourceVlan", expression = "java(request.getSource().getVlanId())")
    @Mapping(target = "destinationVlan", expression = "java(request.getDestination().getVlanId())")
    @Mapping(target = "bandwidth", source = "maximumBandwidth")
    @Mapping(target = "detectConnectedDevices", expression = "java(new DetectConnectedDevicesDto("
            + "request.getSource().getDetectConnectedDevices().isLldp(), "
            + "request.getSource().getDetectConnectedDevices().isArp(), "
            + "request.getDestination().getDetectConnectedDevices().isLldp(), "
            + "request.getDestination().getDetectConnectedDevices().isArp()))")
    @Mapping(target = "transitEncapsulationId", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "bulkUpdateFlowIds", ignore = true)
    @Mapping(target = "doNotRevert", ignore = true)
    FlowRequest toFlowRequest(FlowRequestV2 request);

    default FlowRequest toFlowCreateRequest(FlowRequestV2 source) {
        return toFlowRequest(source).toBuilder().type(Type.CREATE).build();
    }

    PingOutput toPingOutput(FlowPingResponse response);

    @Mapping(source = "flowId", target = "id")
    @Mapping(source = "path", target = "path")
    @Mapping(source = "rerouted", target = "rerouted")
    FlowReroutePayload toReroutePayload(String flowId, PathInfoData path, boolean rerouted);


    @Mapping(source = "path", target = "path")
    FlowRerouteResponseV2 toRerouteResponseV2(String flowId, PathInfoData path, boolean rerouted);

    @Mapping(source = "path", target = "nodes")
    FlowPathV2 toFlowPathV2(PathInfoData path);

    @Mapping(target = "segmentLatency", ignore = true)
    FlowPathV2.PathNodeV2 toPathNodeV2(PathNode pathNode);

    @Mapping(source = "flowId", target = "id")
    @Mapping(source = "state", target = "status")
    FlowIdStatusPayload toFlowIdStatusPayload(FlowDto flow);

    @Mapping(target = "latency", source = "meters.networkLatency")
    UniFlowPingOutput toUniFlowPing(UniFlowPingResponse response);

    @Mapping(target = "flowId", source = "flowId")
    @Mapping(target = "source",
            expression = "java(new FlowEndpointV2(f.getSourceSwitch(), f.getSourcePort(), f.getSourceVlan(), "
                    + "new DetectConnectedDevicesV2("
                    + "f.getDetectConnectedDevices().isSrcLldp(), f.getDetectConnectedDevices().isSrcArp())))")
    @Mapping(target = "destination",
            expression = "java(new FlowEndpointV2(f.getDestinationSwitch(), f.getDestinationPort(), "
                    + "f.getDestinationVlan(), new DetectConnectedDevicesV2( "
                    + "f.getDetectConnectedDevices().isDstLldp(), f.getDetectConnectedDevices().isDstArp())))")
    SwapFlowPayload toSwapOutput(FlowDto f);

    @Mapping(target = "sourceSwitch", expression = "java(request.getSource().getSwitchId())")
    @Mapping(target = "destinationSwitch", expression = "java(request.getDestination().getSwitchId())")
    @Mapping(target = "sourcePort", expression = "java(request.getSource().getPortNumber())")
    @Mapping(target = "destinationPort", expression = "java(request.getDestination().getPortNumber())")
    @Mapping(target = "sourceVlan", expression = "java(request.getSource().getVlanId())")
    @Mapping(target = "destinationVlan", expression = "java(request.getDestination().getVlanId())")
    SwapFlowDto toSwapFlowDto(SwapFlowPayload request);

    FlowValidationDto toFlowValidationDto(FlowValidationResponse response);

    /**
     * Convert {@link FlowState} to {@link String}.
     */
    default String encodeFlowState(FlowState state) {
        if (state == null) {
            return null;
        }

        return state.getState();
    }

    @Mapping(target = "mainPath", source = "mainFlowPathStatus")
    @Mapping(target = "protectedPath", source = "protectedFlowPathStatus")
    PathStatus map(FlowStatusDetails flowStatusDetails);
    /**
     * Convert {@link FlowPathStatus} to {@link String}.
     */
    
    default String map(FlowPathStatus flowPathStatus) {
        if (flowPathStatus == null) {
            return null;
        }

        switch (flowPathStatus) {
            case ACTIVE:
                return "Up";
            case INACTIVE:
                return "Down";
            case IN_PROGRESS:
                return "In progress";
            default:
                return flowPathStatus.toString().toLowerCase();
        }
    }

    /**
     * Convert {@link FlowEncapsulationType} to {@link String}.
     */
    default String map(FlowEncapsulationType encapsulationType) {
        if (encapsulationType == null) {
            return null;
        }

        return encapsulationType.toString().toLowerCase();
    }

    /**
     * Convert {@link String} to {@link FlowEncapsulationType}.
     */
    default FlowEncapsulationType map(String encapsulationType) {
        if (encapsulationType == null) {
            return null;
        }

        return FlowEncapsulationType.valueOf(encapsulationType.toUpperCase());
    }

    /**
     * Convert {@link PathComputationStrategy} to {@link String}.
     */
    default String map(PathComputationStrategy pathComputationStrategy) {
        if (pathComputationStrategy == null) {
            return null;
        }

        return pathComputationStrategy.toString().toLowerCase();
    }

    /**
     * Convert {@link String} to {@link PathComputationStrategy}.
     */
    default PathComputationStrategy mapPathComputationStrategy(String pathComputationStrategy) {
        if (pathComputationStrategy == null) {
            return null;
        }

        return PathComputationStrategy.valueOf(pathComputationStrategy.toUpperCase());
    }

    /**
     * Translate Java's error code(enum) into human readable string.
     */
    default String getPingError(Ping.Errors error) {
        if (error == null) {
            return null;
        }

        String message;
        switch (error) {
            case TIMEOUT:
                message = "No ping for reasonable time";
                break;
            case WRITE_FAILURE:
                message = "Can't send ping";
                break;
            case NOT_CAPABLE:
                message = "Can't ping - at least one of endpoints are not capable to catch pings.";
                break;
            case SOURCE_NOT_AVAILABLE:
            case DEST_NOT_AVAILABLE:
                message = "Can't ping - at least one of endpoints are unavailable";
                break;
            default:
                message = error.toString();
        }

        return message;
    }
}
