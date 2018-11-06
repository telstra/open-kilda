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

import org.openkilda.messaging.error.ErrorData;
import org.openkilda.messaging.error.ErrorType;

public class FlowCommandException extends Exception {
    private final String flowId;
    private final ErrorType type;

    public FlowCommandException(String flowId, ErrorType type, SwitchOperationException cause) {
        super(cause);
        this.flowId = flowId;
        this.type = type;
    }

    public FlowCommandException(String flowId, ErrorType type, String message) {
        super(message);
        this.flowId = flowId;
        this.type = type;
    }

    public ErrorData makeErrorResponse() {
        String message = getCause() != null ? getCause().getMessage() : getMessage();
        return new ErrorData(getType(), message, getFlowId());
    }

    public String getFlowId() {
        return flowId;
    }

    public ErrorType getType() {
        return type;
    }
}
