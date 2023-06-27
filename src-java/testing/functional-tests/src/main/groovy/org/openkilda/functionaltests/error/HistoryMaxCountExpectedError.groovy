package org.openkilda.functionaltests.error


import org.springframework.http.HttpStatus

import java.util.regex.Pattern

class HistoryMaxCountExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.BAD_REQUEST
    final static String messagePattern = "Invalid `max_count` argument '%d'."
    final static Pattern descriptionPattern = ~/`max_count` argument must be positive./

    HistoryMaxCountExpectedError(int maxCount) {
        super(statusCode, String.format(messagePattern, maxCount), descriptionPattern)
    }
}
