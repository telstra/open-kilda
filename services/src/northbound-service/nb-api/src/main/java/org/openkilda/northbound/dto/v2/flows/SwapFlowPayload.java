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

package org.openkilda.northbound.dto.v2.flows;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonNaming(value = SnakeCaseStrategy.class)
public class SwapFlowPayload {
    private static final long serialVersionUID = 1L;

    @JsonProperty("flow_id")
    private String flowId;

    @JsonProperty("source")
    private FlowEndpointV2 source;

    @JsonProperty("destination")
    private FlowEndpointV2 destination;

    public SwapFlowPayload(@JsonProperty("flow_id") String flowId,
                           @JsonProperty("source") FlowEndpointV2 source,
                           @JsonProperty("destination") FlowEndpointV2 destination) {
        this.flowId = flowId;
        this.source = source;
        this.destination = destination;
    }
}
