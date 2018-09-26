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

    public ChunkedInfoMessage(InfoData data, long timestamp, String correlationId,
                              int messageIndex, int totalMessages) {
        super(data, timestamp, correlationId);
        this.messageId = correlationId + " : " + messageIndex;
        this.totalMessages = totalMessages;
    }
}
