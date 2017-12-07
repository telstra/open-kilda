package org.openkilda.wfm;

public class StreamNameCollisionException extends NameCollisionException {
    public StreamNameCollisionException() {
        this("Stream ID's collision");
    }

    private StreamNameCollisionException(String s) {
        super(s);
    }
}
