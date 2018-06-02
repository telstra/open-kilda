package org.openkilda.floodlight.kafka;

import org.openkilda.floodlight.kafka.producer.Producer;
import org.openkilda.messaging.Topic;

import java.util.Timer;
import java.util.TimerTask;

public class HeartBeat {
    private static final String topic = Topic.TOPO_DISCO;

    private final Producer producer;
    private final long interval;

    private final Timer timer;
    private TimerTask task;

    public HeartBeat(Producer producer, long interval) {
        this.producer = producer;
        this.interval = interval;

        task = new HeartBeatAction(producer, topic);
        timer = new Timer("kafka.HeartBeat", true);
        timer.scheduleAtFixedRate(task, interval, interval);
    }

    public void reschedule() {
        TimerTask replace = new HeartBeatAction(producer, topic);
        timer.scheduleAtFixedRate(replace, interval, interval);

        synchronized (this) {
            task.cancel();
            task = replace;
        }
    }
}
