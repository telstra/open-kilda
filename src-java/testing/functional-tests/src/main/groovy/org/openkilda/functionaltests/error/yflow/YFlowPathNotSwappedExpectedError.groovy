package org.openkilda.functionaltests.error.yflow

import org.openkilda.functionaltests.error.AbstractExpectedError

import org.springframework.http.HttpStatus

import java.util.regex.Pattern

class YFlowPathNotSwappedExpectedError extends AbstractExpectedError {
    final static String message = "Could not swap y-flow paths"

    YFlowPathNotSwappedExpectedError(HttpStatus status, Pattern descriptionPattern) {
        super(status, message, descriptionPattern)
    }

}
