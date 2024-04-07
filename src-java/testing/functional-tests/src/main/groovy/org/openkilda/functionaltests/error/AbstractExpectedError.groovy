package org.openkilda.functionaltests.error

import org.openkilda.messaging.error.MessageError
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpStatusCodeException

import java.util.regex.Pattern

abstract class AbstractExpectedError extends HttpClientErrorException{
    HttpStatus statusCode
    String message
    Pattern descriptionPattern

    AbstractExpectedError(HttpStatus statusCode, String message, Pattern messagePattern) {
        super(statusCode)
        this.statusCode = statusCode
        this.message = message
        this.descriptionPattern = messagePattern
    }

    boolean matches(HttpStatusCodeException exception) {
        MessageError messageError = exception.responseBodyAsString.to(MessageError)
        assert exception.statusCode == this.statusCode
        assert messageError.getErrorMessage() == this.message
        assert messageError.errorDescription =~ this.descriptionPattern
        return true
    }

    int hashCode() {
        int hash = 7
        hash = 31 * hash + (statusCode == null ? 0 : statusCode.hashCode())
        hash = 17 * hash + (message == null ? 0 : message.hashCode())
        hash = 31 * hash + (descriptionPattern == null ? 0 : descriptionPattern.hashCode())
        return hash
    }
}
