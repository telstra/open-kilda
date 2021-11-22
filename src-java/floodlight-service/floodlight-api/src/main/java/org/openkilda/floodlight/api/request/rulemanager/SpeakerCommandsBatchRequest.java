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

import org.openkilda.floodlight.api.OfSpeaker;
import org.openkilda.floodlight.api.request.OfSpeakerBatchEntry;
import org.openkilda.floodlight.api.request.SpeakerServiceRequest;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.NonNull;
import org.apache.commons.lang3.NotImplementedException;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SpeakerCommandsBatchRequest extends SpeakerServiceRequest {

    @JsonProperty("command_data")
    protected Collection<OfSpeakerBatchEntry> batch;

    public SpeakerCommandsBatchRequest(MessageContext messageContext,
                                       @NonNull SwitchId switchId,
                                       @NonNull UUID commandId,
                                       Collection<OfSpeakerBatchEntry> batch) {
        super(messageContext, switchId, commandId, payload);
        this.batch = batch;
    }

    @Override
    public CompletableFuture<MessageContext> execute(OfSpeaker speaker) {
        throw new NotImplementedException(String.format("%s.execute(OfSpeaker speaker)", getClass().getName()));
    }
}
