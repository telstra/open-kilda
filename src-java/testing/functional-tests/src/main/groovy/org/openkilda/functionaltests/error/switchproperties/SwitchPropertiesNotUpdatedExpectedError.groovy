package org.openkilda.functionaltests.error.switchproperties

import org.openkilda.functionaltests.error.AbstractExpectedError
import org.springframework.http.HttpStatus

import java.util.regex.Pattern


class SwitchPropertiesNotUpdatedExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.BAD_REQUEST
    final static Pattern descriptionPattern = ~/Failed to update switch properties./

    SwitchPropertiesNotUpdatedExpectedError(String message) {
        super(statusCode, message, descriptionPattern)
    }

    SwitchPropertiesNotUpdatedExpectedError(String message, Pattern descriptionPattern) {
        super(statusCode, message, descriptionPattern)
    }
}
