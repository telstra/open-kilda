package org.openkilda.functionaltests.error

import org.springframework.http.HttpStatus

class SwitchIsInIllegalStateExpectedError extends AbstractExpectedError {
    final static HttpStatus statusCode = HttpStatus.BAD_REQUEST

    SwitchIsInIllegalStateExpectedError(String message) {
        super(statusCode, message, ~/Switch is in illegal state/)

    }
}
