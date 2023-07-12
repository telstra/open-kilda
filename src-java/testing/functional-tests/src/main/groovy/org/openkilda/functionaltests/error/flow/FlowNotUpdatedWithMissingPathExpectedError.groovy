package org.openkilda.functionaltests.error.flow

import org.openkilda.functionaltests.error.AbstractExpectedError
import org.springframework.http.HttpStatus

import java.util.regex.Pattern

class FlowNotUpdatedWithMissingPathExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.NOT_FOUND
    final static String message = "Could not update flow"

    FlowNotUpdatedWithMissingPathExpectedError(Pattern descriptionPattern) {
        super(statusCode, message, descriptionPattern)
    }
}
