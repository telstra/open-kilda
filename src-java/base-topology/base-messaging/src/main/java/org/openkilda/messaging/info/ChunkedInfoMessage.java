/* Copyright 2018 Telstra Open Source
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

package org.openkilda.messaging.info;

import static org.openkilda.messaging.Utils.CORRELATION_ID;
import static org.openkilda.messaging.Utils.PAYLOAD;
import static org.openkilda.messaging.Utils.TIMESTAMP;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class ChunkedInfoMessage extends InfoMessage {

    @JsonProperty("message_id")
    private String messageId;

    @JsonProperty("total_messages")
    private int totalMessages;

    public ChunkedInfoMessage(@JsonProperty(PAYLOAD) InfoData data,
                              @JsonProperty(TIMESTAMP) long timestamp,
                              @JsonProperty(CORRELATION_ID) String correlationId,
                              @JsonProperty("message_id") String messageId,
                              @JsonProperty("total_messages") int totalMessages) {
        super(data, timestamp, correlationId);
        this.messageId = messageId;
        this.totalMessages = totalMessages;
    }

    ChunkedInfoMessage(InfoData data, long timestamp, String correlationId,
                              int messageIndex, int totalMessages) {
        super(data, timestamp, correlationId);
        this.messageId = String.join(" : ", String.valueOf(messageIndex), correlationId);
        this.totalMessages = totalMessages;
    }

    /**
     * Creates list of ChunkedInfoMessages from list of InfoData.
     */
    public static List<ChunkedInfoMessage> createChunkedList(
            Collection<? extends InfoData> dataCollection, String messageId) {
        List<ChunkedInfoMessage> result = new ArrayList<>();
        if (dataCollection == null || dataCollection.isEmpty()) {
            result.add(new ChunkedInfoMessage(null, System.currentTimeMillis(), messageId, messageId, 0));
        } else {
            int i = 0;
            for (InfoData infoData : dataCollection) {
                result.add(new ChunkedInfoMessage(
                        infoData, System.currentTimeMillis(), messageId, i++, dataCollection.size()));
            }
        }
        return result;
    }
}
