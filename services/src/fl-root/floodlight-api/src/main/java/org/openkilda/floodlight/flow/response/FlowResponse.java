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

package org.openkilda.floodlight.flow.response;

import static org.openkilda.messaging.Utils.FLOW_ID;

import org.openkilda.floodlight.FloodlightResponse;
import org.openkilda.floodlight.flow.request.FlowRequest;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class FlowResponse extends FloodlightResponse {

    @JsonProperty("command_id")
    private final String commandId;

    @JsonProperty(FLOW_ID)
    private final String flowId;

    @JsonProperty("switch_id")
    private final SwitchId switchId;

    @JsonProperty
    private final boolean success;

    @JsonCreator
    @Builder
    public FlowResponse(@JsonProperty("success") boolean success,
                        @JsonProperty("command_context") MessageContext messageContext,
                        @JsonProperty("command_id") String commandId,
                        @JsonProperty(FLOW_ID) String flowId,
                        @JsonProperty("switch_id") SwitchId switchId) {
        super(messageContext);

        this.commandId = commandId;
        this.flowId = flowId;
        this.switchId = switchId;
        this.success = success;
    }

    public FlowResponse(FlowRequest request, boolean success) {
        super(request.getMessageContext());

        this.commandId = request.getCommandId();
        this.flowId = request.getFlowId();
        this.switchId = request.getSwitchId();
        this.success = success;
    }

}
