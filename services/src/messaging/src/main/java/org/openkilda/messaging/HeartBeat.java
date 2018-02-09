package org.openkilda.messaging;

import java.util.UUID;

public class HeartBeat extends Message {

    public HeartBeat(long timestamp, String correlationId, Destination destination) {
        super(timestamp, correlationId, destination);
    }

    public HeartBeat(long timestamp, String correlationId) {
        super(timestamp, correlationId);
    }

    public HeartBeat() {
        this(System.currentTimeMillis(), UUID.randomUUID().toString());
    }
}
