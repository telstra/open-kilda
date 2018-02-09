package org.openkilda.atdd.floodlight;

public class KafkaBreakException extends Exception {
    public KafkaBreakException(String s) {
        super(s);
    }

    public KafkaBreakException(String s, Throwable throwable) {
        super(s, throwable);
    }
}
