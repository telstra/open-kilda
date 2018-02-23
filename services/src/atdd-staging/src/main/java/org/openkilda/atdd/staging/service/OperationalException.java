package org.openkilda.atdd.staging.service;

public class OperationalException extends Exception {
    public OperationalException(String s) {
        super(s);
    }

    public OperationalException(String s, Throwable throwable) {
        super(s, throwable);
    }
}
