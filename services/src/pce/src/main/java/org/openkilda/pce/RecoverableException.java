package org.openkilda.pce;

public class RecoverableException extends Exception {
    public RecoverableException() {
    }

    public RecoverableException(String message) {
        super(message);
    }

    public RecoverableException(String message, Throwable cause) {
        super(message, cause);
    }
}
