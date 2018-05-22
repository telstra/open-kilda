package org.openkilda.wfm.error;

public class MessageException extends Exception {
    public MessageException() {
        super("Exception raised parsing message");
    }

    public MessageException(String s) {
        super(s);
    }
}
