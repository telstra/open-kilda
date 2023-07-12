package org.openkilda.functionaltests.error


import org.springframework.http.HttpStatus

import java.util.regex.Pattern

class WrongLicenseKeyExpectedError extends AbstractExpectedError{
    final static HttpStatus statusCode = HttpStatus.BAD_REQUEST
    final static String message = "Invalid license key."
    final static Pattern descriptionPattern = ~/Invalid license key./

    WrongLicenseKeyExpectedError() {
        super(statusCode, message, descriptionPattern)
    }
}
