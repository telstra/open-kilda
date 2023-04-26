package org.openkilda.functionaltests.error.flow

import org.openkilda.functionaltests.error.AbstractExpectedError
import org.springframework.http.HttpStatus

import java.util.regex.Pattern

class FlowNotCreatedWithConflictExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.CONFLICT
    final static String message = "Could not create flow"

    FlowNotCreatedWithConflictExpectedError(Pattern descriptionPattern) {
        super(statusCode, message, descriptionPattern)
    }
}
