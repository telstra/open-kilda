package org.openkilda.floodlight.kafka;

import java.util.Map;
import java.util.Properties;

public class KafkaConfig extends Properties {

    public KafkaConfig(Map<String, String> moduleConfig) {
        super();

        putDefaults();

        put("bootstrap.servers", moduleConfig.get("bootstrap-servers"));
    }

    private void putDefaults() {
        put("acks", "all");
        put("retries", 0);
        put("batch.size", 4);
        put("buffer.memory", 33554432);
        put("linger.ms", 10);

        // producer
        put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        // consumer
        put("group.id", "experiment-consumer");
        put("session.timeout.ms", "30000");
        put("enable.auto.commit", "true");
        put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    }
}
