package org.openkilda.functionaltests.error

import org.openkilda.model.SwitchId
import org.springframework.http.HttpStatus

import java.util.regex.Pattern


class SwitchNotFoundExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.NOT_FOUND
    final static String messagePattern = "Switch %s not found."
    final static Pattern descriptionPattern = ~/Switch was not found./

    SwitchNotFoundExpectedError(SwitchId switchId) {
        super(statusCode, String.format(messagePattern, switchId), descriptionPattern)
    }

    SwitchNotFoundExpectedError(SwitchId switchId, Pattern descriptionPattern) {
        super(statusCode, String.format(messagePattern, switchId), descriptionPattern)
    }

    SwitchNotFoundExpectedError(String message, Pattern descriptionPattern) {
        super(statusCode, message, descriptionPattern)
    }
}
