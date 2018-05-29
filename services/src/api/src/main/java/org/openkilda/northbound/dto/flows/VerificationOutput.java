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

package org.openkilda.northbound.dto.flows;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
public class VerificationOutput {
    @JsonProperty("flow_id")
    private String flowId;

    @JsonProperty("forward")
    private UniFlowVerificationOutput forward;

    @JsonProperty("reverse")
    private UniFlowVerificationOutput reverse;

    // To satisfy mapstruct
    public VerificationOutput() { }

    @Builder
    @JsonCreator
    public VerificationOutput(
            @JsonProperty("flow_id") String flowId,
            @JsonProperty("forward") UniFlowVerificationOutput forward,
            @JsonProperty("reverse") UniFlowVerificationOutput reverse) {
        this.flowId = flowId;
        this.forward = forward;
        this.reverse = reverse;
    }
}
