/* Copyright 2018 Telstra Open Source
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

import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.flow.FlowPingResponse;
import org.openkilda.messaging.info.flow.UniFlowPingResponse;
import org.openkilda.messaging.model.BidirectionalFlowDto;
import org.openkilda.messaging.model.FlowDto;
import org.openkilda.messaging.model.Ping;
import org.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowReroutePayload;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.northbound.dto.v1.flows.FlowPatchDto;
import org.openkilda.northbound.dto.v1.flows.PingOutput;
import org.openkilda.northbound.dto.v1.flows.UniFlowPingOutput;
import org.openkilda.northbound.dto.v2.flows.FlowEndpointV2;
import org.openkilda.northbound.dto.v2.flows.FlowRequestV2;
import org.openkilda.northbound.dto.v2.flows.FlowResponseV2;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = {FlowEndpointPayload.class, FlowEndpointV2.class})
public interface FlowMapper {
    @Mapping(target = "id", source = "flowId")
    @Mapping(target = "source",
            expression = "java(new FlowEndpointPayload(f.getSourceSwitch(), f.getSourcePort(), f.getSourceVlan()))")
    @Mapping(target = "destination",
            expression = "java(new FlowEndpointPayload(f.getDestinationSwitch(), f.getDestinationPort(), "
                    + "f.getDestinationVlan()))")
    @Mapping(target = "maximumBandwidth", source = "bandwidth")
    @Mapping(target = "ignoreBandwidth", source = "ignoreBandwidth")
    @Mapping(target = "status", source = "state")
    @Mapping(target = "created", source = "createdTime")
    FlowPayload toFlowOutput(FlowDto f);

    @Mapping(target = "source",
            expression = "java(FlowEndpointV2.builder().switchId(f.getSourceSwitch())"
                    + ".portNumber(f.getSourcePort()).vlanId(f.getSourceVlan()).build())")
    @Mapping(target = "destination",
            expression = "java(FlowEndpointV2.builder().switchId(f.getDestinationSwitch())"
                    + ".portNumber(f.getDestinationPort()).vlanId(f.getDestinationVlan()).build())")
    @Mapping(target = "maximumBandwidth", source = "bandwidth")
    @Mapping(target = "status", source = "state")
    @Mapping(target = "created", source = "createdTime")
    FlowResponseV2 toFlowResponseV2(FlowDto f);

    FlowDto toFlowDto(FlowPatchDto flowPatchDto);

    @Mapping(target = "sourceSwitch", expression = "java(request.getSource().getSwitchId())")
    @Mapping(target = "destinationSwitch", expression = "java(request.getDestination().getSwitchId())")
    @Mapping(target = "sourcePort", expression = "java(request.getSource().getPortNumber())")
    @Mapping(target = "destinationPort", expression = "java(request.getDestination().getPortNumber())")
    @Mapping(target = "sourceVlan", expression = "java(request.getSource().getVlanId())")
    @Mapping(target = "destinationVlan", expression = "java(request.getDestination().getVlanId())")
    @Mapping(target = "bandwidth", source = "maximumBandwidth")
    FlowDto toFlowDto(FlowRequestV2 request);

    PingOutput toPingOutput(FlowPingResponse response);

    @Mapping(source = "flowId", target = "id")
    @Mapping(source = "path", target = "path")
    @Mapping(source = "rerouted", target = "rerouted")
    FlowReroutePayload toReroutePayload(String flowId, PathInfoData path, boolean rerouted);

    @Mapping(source = "flowId", target = "id")
    @Mapping(source = "state", target = "status")
    FlowIdStatusPayload toFlowIdStatusPayload(BidirectionalFlowDto flow);

    @Mapping(target = "latency", source = "meters.networkLatency")
    UniFlowPingOutput toUniFlowPing(UniFlowPingResponse response);

    /**
     * Convert {@link FlowState} to {@link String}.
     */
    default String encodeFlowState(FlowState state) {
        if (state == null) {
            return null;
        }

        return state.getState();
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
