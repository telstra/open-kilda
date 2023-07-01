package org.openkilda.functionaltests.error.flow

import org.openkilda.functionaltests.error.AbstractExpectedError
import org.springframework.http.HttpStatus

import java.util.regex.Pattern

class FlowNotUpdatedWithConflictExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.CONFLICT
    final static String message = "Could not update flow"

    FlowNotUpdatedWithConflictExpectedError(Pattern descriptionPattern) {
        super(statusCode, message, descriptionPattern)
    }
}
