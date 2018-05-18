package org.openkilda.floodlight.kafka;

import org.openkilda.floodlight.utils.CorrelationContext;
import org.openkilda.floodlight.utils.NewCorrelationContextRequired;
import org.openkilda.messaging.Message;

import java.util.TimerTask;

public class HeartBeatAction extends TimerTask {
    private final Producer producer;
    private final String topic;

    public HeartBeatAction(Producer producer, String topic) {
        this.producer = producer;
        this.topic = topic;
    }

    @Override
    @NewCorrelationContextRequired
    public void run() {
        Message message = new org.openkilda.messaging.HeartBeat(System.currentTimeMillis(), CorrelationContext.getId());
        producer.handle(topic, message);
    }
}
