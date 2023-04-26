package org.openkilda.functionaltests.error.haflow

import org.openkilda.functionaltests.error.AbstractExpectedError
import org.springframework.http.HttpStatus

import java.util.regex.Pattern

class HaFlowNotFoundExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.NOT_FOUND
    final static String message = "Couldn't find HA-flow"

    HaFlowNotFoundExpectedError(Pattern descriptionPattern) {
        super(statusCode, message, descriptionPattern)
    }
}
