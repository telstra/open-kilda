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

package org.openkilda.floodlight.error;

import org.openkilda.messaging.error.ErrorType;

import java.util.UUID;

/**
 * {@code FlowCommandException} indicates a processing failure for a flow command.
 */
public class FlowCommandException extends Exception {
    private final String flowId;
    private final Long cookie;
    private final UUID transactionId;
    private final ErrorType type;

    public FlowCommandException(String flowId, Long cookie, UUID transactionId,
                                ErrorType type, SwitchOperationException cause) {
        super(cause);
        this.flowId = flowId;
        this.cookie = cookie;
        this.transactionId = transactionId;
        this.type = type;
    }

    public FlowCommandException(String flowId, Long cookie, UUID transactionId,
                                ErrorType type, String message) {
        super(message);
        this.flowId = flowId;
        this.cookie = cookie;
        this.transactionId = transactionId;
        this.type = type;
    }

    public String getFlowId() {
        return flowId;
    }

    public Long getCookie() {
        return cookie;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public ErrorType getErrorType() {
        return type;
    }
}
