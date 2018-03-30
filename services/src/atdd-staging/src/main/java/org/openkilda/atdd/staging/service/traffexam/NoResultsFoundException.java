package org.openkilda.atdd.staging.service.traffexam;

public class NoResultsFoundException extends RuntimeException {
    public NoResultsFoundException(String message) {
        super(message);
    }
}
