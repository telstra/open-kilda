package org.openkilda.functionaltests.error.yflow

import org.openkilda.functionaltests.error.AbstractExpectedError
import org.springframework.http.HttpStatus

import java.util.regex.Pattern

class YFlowNotCreatedExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.BAD_REQUEST
    final static String message = "Could not create y-flow"

    YFlowNotCreatedExpectedError(Pattern descriptionPattern) {
        super(statusCode, message, descriptionPattern)
    }
}
