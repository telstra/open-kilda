package org.openkilda.functionaltests.error


import org.springframework.http.HttpStatus

import java.util.regex.Pattern


class NonExistingEncapsulationTypeExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.BAD_REQUEST
    final static String messagePattern = "No enum constant org.openkilda.model.FlowEncapsulationType.%s"
    final static Pattern descriptionPattern = ~/Update kilda configuration./

    NonExistingEncapsulationTypeExpectedError(String encapsulationType) {
        super(statusCode, String.format(messagePattern, encapsulationType), descriptionPattern)
    }
}
