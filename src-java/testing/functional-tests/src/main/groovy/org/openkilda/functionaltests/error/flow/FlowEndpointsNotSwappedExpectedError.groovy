package org.openkilda.functionaltests.error.flow

import org.openkilda.functionaltests.error.AbstractExpectedError
import org.springframework.http.HttpStatus

import java.util.regex.Pattern

class FlowEndpointsNotSwappedExpectedError extends AbstractExpectedError{
    final static String message = "Could not swap endpoints"

    FlowEndpointsNotSwappedExpectedError(Pattern descriptionPattern) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message, descriptionPattern)
    }

    FlowEndpointsNotSwappedExpectedError(HttpStatus statusCode, Pattern descriptionPattern) {
        super(statusCode, message, descriptionPattern)
    }
}
