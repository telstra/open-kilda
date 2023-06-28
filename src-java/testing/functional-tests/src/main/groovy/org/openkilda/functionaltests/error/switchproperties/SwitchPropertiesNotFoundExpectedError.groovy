package org.openkilda.functionaltests.error.switchproperties

import org.openkilda.functionaltests.error.AbstractExpectedError
import org.openkilda.model.SwitchId
import org.springframework.http.HttpStatus

import java.util.regex.Pattern

class SwitchPropertiesNotFoundExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.NOT_FOUND
    final static String messagePattern = "Switch properties for switch id '%s' not found."

    SwitchPropertiesNotFoundExpectedError(SwitchId switchId, Pattern descriptionPattern) {
        super(statusCode, String.format(messagePattern, switchId), descriptionPattern)
    }
}
