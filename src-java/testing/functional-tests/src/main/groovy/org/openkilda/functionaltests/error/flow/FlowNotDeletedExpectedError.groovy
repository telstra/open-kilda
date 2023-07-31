package org.openkilda.functionaltests.error.flow

import org.openkilda.functionaltests.error.AbstractExpectedError
import org.springframework.http.HttpStatus

import java.util.regex.Pattern

class FlowNotDeletedExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.BAD_REQUEST
    final static String message = "Could not delete flow"

    FlowNotDeletedExpectedError(Pattern descriptionPattern) {
        super(statusCode, message, descriptionPattern)
    }
}
