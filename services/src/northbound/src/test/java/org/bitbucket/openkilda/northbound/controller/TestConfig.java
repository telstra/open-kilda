package org.bitbucket.openkilda.northbound.controller;

import org.bitbucket.openkilda.northbound.config.MessageConsumerConfig;
import org.bitbucket.openkilda.northbound.config.MessageProducerConfig;
import org.bitbucket.openkilda.northbound.config.SecurityConfig;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import java.util.HashMap;
import java.util.Map;

/**
 * The Test configuration.
 */
@Configuration
@EnableWebSecurity
@Import({MessageConsumerConfig.class, MessageProducerConfig.class, SecurityConfig.class})
public class TestConfig {
    /**
     * Kafka consumer configuration bean. This {@link Map} is used by {@link MessageConsumerConfig#consumerFactory}.
     * Overrides {@link MessageConsumerConfig#consumerConfigs()}.
     *
     * @return kafka properties
     */
    @Primary
    @Bean
    public Map<String, Object> consumerConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getProperty("kafka.bootstrap-servers"));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000");
        return props;
    }

    /**
     * Kafka producer config bean. This {@link Map} is used by {@link MessageProducerConfig#producerFactory}. Overrides
     * {@link MessageProducerConfig#producerConfigs()}.
     *
     * @return kafka properties bean
     */
    @Primary
    @Bean
    public Map<String, Object> producerConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getProperty("kafka.bootstrap-servers"));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.RETRIES_CONFIG, 0);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        return props;
    }
}
