package org.openkilda.functionaltests.error.link

import org.openkilda.functionaltests.error.AbstractExpectedError
import org.springframework.http.HttpStatus

import java.util.regex.Pattern

class LinkBfdNotSetExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.BAD_REQUEST
    final static String message = "Invalid BFD properties"

    LinkBfdNotSetExpectedError(Pattern descriptionPattern) {
        super(statusCode, message, descriptionPattern)
    }
}
