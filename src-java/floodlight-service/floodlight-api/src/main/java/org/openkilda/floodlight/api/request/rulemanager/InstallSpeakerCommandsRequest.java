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

package org.openkilda.floodlight.api.request.rulemanager;

import org.openkilda.floodlight.api.BatchCommandProcessor;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

import java.util.Collection;
import java.util.UUID;

public class InstallSpeakerCommandsRequest extends BaseSpeakerCommandsRequest {

    @JsonProperty("fail_if_exists")
    @Getter
    private final boolean failIfExists;

    @Builder(toBuilder = true)
    @JsonCreator
    public InstallSpeakerCommandsRequest(@JsonProperty("message_context") MessageContext messageContext,
                                         @JsonProperty("switch_id") @NonNull SwitchId switchId,
                                         @JsonProperty("command_id") @NonNull UUID commandId,
                                         @JsonProperty("command_data") Collection<OfCommand> commands,
                                         @JsonProperty("origin") Origin origin,
                                         @JsonProperty("fail_if_exists") Boolean failIfExists) {
        super(messageContext, switchId, commandId, commands, origin);
        this.failIfExists = failIfExists == null || failIfExists;
    }

    public void process(BatchCommandProcessor processor, String key) {
        processor.processBatchInstall(this, key);
    }
}
