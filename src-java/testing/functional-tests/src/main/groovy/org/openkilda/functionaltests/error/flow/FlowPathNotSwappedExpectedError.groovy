package org.openkilda.functionaltests.error.flow

import org.openkilda.functionaltests.error.AbstractExpectedError

import org.springframework.http.HttpStatus

import java.util.regex.Pattern

class FlowPathNotSwappedExpectedError extends AbstractExpectedError {
    final static HttpStatus statusCode = HttpStatus.BAD_REQUEST
    final static String message = "Could not swap paths for flow"

    FlowPathNotSwappedExpectedError(Pattern descriptionPattern) {
        super(statusCode, message, descriptionPattern)
    }
    FlowPathNotSwappedExpectedError(HttpStatus statusCode, Pattern descriptionPattern) {
        super(statusCode, message, descriptionPattern)
    }
}
