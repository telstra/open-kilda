package org.openkilda.floodlight.kafka;

import org.openkilda.floodlight.utils.CorrelationContext;
import org.openkilda.floodlight.utils.CorrelationContext.CorrelationContextClosable;
import org.openkilda.messaging.Message;

import java.util.TimerTask;
import java.util.UUID;

public class HeartBeatAction extends TimerTask {
    private final Producer producer;
    private final String topic;

    public HeartBeatAction(Producer producer, String topic) {
        this.producer = producer;
        this.topic = topic;
    }

    @Override
    public void run() {
        // Create a new correlation context to process the heart beat.
        try (CorrelationContextClosable closable = CorrelationContext.create(UUID.randomUUID().toString())) {

            Message message = new org.openkilda.messaging.HeartBeat(System.currentTimeMillis(), CorrelationContext.getId());
            producer.handle(topic, message);
        }
    }
}
