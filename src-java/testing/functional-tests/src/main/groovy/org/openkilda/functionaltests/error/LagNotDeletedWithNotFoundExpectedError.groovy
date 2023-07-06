package org.openkilda.functionaltests.error


import org.springframework.http.HttpStatus

import java.util.regex.Pattern

class LagNotDeletedWithNotFoundExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.NOT_FOUND
    final static String message = "Error during LAG delete"

    LagNotDeletedWithNotFoundExpectedError(Pattern descriptionPattern) {
        super(statusCode, message, descriptionPattern)
    }
}
