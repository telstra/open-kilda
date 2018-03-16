package org.openkilda.messaging;

public class HeartBeat extends Message {

    public HeartBeat(long timestamp, String correlationId, Destination destination) {
        super(timestamp, correlationId, destination);
    }

    public HeartBeat(long timestamp, String correlationId) {
        super(timestamp, correlationId);
    }
}
