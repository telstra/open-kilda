package org.openkilda.floodlight.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.PartitionInfo;

public class KafkaTopicOrderDescriptor {
    private int deep = 0;
    private long expireAt = 0;
    private final String topic;
    private final int partition;

    public KafkaTopicOrderDescriptor(KafkaProducer<?, ?> producer, String topic) {
        this.topic = topic;
        partition = producer.partitionsFor(topic)
                .stream()
                .map(PartitionInfo::partition)
                .sorted()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("There is no any partition for topic %s", topic)));
    }

    public void enable() {
        deep += 1;
    }

    public void disable() {
        disable(1000);
    }

    public void disable(long transitionPeriod) {
        if (deep == 0) {
            throw new IllegalStateException("Number of .diable() calls have overcome number of .enable() calls");
        }

        deep -= 1;
        if (deep == 0) {
            expireAt = System.currentTimeMillis() + transitionPeriod;
        }
    }

    public boolean isEnabled() {
        if (0 < deep) {
            return true;
        }
        return expireAt < System.currentTimeMillis();
    }

    public int getPartition() {
        return partition;
    }

    @Override
    public String toString() {
        return "KafkaTopicOrderDescriptor{" +
                "topic='" + topic + '\'' +
                ", deep=" + deep +
                ", partition=" + partition +
                '}';
    }
}
