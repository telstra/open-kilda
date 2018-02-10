package org.openkilda.floodlight.kafka;

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
    public void run() {
        Message message = new org.openkilda.messaging.HeartBeat();
        producer.handle(topic, message);
    }
}
