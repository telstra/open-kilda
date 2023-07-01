package org.openkilda.functionaltests.error


import org.springframework.http.HttpStatus

import java.util.regex.Pattern

class PinnedFlowNotReroutedExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.UNPROCESSABLE_ENTITY
    final static String message = "Could not reroute flow"
    final static Pattern descriptionPattern = ~/Can\'t reroute pinned flow/

    PinnedFlowNotReroutedExpectedError() {
        super(statusCode, message, descriptionPattern)
    }
}
