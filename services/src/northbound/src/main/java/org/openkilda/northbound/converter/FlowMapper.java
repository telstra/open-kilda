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

import org.openkilda.messaging.info.flow.FlowPingResponse;
import org.openkilda.messaging.info.flow.UniFlowPingResponse;
import org.openkilda.messaging.model.Ping;
import org.openkilda.northbound.dto.flows.FlowPingOutput;
import org.openkilda.northbound.dto.flows.UniFlowPingOutput;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface FlowMapper {
    FlowPingOutput toFlowPingOutput(FlowPingResponse response);

    @Mapping(source = "meters.networkLatency", target = "latency")
    UniFlowPingOutput toUniFlowPing(UniFlowPingResponse response);

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
