/*
 * Copyright 2017 Telstra Open Source
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openkilda.messaging.info.flow;

import org.openkilda.messaging.info.InfoData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class FlowVerificationResponse extends InfoData {
    @JsonProperty("flow_id")
    private String flowId;

    @JsonProperty("forward")
    private UniFlowVerificationResponse forward;

    @JsonProperty("reverse")
    private UniFlowVerificationResponse reverse;

    @JsonCreator
    public FlowVerificationResponse(
            @JsonProperty("flow_id") String flowId,
            @JsonProperty("forward") UniFlowVerificationResponse forward,
            @JsonProperty("reverse") UniFlowVerificationResponse reverse) {
        this.flowId = flowId;
        this.forward = forward;
        this.reverse = reverse;
    }
}
