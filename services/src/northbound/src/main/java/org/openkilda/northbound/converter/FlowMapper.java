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
import org.openkilda.northbound.dto.flows.FlowPatchDto;
import org.openkilda.northbound.dto.flows.PingOutput;
import org.openkilda.northbound.dto.flows.UniFlowPingOutput;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = FlowEndpointPayload.class)
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
    FlowPayload toFlowOutput(FlowDto f);

    FlowDto toFlowDto(FlowPatchDto flowPatchDto);

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
