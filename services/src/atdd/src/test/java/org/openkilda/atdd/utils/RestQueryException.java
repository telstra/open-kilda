package org.openkilda.atdd.utils;

public class RestQueryException extends Exception {
    public RestQueryException(String s) {
        super(s);
    }

    public RestQueryException(Throwable throwable) {
        this("REST API call have failed", throwable);
    }

    public RestQueryException(String message, Throwable cause) {
        super(message, cause);
    }
}
