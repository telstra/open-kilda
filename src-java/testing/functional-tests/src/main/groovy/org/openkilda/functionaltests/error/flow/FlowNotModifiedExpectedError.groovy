package org.openkilda.functionaltests.error.flow

import org.openkilda.functionaltests.error.AbstractExpectedError
import org.springframework.http.HttpStatus

class FlowNotModifiedExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.BAD_REQUEST
    final static String message = "Could not modify flow"
    final static String description = "%s is a sub-flow of a y-flow. Operations on sub-flows are forbidden."

    FlowNotModifiedExpectedError(String subflowId) {
        super(statusCode, message, ~/${String.format(description, subflowId)}/)
    }
}
