package org.openkilda.functionaltests.error


import org.springframework.http.HttpStatus

import java.util.regex.Pattern

class InvalidRequestParametersExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.BAD_REQUEST

    InvalidRequestParametersExpectedError(String message, Pattern descriptionPattern) {
        super(statusCode, message, descriptionPattern)
    }
}
