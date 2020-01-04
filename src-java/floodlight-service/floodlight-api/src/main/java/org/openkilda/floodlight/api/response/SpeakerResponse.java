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

package org.openkilda.floodlight.api.response;

import org.openkilda.messaging.AbstractMessage;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NonNull;

import java.util.UUID;

@Getter
public abstract class SpeakerResponse extends AbstractMessage {
    @JsonProperty("command_id")
    protected final UUID commandId;

    @JsonProperty("switch_id")
    protected final SwitchId switchId;

    public SpeakerResponse(MessageContext messageContext,
                           @NonNull UUID commandId, @NonNull SwitchId switchId) {
        super(messageContext);
        this.commandId = commandId;
        this.switchId = switchId;
    }

    public boolean isSuccess() {
        return ! (this instanceof SpeakerErrorResponse);
    }
}
