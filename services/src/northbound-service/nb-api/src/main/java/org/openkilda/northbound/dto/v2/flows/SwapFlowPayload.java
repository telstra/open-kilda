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

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.payload.flow.FlowEndpointPayload;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Data
@NoArgsConstructor
@EqualsAndHashCode
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SwapFlowPayload {
    private static final long serialVersionUID = 1L;

    @JsonProperty(Utils.FLOW_ID)
    private String flowId;

    @NonNull
    @JsonProperty("source")
    private FlowEndpointPayload source;

    @NonNull
    @JsonProperty("destination")
    private FlowEndpointPayload destination;

    /**
     * Instance constructor.
     *
     * @param flowId                    flow id
     * @param source                flow source
     * @param destination           flow destination
     */
    @Builder
    @JsonCreator
    public SwapFlowPayload(@JsonProperty(Utils.FLOW_ID) String flowId,
                       @JsonProperty("source") FlowEndpointPayload source,
                       @JsonProperty("destination") FlowEndpointPayload destination) {
        setFlowId(flowId);
        setSource(source);
        setDestination(destination);
    }

    /**
     * Sets flow id.
     */
    public void setFlowId(String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("need to set id");
        }
        this.flowId = id;
    }
}
