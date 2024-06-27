package org.openkilda.functionaltests.error

import org.springframework.http.HttpStatus

class MissingServletRequestParameterException extends AbstractExpectedError {
    final static HttpStatus statusCode = HttpStatus.BAD_REQUEST

    MissingServletRequestParameterException(String message){
        super(statusCode, message, ~/MissingServletRequestParameterException/)
    }
}
