package org.openkilda.functionaltests.error.flow

import org.openkilda.functionaltests.error.AbstractExpectedError
import org.springframework.http.HttpStatus

import java.util.regex.Pattern

class FlowNotUpdatedExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.BAD_REQUEST

    FlowNotUpdatedExpectedError(Pattern descriptionPattern) {
        super(statusCode, "Could not update flow", descriptionPattern)
    }

    FlowNotUpdatedExpectedError(String message, Pattern descriptionPattern) {
        super(statusCode, message, descriptionPattern)
    }
}
