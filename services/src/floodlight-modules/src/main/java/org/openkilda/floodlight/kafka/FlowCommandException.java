package org.openkilda.floodlight.kafka;

import org.openkilda.floodlight.switchmanager.SwitchOperationException;
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

    public ErrorData makeErrorResponse() {
        return new ErrorData(getType(), getCause().getMessage(), getFlowId());
    }

    public String getFlowId() {
        return flowId;
    }

    public ErrorType getType() {
        return type;
    }
}
