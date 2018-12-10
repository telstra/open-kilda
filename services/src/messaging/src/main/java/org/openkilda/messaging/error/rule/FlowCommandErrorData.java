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

package org.openkilda.messaging.error.rule;

import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.UUID;

/**
 * Defines the payload of a message representing a flow command error.
 */
@Value
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowCommandErrorData extends ErrorData {
    @JsonProperty("flow_id")
    private String flowId;
    @JsonProperty("cookie")
    private Long cookie;
    @JsonProperty("transaction_id")
    private UUID transactionId;

    @JsonCreator
    public FlowCommandErrorData(@JsonProperty("flow_id") String flowId,
                                @JsonProperty("cookie") Long cookie,
                                @JsonProperty("transaction_id") UUID transactionId,
                                @JsonProperty("error-type") final ErrorType errorType,
                                @JsonProperty("error-message") final String errorMessage,
                                @JsonProperty("error-description") final String errorDescription) {
        super(errorType, errorMessage, errorDescription);

        this.flowId = flowId;
        this.cookie = cookie;
        this.transactionId = transactionId;
    }
}
