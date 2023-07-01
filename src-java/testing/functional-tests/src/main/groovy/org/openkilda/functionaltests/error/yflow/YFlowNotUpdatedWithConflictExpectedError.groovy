package org.openkilda.functionaltests.error.yflow

import org.openkilda.functionaltests.error.AbstractExpectedError
import org.springframework.http.HttpStatus

import java.util.regex.Pattern

class YFlowNotUpdatedWithConflictExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.CONFLICT
    final static String message = "Could not update y-flow"

    YFlowNotUpdatedWithConflictExpectedError(Pattern descriptionPattern) {
        super(statusCode, message, descriptionPattern)
    }
}
