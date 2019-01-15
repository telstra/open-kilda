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
    protected String correlationId;

    /**
     * The timestamp.
     */
    protected long timestamp;

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
        super(errorType, errorMessage, errorDescription);
        this.correlationId = correlationId;
        this.timestamp = timestamp;
    }

    /**
     * Instance constructor.
     *
     * @param errorType        error type
     * @param errorMessage     error message
     * @param errorDescription error description
     */
    public MessageException(ErrorType errorType, String errorMessage, String errorDescription) {
        super(errorType, errorMessage, errorDescription);
    }

    /**
     * Instance constructor.
     *
     * @param message the {@link MessageError}
     */
    public MessageException(ErrorMessage message) {
        super(message.getData().getErrorType(),
                message.getData().getErrorMessage(),
                message.getData().getErrorDescription());
        this.correlationId = message.getCorrelationId();
        this.timestamp = message.getTimestamp();
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
