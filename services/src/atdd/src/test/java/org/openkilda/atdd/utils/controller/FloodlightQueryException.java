package org.openkilda.atdd.utils.controller;

import org.openkilda.atdd.utils.RestQueryException;

public class FloodlightQueryException extends RestQueryException {
    public FloodlightQueryException(Throwable cause) {
        this("Floodlight API call have failed", cause);
    }

    public FloodlightQueryException(Integer expect, Integer actual) {
        super(String.format("Floodlight API call complete with invalid status code %s, expect %s", actual, expect));
    }

    public FloodlightQueryException(String message, Throwable cause) {
        super(message, cause);
    }
}
