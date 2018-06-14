package org.openkilda.floodlight.kafka;

import static java.util.Objects.requireNonNull;

import org.openkilda.floodlight.config.KafkaFloodlightConfig;

import java.util.Properties;

public class Context {
    private final KafkaFloodlightConfig kafkaConfig;

    public Context(KafkaFloodlightConfig kafkaConfig) {
        this.kafkaConfig = requireNonNull(kafkaConfig, "kafkaConfig cannot be null");
    }

    public boolean isTestingMode() {
        return "YES".equals(kafkaConfig.getTestingMode());
    }

    public String getHeartBeatInterval() {
        return kafkaConfig.getHeartBeatInterval();
    }

    public Properties getKafkaProducerProperties() {
        return kafkaConfig.createKafkaProducerProperties();
    }

    public Properties getKafkaConsumerProperties() {
        return kafkaConfig.createKafkaConsumerProperties();
    }
}
