package org.openkilda.functionaltests.error.yflow

import org.openkilda.functionaltests.error.AbstractExpectedError
import org.springframework.http.HttpStatus

class YFlowNotFoundExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.NOT_FOUND
    final static String description = "Y-flow %s not found"

    YFlowNotFoundExpectedError(String message, String yFlowid) {
        super(statusCode, message, ~/${String.format(description, yFlowid)}/)
    }
}
