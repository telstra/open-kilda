package org.openkilda.wfm.error;

public class UnsupportedActionException extends Exception {
    private final String action;

    public UnsupportedActionException(String action) {
        super(String.format("Try to \"call\" unsupported action \"%s\"", action));
        this.action = action;
    }

    public String getAction() {
        return action;
    }
}
