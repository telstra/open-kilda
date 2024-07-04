package org.openkilda.functionaltests.error.flow

import org.openkilda.functionaltests.error.AbstractExpectedError
import org.springframework.http.HttpStatus

import java.util.regex.Pattern


class FlowNotFoundExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.NOT_FOUND

    FlowNotFoundExpectedError(String message, Pattern descriptionPattern) {
        super(statusCode, message, descriptionPattern)
    }

    FlowNotFoundExpectedError(String flowId) {
        super(statusCode, 'Flow not found', ~/${String.format("Flow %s not found", "\'${flowId}\'")}/)
    }
}
