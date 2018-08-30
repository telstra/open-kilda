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

package org.openkilda.messaging.info.flow;

import org.openkilda.messaging.info.InfoData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@Builder
@EqualsAndHashCode(callSuper = false)
public class FlowPingResponse extends InfoData {
    @JsonProperty("flow_id")
    private String flowId;

    @JsonProperty("forward")
    private UniFlowPingResponse forward;

    @JsonProperty("reverse")
    private UniFlowPingResponse reverse;

    @JsonProperty("error")
    private String error;

    @JsonCreator
    public FlowPingResponse(
            @JsonProperty("flow_id") String flowId,
            @JsonProperty("forward") UniFlowPingResponse forward,
            @JsonProperty("reverse") UniFlowPingResponse reverse,
            @JsonProperty("error") String error) {
        this.flowId = flowId;
        this.forward = forward;
        this.reverse = reverse;
        this.error = error;
    }

    public FlowPingResponse(String flowId, String errorMessage) {
        this(flowId, null, null, errorMessage);
    }

    /**
     * Check all error fields and return true if any one is set.
     */
    @JsonIgnore
    public boolean isError() {
        return (error != null)
                || ((forward != null) && forward.getError() != null)
                || ((reverse != null) && reverse.getError() != null);
    }
}
