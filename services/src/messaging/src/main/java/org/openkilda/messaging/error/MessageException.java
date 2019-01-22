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

import org.openkilda.model.LogLevel;

/**
 * The exception for notifying errors.
 */
public class MessageException extends CacheException {
    /**
     * The constant serialVersionUID.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The correlation id.
     */
    private final String correlationId;

    /**
     * The timestamp.
     */
    private final long timestamp;

    /**
     * Instance constructor.
     *
     * @param correlationId    error correlation id
     * @param timestamp        error timestamp
     * @param errorType        error type
     * @param errorMessage     error message
     * @param errorDescription error description
     */
    public MessageException(String correlationId, long timestamp, ErrorType errorType,
                            String errorMessage, String errorDescription) {
        this(correlationId, timestamp, errorType, errorMessage, errorDescription, LogLevel.ERROR);
    }

    /**
     * Instance constructor.
     *
     * @param correlationId    error correlation id
     * @param timestamp        error timestamp
     * @param errorType        error type
     * @param errorMessage     error message
     * @param errorDescription error description
     * @param logLevel         log level
     */
    public MessageException(String correlationId, long timestamp, ErrorType errorType,
                            String errorMessage, String errorDescription, LogLevel logLevel) {
        super(errorType, errorMessage, errorDescription, logLevel);
        this.correlationId = correlationId;
        this.timestamp = timestamp;
    }

    /**
     * Instance constructor.
     *
     * @param message the {@link MessageError}
     */
    public MessageException(ErrorMessage message) {
        this(message.getCorrelationId(),
                message.getTimestamp(),
                message.getData().getErrorType(),
                message.getData().getErrorMessage(),
                message.getData().getErrorDescription());
    }

    /**
     * Returns error correlation id.
     *
     * @return error correlation id
     */
    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * Returns error timestamp.
     *
     * @return error timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }
}
