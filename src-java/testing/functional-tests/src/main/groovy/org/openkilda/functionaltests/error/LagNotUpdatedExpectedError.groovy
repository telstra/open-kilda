package org.openkilda.functionaltests.error

import org.openkilda.model.SwitchId
import org.springframework.http.HttpStatus

import java.util.regex.Pattern

class LagNotUpdatedExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.BAD_REQUEST
    final static String messagePattern = "Error processing LAG logical port #%d on %s update request"

    LagNotUpdatedExpectedError(SwitchId switchId, int port, Pattern descriptionPattern) {
        super(statusCode, String.format(messagePattern, port, switchId), descriptionPattern)
    }
}
