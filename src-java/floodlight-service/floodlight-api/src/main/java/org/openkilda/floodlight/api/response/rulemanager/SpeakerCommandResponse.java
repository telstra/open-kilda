/* Copyright 2021 Telstra Open Source
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

package org.openkilda.floodlight.api.response.rulemanager;

import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;

import java.util.Map;
import java.util.UUID;

@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SpeakerCommandResponse extends SpeakerResponse {

    @JsonProperty("success")
    boolean success;
    @JsonProperty("failed_command_ids")
    Map<UUID, String> failedCommandIds;

    @Builder
    @JsonCreator
    public SpeakerCommandResponse(@JsonProperty("message_context") @NonNull MessageContext messageContext,
                                  @JsonProperty("command_id") @NonNull UUID commandId,
                                  @JsonProperty("switch_id") @NonNull SwitchId switchId,
                                  @JsonProperty("success") boolean success,
                                  @JsonProperty("failed_command_ids") @NonNull Map<UUID, String> failedCommandIds) {
        super(messageContext, commandId, switchId);
        this.success = success;
        this.failedCommandIds = failedCommandIds;
    }
}
