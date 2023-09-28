package org.openkilda.functionaltests.error.yflow

import org.openkilda.functionaltests.error.AbstractExpectedError

import org.springframework.http.HttpStatus

import java.util.regex.Pattern

class YFlowRerouteExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.NOT_FOUND
    final static String message = "Could not reroute y-flow"

    YFlowRerouteExpectedError(Pattern descriptionPattern) {
        super(statusCode, message, descriptionPattern)
    }
}
