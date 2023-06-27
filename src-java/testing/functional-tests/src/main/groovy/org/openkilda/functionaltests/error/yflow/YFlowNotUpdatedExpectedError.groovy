package org.openkilda.functionaltests.error.yflow

import org.openkilda.functionaltests.error.AbstractExpectedError
import org.springframework.http.HttpStatus

import java.util.regex.Pattern

class YFlowNotUpdatedExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.BAD_REQUEST
    final static String message = "Could not update y-flow"

    YFlowNotUpdatedExpectedError(Pattern descriptionPattern) {
        super(statusCode, message, descriptionPattern)
    }

    YFlowNotUpdatedExpectedError(String message, Pattern descriptionPattern) {
        super(statusCode, message, descriptionPattern)
    }
}
