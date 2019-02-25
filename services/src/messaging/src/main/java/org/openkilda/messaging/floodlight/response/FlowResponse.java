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

package org.openkilda.messaging.floodlight.response;

import static org.openkilda.messaging.Utils.FLOW_ID;

import org.openkilda.messaging.CommandContext;
import org.openkilda.messaging.floodlight.FlowMessage;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class FlowResponse extends FlowMessage {

    @JsonProperty
    private boolean success;

    @JsonProperty
    private Error error;

    @JsonCreator
    @Builder
    public FlowResponse(@JsonProperty("success") boolean success,
                        @JsonProperty("error") Error error,
                        @JsonProperty("command-context") CommandContext commandContext,
                        @JsonProperty(FLOW_ID) String flowId,
                        @JsonProperty("switch_id") SwitchId switchId) {
        super(commandContext, flowId, switchId);

        this.success = success;
        this.error = error;
    }

    public FlowResponse(FlowMessage flowMessage, boolean success) {
        super(flowMessage.getCommandContext(), flowMessage.getFlowId(), flowMessage.getSwitchId());
        this.success = success;
    }

    public enum Error {
        SWITCH_UNAVAILABLE,
        UNSUPPORTED_OPERATION,
        OPERATION_TIMEOUT,
        OTHER
    }
}
