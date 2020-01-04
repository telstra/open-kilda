/* Copyright 2017 Telstra Open Source
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

import static com.google.common.base.MoreObjects.toStringHelper;
import static org.openkilda.messaging.Utils.CORRELATION_ID;
import static org.openkilda.messaging.Utils.DESTINATION;
import static org.openkilda.messaging.Utils.TIMESTAMP;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;

/**
 * Class represents high level view of every message used by any service.
 */
@EqualsAndHashCode(callSuper = true)
public class Message extends BaseMessage {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Message correlation id.
     * Correlation ID request value for Northbound messages or generated value without REST API calls (re-flow, etc.).
     */
    @JsonProperty(CORRELATION_ID)
    protected String correlationId;

    /**
     * Message destination.
     */
    @JsonProperty(DESTINATION)
    protected Destination destination;

    /**
     * Instance constructor.
     *
     * @param timestamp     message timestamp
     * @param correlationId message correlation id
     * @param destination   message destination
     */
    @JsonCreator
    public Message(@JsonProperty(TIMESTAMP) final long timestamp,
                   @JsonProperty(CORRELATION_ID) final String correlationId,
                   @JsonProperty(DESTINATION) final Destination destination) {
        super(timestamp);
        this.correlationId = correlationId;
        this.destination = destination;
    }

    /**
     * Instance constructor.
     *
     * @param timestamp     message timestamp
     * @param correlationId message correlation id
     */
    public Message(final long timestamp, final String correlationId) {
        super(timestamp);
        this.correlationId = correlationId;
    }

    /**
     * Returns message correlation id.
     *
     * @return message correlation id
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * Returns message destination.
     *
     * @return message destination
     */
    public Destination getDestination() {
        return destination;
    }

    /**
     * Sets message destination.
     *
     * @param destination message destination
     */
    public void setDestination(final Destination destination) {
        this.destination = destination;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add(TIMESTAMP, timestamp)
                .add(CORRELATION_ID, correlationId)
                .add(DESTINATION, destination)
                .toString();
    }
}

