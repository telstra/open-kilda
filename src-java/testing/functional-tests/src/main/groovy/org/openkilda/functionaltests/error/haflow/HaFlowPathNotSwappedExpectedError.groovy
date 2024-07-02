package org.openkilda.functionaltests.error.haflow

import org.openkilda.functionaltests.error.AbstractExpectedError

import org.springframework.http.HttpStatus

import java.util.regex.Pattern

class HaFlowPathNotSwappedExpectedError extends AbstractExpectedError {
    final static String message = "Could not swap paths for flow"

    HaFlowPathNotSwappedExpectedError(HttpStatus statusCode, Pattern descriptionPattern){
        super(statusCode, message, descriptionPattern)
    }
}
