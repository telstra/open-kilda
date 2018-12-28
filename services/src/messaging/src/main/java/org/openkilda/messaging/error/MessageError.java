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

package org.openkilda.messaging.error;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.MoreObjects.toStringHelper;
import static org.openkilda.messaging.Utils.CORRELATION_ID;
import static org.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;
import static org.openkilda.messaging.Utils.TIMESTAMP;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Data;

import java.io.Serializable;
import java.util.Objects;

/**
 * The class represents error response.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class MessageError implements Serializable {
    /**
     * The Constant serialVersionUID.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The failed request correlation ID.
     */
    @JsonProperty(CORRELATION_ID)
    private String correlationId;

    /**
     * The error timestamp.
     */
    @JsonProperty(TIMESTAMP)
    private long timestamp;

    /**
     * Error type.
     */
    @JsonProperty("error-type")
    private String errorType;

    /**
     * Error message.
     */
    @JsonProperty("error-message")
    private String errorMessage;

    /**
     * Error description.
     */
    @JsonProperty("error-description")
    private String errorDescription;

    /**
     * Instance constructor.
     *
     * @param correlationId the failed request correlation id
     * @param timestamp     the error timestamp
     * @param type          the error type
     * @param message       the error message
     * @param description   the error description
     */
    @JsonCreator
    public MessageError(@JsonProperty(CORRELATION_ID) final String correlationId,
                        @JsonProperty(TIMESTAMP) final long timestamp,
                        @JsonProperty("error-type") final String type,
                        @JsonProperty("error-message") final String message,
                        @JsonProperty("error-description") final String description) {
        this.correlationId = firstNonNull(correlationId, DEFAULT_CORRELATION_ID);
        this.timestamp = timestamp;
        this.errorType = type;
        this.errorMessage = message;
        this.errorDescription = description;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return toStringHelper(this)
                .add(CORRELATION_ID, correlationId)
                .add(TIMESTAMP, timestamp)
                .add("error-type", errorType)
                .add("error-message", errorMessage)
                .add("error-description", errorDescription)
                .toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(correlationId, timestamp, errorType, errorMessage, errorDescription);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }

        MessageError that = (MessageError) object;
        return Objects.equals(this.correlationId, that.correlationId)
                && Objects.equals(this.errorType, that.errorType)
                && Objects.equals(this.errorMessage, that.errorMessage)
                && Objects.equals(this.errorDescription, that.errorDescription);
    }
}
