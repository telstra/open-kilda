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

import static com.google.common.base.MoreObjects.toStringHelper;
import static org.openkilda.messaging.Utils.CORRELATION_ID;
import static org.openkilda.messaging.Utils.DESTINATION;
import static org.openkilda.messaging.Utils.LOG_LEVEL;
import static org.openkilda.messaging.Utils.PAYLOAD;
import static org.openkilda.messaging.Utils.TIMESTAMP;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.model.LogLevel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Class represents error message.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(value = {
        DESTINATION,
        PAYLOAD,
        TIMESTAMP,
        CORRELATION_ID,
        LOG_LEVEL})
public class ErrorMessage extends Message {
    /**
     * Serialization version number constant.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Data of the error message.
     */
    @JsonProperty(PAYLOAD)
    private ErrorData data;

    @JsonProperty(LOG_LEVEL)
    private LogLevel logLevel;

    /**
     * Instance constructor.
     *
     * @param data          error message payload
     * @param timestamp     timestamp value
     * @param correlationId message correlation id
     * @param destination   message destination
     */
    public ErrorMessage(@JsonProperty(PAYLOAD) final ErrorData data,
                        @JsonProperty(TIMESTAMP) final long timestamp,
                        @JsonProperty(CORRELATION_ID) final String correlationId,
                        @JsonProperty(DESTINATION) final Destination destination) {
        this(data, timestamp, correlationId, destination, LogLevel.ERROR);
    }

    /**
     * Instance constructor.
     *
     * @param data          error message payload
     * @param timestamp     timestamp value
     * @param correlationId message correlation id
     * @param destination   message destination
     */
    @JsonCreator
    public ErrorMessage(@JsonProperty(PAYLOAD) final ErrorData data,
                        @JsonProperty(TIMESTAMP) final long timestamp,
                        @JsonProperty(CORRELATION_ID) final String correlationId,
                        @JsonProperty(DESTINATION) final Destination destination,
                        @JsonProperty(LOG_LEVEL) final LogLevel logLevel) {
        super(timestamp, correlationId, destination);
        setData(data);
        this.logLevel = logLevel;
    }


    /**
     * Instance constructor.
     *
     * @param data          error message payload
     * @param timestamp     timestamp value
     * @param correlationId message correlation id
     */
    public ErrorMessage(final ErrorData data,
                        final long timestamp,
                        final String correlationId) {
        super(timestamp, correlationId);
        setData(data);
        this.logLevel = LogLevel.ERROR;
    }

    /**
     * Returns payload of the error message.
     *
     * @return error message payload
     */
    public ErrorData getData() {
        return data;
    }

    /**
     * Sets payload of the error message.
     *
     * @param data error message payload
     */
    public void setData(final ErrorData data) {
        this.data = data;
    }

    /**
     * Returns log level which should (not must) be used to log this exception.
     *
     * @return log level {@link LogLevel}
     */
    public LogLevel getLogLevel() {
        return logLevel;
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
                .add(PAYLOAD, data)
                .add(LOG_LEVEL, logLevel)
                .toString();
    }
}
