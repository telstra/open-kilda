package org.openkilda.functionaltests.error.haflow

import org.openkilda.functionaltests.error.AbstractExpectedError
import org.springframework.http.HttpStatus

import java.util.regex.Pattern

class HaFlowNotCreatedWithConflictExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.CONFLICT
    final static String message = "Could not create ha-flow"

    HaFlowNotCreatedWithConflictExpectedError(Pattern descriptionPattern) {
        super(statusCode, message, descriptionPattern)
    }
}
