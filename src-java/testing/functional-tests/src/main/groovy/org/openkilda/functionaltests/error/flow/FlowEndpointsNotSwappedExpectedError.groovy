package org.openkilda.functionaltests.error.flow

import org.openkilda.functionaltests.error.AbstractExpectedError
import org.springframework.http.HttpStatus

import java.util.regex.Pattern

class FlowEndpointsNotSwappedExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.INTERNAL_SERVER_ERROR
    final static String message = "Could not swap endpoints"

    FlowEndpointsNotSwappedExpectedError(Pattern descriptionPattern) {
        super(statusCode, message, descriptionPattern)
    }
}
