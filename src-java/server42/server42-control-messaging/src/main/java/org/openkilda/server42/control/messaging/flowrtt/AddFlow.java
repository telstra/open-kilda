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

package org.openkilda.server42.control.messaging.flowrtt;

import org.openkilda.server42.messaging.FlowDirection;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@Builder
@EqualsAndHashCode(callSuper = true)
@JsonNaming(value = SnakeCaseStrategy.class)
@JsonDeserialize(builder = AddFlow.AddFlowBuilder.class)
public class AddFlow extends Message {
    @JsonProperty("headers")
    Headers headers;
    @JsonProperty("flow_id")
    String flowId;
    @JsonProperty("tunnel_id")
    Long tunnelId;
    @JsonProperty("inner_tunnel_id")
    Long innerTunnelId;
    @JsonProperty("direction")
    FlowDirection direction;
    @JsonProperty("port")
    Integer port;
}
