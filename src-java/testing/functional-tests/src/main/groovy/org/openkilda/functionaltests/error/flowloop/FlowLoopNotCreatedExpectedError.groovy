package org.openkilda.functionaltests.error.flowloop

import org.openkilda.functionaltests.error.AbstractExpectedError
import org.springframework.http.HttpStatus

import java.util.regex.Pattern

class FlowLoopNotCreatedExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.UNPROCESSABLE_ENTITY
    final static String messagePattern = "Can't create flow loop on '%s'"

    FlowLoopNotCreatedExpectedError(String flowId, Pattern descriptionPattern) {
        super(statusCode, String.format(messagePattern, flowId), descriptionPattern)
    }
}
