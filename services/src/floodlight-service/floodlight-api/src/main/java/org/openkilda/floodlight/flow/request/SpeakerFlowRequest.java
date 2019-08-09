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

package org.openkilda.floodlight.flow.request;

import static java.util.Objects.requireNonNull;
import static org.openkilda.messaging.Utils.FLOW_ID;

import org.openkilda.messaging.AbstractMessage;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

@Getter
@ToString
public abstract class SpeakerFlowRequest extends AbstractMessage {

    /**
     * Unique identifier for the command.
     */
    @JsonProperty("command_id")
    private final UUID commandId;

    /**
     * The flow id.
     */
    @JsonProperty(FLOW_ID)
    private final String flowId;

    /**
     * The switch id to manage flow on. It is a mandatory parameter.
     */
    @JsonProperty("switch_id")
    final SwitchId switchId;

    /**
     * Whether use multitable pipeline or not.
     */
    @JsonProperty("multi_table")
    final boolean multiTable;

    public SpeakerFlowRequest(MessageContext context, UUID commandId, String flowId, SwitchId switchId,
                              boolean multiTable) {
        super(context);

        requireNonNull(commandId, "Message id should be not null");
        requireNonNull(flowId, "Flow id should be not null");
        this.commandId = commandId;
        this.flowId = flowId;
        this.switchId = switchId;
        this.multiTable = multiTable;
    }

}
