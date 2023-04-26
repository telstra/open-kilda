package org.openkilda.functionaltests.error.flow

import org.openkilda.functionaltests.error.AbstractExpectedError
import org.springframework.http.HttpStatus

import java.util.regex.Pattern

class FlowNotValidatedExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.UNPROCESSABLE_ENTITY
    final static String message = "Could not validate flow: Could not validate flow"

    FlowNotValidatedExpectedError(Pattern descriptionPattern) {
        super(statusCode, message, descriptionPattern)
    }
}
