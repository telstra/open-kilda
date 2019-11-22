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

package org.openkilda.messaging;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.NonNull;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.UUID;

@Value
public class MessageContext implements Serializable {
    @JsonProperty("correlation_id")
    private final String correlationId;

    @JsonProperty("create_time")
    private final long createTime;

    public MessageContext() {
        this(UUID.randomUUID().toString());
    }

    public MessageContext(Message message) {
        this(message.getCorrelationId(), message.getTimestamp());
    }

    public MessageContext(String correlationId) {
        this(correlationId, System.currentTimeMillis());
    }

    public MessageContext(String operationId, String correlationId) {
        this(StringUtils.joinWith(" : ", operationId, correlationId), System.currentTimeMillis());
    }

    @JsonCreator
    public MessageContext(
            @JsonProperty("correlation_id") @NonNull String correlationId,
            @JsonProperty("create_time") long createTime) {
        this.correlationId = correlationId;
        this.createTime = createTime;
    }

    /**
     * Create new {@link MessageContext} object using data from current one. Produced object receive extended/nested
     * correlation ID i.e. it contain original correlation ID plus part passed in argument.
     */
    public MessageContext fork(String correlationIdExtension) {
        return new MessageContext(correlationIdExtension + " : " + correlationId);
    }
}
