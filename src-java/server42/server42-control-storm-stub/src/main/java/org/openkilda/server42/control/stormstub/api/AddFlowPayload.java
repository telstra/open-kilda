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


package org.openkilda.server42.control.stormstub.api;

import org.openkilda.server42.control.messaging.flowrtt.EncapsulationType;
import org.openkilda.server42.messaging.FlowDirection;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@AllArgsConstructor
public class AddFlowPayload {
    @JsonProperty("flow_id")
    String flowId;

    @JsonProperty("encapsulation_type")
    EncapsulationType encapsulationType;

    @JsonProperty("tunnel_id")
    Long tunnelId;

    @JsonProperty("direction")
    FlowDirection direction;

    @JsonProperty("port")
    Integer port;
}
