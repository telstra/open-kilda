/* Copyright 2024 Telstra Open Source
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

import org.openkilda.messaging.MessageContext;
import org.openkilda.messaging.MessageData;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class ChunkedSpeakerDataResponse extends SpeakerDataResponse {

    @JsonProperty("message_id")
    private String messageId;

    @JsonProperty("total_messages")
    private int totalMessages;

    public ChunkedSpeakerDataResponse(@JsonProperty("data") MessageData data,
                                      @JsonProperty("message_context") @NonNull MessageContext messageContext,
                                      @JsonProperty("total_messages") int totalMessages) {
        super(messageContext, data);
        this.messageId = messageContext.getCorrelationId();
        this.totalMessages = totalMessages;
    }

    ChunkedSpeakerDataResponse(MessageData data, MessageContext messageContext, int totalMessages, int messageIndex) {
        this(data, messageContext, totalMessages);
        this.messageId = String.join(" : ", String.valueOf(messageIndex),
                messageContext.getCorrelationId());
    }

    /**
     * Creates list of ChunkedInfoMessages from list of InfoData.
     */
    public static List<ChunkedSpeakerDataResponse> createChunkedList(
            Collection<? extends MessageData> dataCollection, MessageContext messageContext) {

        if (CollectionUtils.isEmpty(dataCollection)) {
            return Collections.singletonList(new ChunkedSpeakerDataResponse(null, messageContext, 0));
        }

        List<ChunkedSpeakerDataResponse> result = new ArrayList<>();
        int i = 0;
        for (MessageData messageData : dataCollection) {
            result.add(new ChunkedSpeakerDataResponse(
                    messageData, messageContext, dataCollection.size(), i++));
        }
        return result;
    }
}
