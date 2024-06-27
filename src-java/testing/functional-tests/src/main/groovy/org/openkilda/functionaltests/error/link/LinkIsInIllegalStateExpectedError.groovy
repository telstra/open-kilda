package org.openkilda.functionaltests.error.link

import org.openkilda.functionaltests.error.AbstractExpectedError

import org.springframework.http.HttpStatus

class LinkIsInIllegalStateExpectedError extends AbstractExpectedError {
    final static HttpStatus statusCode = HttpStatus.BAD_REQUEST

    LinkIsInIllegalStateExpectedError(String message) {
        super(statusCode, message, ~/ISL is in illegal state./)
    }
}
