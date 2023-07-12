package org.openkilda.functionaltests.error

import org.openkilda.model.SwitchId
import org.springframework.http.HttpStatus

import java.util.regex.Pattern

class PortNotFoundExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.NOT_FOUND
    final static String messagePattern = "Port not found: 'Port FSM not found (%s_%d).'"

    PortNotFoundExpectedError(SwitchId switchId, int port, Pattern description) {
        super(statusCode, String.format(messagePattern, switchId, port), description)
    }
}
