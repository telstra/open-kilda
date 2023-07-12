package org.openkilda.functionaltests.error.flow

import org.openkilda.functionaltests.error.AbstractExpectedError
import org.springframework.http.HttpStatus

import java.util.regex.Pattern


class FlowNotFoundExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.NOT_FOUND
    final static String messagePattern = "Flow %s not found"

    FlowNotFoundExpectedError(String flowId, Pattern descriptionPattern) {
        super(statusCode, String.format(messagePattern, flowId), descriptionPattern)
    }

    FlowNotFoundExpectedError(String flowId) {
        super(statusCode, 'Flow not found', ~/${String.format(messagePattern, "\'${flowId}\'")}/)
    }
}
