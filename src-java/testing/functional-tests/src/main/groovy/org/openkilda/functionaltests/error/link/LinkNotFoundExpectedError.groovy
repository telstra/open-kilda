package org.openkilda.functionaltests.error.link

import org.openkilda.functionaltests.error.AbstractExpectedError

import org.springframework.http.HttpStatus


class LinkNotFoundExpectedError extends AbstractExpectedError {
    final static HttpStatus statusCode = HttpStatus.NOT_FOUND

    LinkNotFoundExpectedError(String message){
        super(statusCode, message, ~/ISL was not found./)
    }
}
