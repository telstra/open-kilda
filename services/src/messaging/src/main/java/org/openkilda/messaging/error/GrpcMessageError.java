/* Copyright 2019 Telstra Open Source
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

import static org.openkilda.messaging.Utils.TIMESTAMP;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * The class represents error response.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode(exclude = "errorType")
public class GrpcMessageError implements Serializable {
    /**
     * The Constant serialVersionUID.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The error timestamp.
     */
    @JsonProperty(TIMESTAMP)
    private long timestamp;

    /**
     * The GRPC response error code.
     */
    @JsonProperty("error-code")
    private long errorCode;

    /**
     * Error message.
     */
    @JsonProperty("error-message")
    private String errorMessage;

    /**
     * Error type.
     */
    private String errorType;

    /**
     * Constructs a Error message.
     *
     * @param timestamp the error timestamp.
     * @param errorCode the error code.
     * @param errorMessage the error message.
     */
    @JsonCreator
    public GrpcMessageError(
            @JsonProperty(TIMESTAMP) long timestamp,
            @JsonProperty("error-code") long errorCode,
            @JsonProperty("error-message") String errorMessage) {
        this.timestamp = timestamp;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    /**
     * Constructs a Error message.
     *
     * @param timestamp the error timestamp.
     * @param errorCode the error code.
     * @param errorMessage the error message.
     * @param errorType the error type.
     */
    public GrpcMessageError(long timestamp, long errorCode, String errorMessage, String errorType) {
        this.timestamp = timestamp;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.errorType = errorType;
    }
}
