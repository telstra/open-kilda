package org.openkilda.wfm.error;

import org.apache.storm.tuple.Tuple;

public class MessageFormatException extends AbstractException {
    private Tuple tuple;

    public MessageFormatException(Tuple tuple, Throwable throwable) {
        super("Invalid input message/tuple", throwable);

        this.tuple = tuple;
    }

    public MessageFormatException(String s, Throwable throwable) {
        super(s, throwable);
    }

    public Tuple getTuple() {
        return tuple;
    }
}
