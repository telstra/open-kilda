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

package org.openkilda.floodlight.api.response;

import org.openkilda.messaging.AbstractMessage;
import org.openkilda.messaging.MessageContext;
import org.openkilda.messaging.MessageData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode(callSuper = false)
public class SpeakerDataResponse extends AbstractMessage {
    MessageData data;

    @JsonCreator
    public SpeakerDataResponse(@JsonProperty("message_context") @NonNull MessageContext messageContext,
                               @JsonProperty("data") @NonNull MessageData data) {
        super(messageContext);

        this.data = data;
    }
}
