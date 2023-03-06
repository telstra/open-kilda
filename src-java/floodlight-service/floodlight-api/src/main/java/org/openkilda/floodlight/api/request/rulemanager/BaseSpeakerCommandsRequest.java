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
import org.openkilda.floodlight.api.request.SpeakerRequest;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

import java.util.Collection;
import java.util.UUID;

@JsonTypeInfo(use = Id.NAME, property = "clazz")
@JsonSubTypes({
        @Type(value = InstallSpeakerCommandsRequest.class,
                name = "org.openkilda.floodlight.api.request.rulemanager.InstallSpeakerCommandsRequest"),
        @Type(value = ModifySpeakerCommandsRequest.class,
                name = "org.openkilda.floodlight.api.request.rulemanager.ModifySpeakerCommandsRequest"),
        @Type(value = DeleteSpeakerCommandsRequest.class,
                name = "org.openkilda.floodlight.api.request.rulemanager.DeleteSpeakerCommandsRequest")
})

@Getter
@ToString(callSuper = true)
public abstract class BaseSpeakerCommandsRequest extends SpeakerRequest {

    @JsonProperty("command_data")
    protected Collection<OfCommand> commands;

    @JsonProperty("source_topic")
    protected String sourceTopic;

    public BaseSpeakerCommandsRequest(MessageContext messageContext,
                                      @NonNull SwitchId switchId,
                                      @NonNull UUID commandId,
                                      Collection<OfCommand> commands) {

        super(messageContext, switchId, commandId);
        this.commands = commands;
    }

    public void setSourceTopic(String sourceTopic) {
        this.sourceTopic = sourceTopic;
    }

    public abstract void process(BatchCommandProcessor processor, String key);

}
