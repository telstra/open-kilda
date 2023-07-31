package org.openkilda.functionaltests.error.flow

import org.openkilda.functionaltests.error.AbstractExpectedError
import org.springframework.http.HttpStatus

import java.util.regex.Pattern

class FlowForbiddenToDeleteExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.FORBIDDEN
    final static String message = "Could not delete flow"

    FlowForbiddenToDeleteExpectedError(Pattern descriptionPattern) {
        super(statusCode, message, descriptionPattern)
    }
}
