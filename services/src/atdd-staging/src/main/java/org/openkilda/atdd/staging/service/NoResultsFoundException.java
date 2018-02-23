package org.openkilda.atdd.staging.service;

public class NoResultsFoundException extends Exception {
    public NoResultsFoundException(String message) {
        super(message);
    }
}
