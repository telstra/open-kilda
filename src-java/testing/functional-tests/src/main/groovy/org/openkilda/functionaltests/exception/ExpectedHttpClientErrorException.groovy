package org.openkilda.functionaltests.exception

import org.openkilda.messaging.error.MessageError
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException

import java.util.regex.Pattern

class ExpectedHttpClientErrorException {
    HttpStatus statusCode
    Pattern messagePattern

    ExpectedHttpClientErrorException(HttpStatus statusCode, Pattern messagePattern) {
        this.statusCode = statusCode
        this.messagePattern = messagePattern
    }

    boolean equals(other) {
        if (other == null) {
            return false
        }
        if (other.class == HttpClientErrorException.class) {
            return other.statusCode == statusCode &&
                    other.responseBodyAsString.to(MessageError).errorDescription =~ messagePattern
        }
        if (other.class == this.class) {
            return other.statusCode == statusCode &&
                    other.messagePattern == messagePattern
        }
        return false
    }

    int hashCode() {
        int hash = 7
        hash = 31 * hash + (statusCode == null ? 0 : statusCode.hashCode())
        hash = 31 * hash + (messagePattern == null ? 0 : messagePattern.hashCode())
        return hash
    }
}
