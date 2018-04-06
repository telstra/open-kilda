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

package org.openkilda.messaging.command;

import static com.google.common.base.MoreObjects.toStringHelper;
import static org.openkilda.messaging.Utils.CORRELATION_ID;
import static org.openkilda.messaging.Utils.DESTINATION;
import static org.openkilda.messaging.Utils.PAYLOAD;
import static org.openkilda.messaging.Utils.TIMESTAMP;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.openkilda.messaging.Destination;

import java.util.Objects;

/**
 * Class represents command message with reply-to topic.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommandWithReplyToMessage extends CommandMessage {

    @JsonProperty("reply_to")
    protected String replyTo;

    /**
     * Instance constructor.
     *
     * @param data command message payload
     * @param timestamp timestamp value
     * @param correlationId message correlation id
     * @param destination message destination
     * @param replyTo topic for message reply to
     */
    @JsonCreator
    public CommandWithReplyToMessage(@JsonProperty(PAYLOAD) final CommandData data,
            @JsonProperty(TIMESTAMP) final long timestamp,
            @JsonProperty(CORRELATION_ID) final String correlationId,
            @JsonProperty(DESTINATION) final Destination destination,
            @JsonProperty("reply_to") final String replyTo) {
        super(data, timestamp, correlationId, destination);
        this.replyTo = Objects.requireNonNull(replyTo, "reply_to must not be null");
    }

    public String getReplyTo() {
        return replyTo;
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add(TIMESTAMP, timestamp)
                .add(CORRELATION_ID, correlationId)
                .add(DESTINATION, destination)
                .add("replyTo", replyTo)
                .add(PAYLOAD, data)
                .toString();
    }
}
