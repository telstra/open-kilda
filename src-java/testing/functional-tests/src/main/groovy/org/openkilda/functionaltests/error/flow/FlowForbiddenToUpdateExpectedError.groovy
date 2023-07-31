package org.openkilda.functionaltests.error.flow

import org.openkilda.functionaltests.error.AbstractExpectedError
import org.springframework.http.HttpStatus

import java.util.regex.Pattern

class FlowForbiddenToUpdateExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.FORBIDDEN
    final static String message = "Could not update flow"

    FlowForbiddenToUpdateExpectedError(Pattern descriptionPattern) {
        super(statusCode, message, descriptionPattern)
    }
}
