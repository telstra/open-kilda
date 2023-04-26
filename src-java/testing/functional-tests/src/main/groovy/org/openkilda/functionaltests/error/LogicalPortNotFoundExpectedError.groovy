package org.openkilda.functionaltests.error


import org.springframework.http.HttpStatus

import java.util.regex.Pattern


class LogicalPortNotFoundExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.NOT_FOUND
    final static String message = "Provided logical port does not exist."

    LogicalPortNotFoundExpectedError(Pattern description) {
        super(statusCode, message, description)
    }
}
