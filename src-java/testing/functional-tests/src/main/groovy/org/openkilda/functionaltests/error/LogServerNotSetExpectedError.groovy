package org.openkilda.functionaltests.error


import org.springframework.http.HttpStatus

import java.util.regex.Pattern

class LogServerNotSetExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.BAD_REQUEST
    final static Pattern descriptionPattern = ~/Log server address is not updated/

    LogServerNotSetExpectedError(String message) {
        super(statusCode, message, descriptionPattern)
    }
}
