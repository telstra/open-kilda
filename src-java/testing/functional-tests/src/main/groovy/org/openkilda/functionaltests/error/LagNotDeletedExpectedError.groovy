package org.openkilda.functionaltests.error


import org.springframework.http.HttpStatus

import java.util.regex.Pattern

class LagNotDeletedExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.BAD_REQUEST
    final static String message = "Error during LAG delete"

    LagNotDeletedExpectedError(Pattern descriptionPattern) {
        super(statusCode, message, descriptionPattern)
    }
}
