package org.openkilda.functionaltests.error.flow

import org.openkilda.functionaltests.error.AbstractExpectedError
import org.springframework.http.HttpStatus

import java.util.regex.Pattern

class FlowNotReroutedExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.NOT_FOUND
    final static String message = "Could not reroute flow"

    FlowNotReroutedExpectedError(Pattern descriptionPattern) {
        super(statusCode, message, descriptionPattern)
    }
}
