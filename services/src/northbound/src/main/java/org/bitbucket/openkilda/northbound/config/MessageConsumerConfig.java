package org.bitbucket.openkilda.northbound.config;

import org.bitbucket.openkilda.northbound.messaging.kafka.KafkaMessageConsumer;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * The Kafka message consumer configuration.
 */
@Configuration
@EnableKafka
@PropertySource("classpath:northbound.properties")
@ComponentScan("org.bitbucket.openkilda.northbound")
public class MessageConsumerConfig {
    /**
     * Kafka queue poll timeout.
     */
    private static final int POLL_TIMEOUT = 3000;

    /**
     * Kafka bootstrap servers.
     */
    @Value("${kafka.hosts}")
    private String kafkaHosts;

    /**
     * Kafka group id.
     */
    @Value("${kafka.groupid}")
    private String groupId;

    /**
     * Kafka consumer configuration bean. This {@link Map} is used by {@link MessageConsumerConfig#consumerFactory}.
     *
     * @return kafka properties
     */
    @Bean
    public Map<String, Object> consumerConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaHosts);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000");
        //props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        //props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "100");
        //props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return props;
    }

    /**
     * Kafka consumer factory bean. The strategy to produce a {@link org.apache.kafka.clients.consumer.Consumer}
     * instance with {@link MessageConsumerConfig#consumerConfigs} on each {@link org.springframework.kafka.core.DefaultKafkaConsumerFactory#createConsumer}
     * invocation.
     *
     * @return kafka consumer factory
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        return new DefaultKafkaConsumerFactory<>(consumerConfigs());
    }

    /**
     * Kafka listener container factory bean. Returned instance builds {@link org.springframework.kafka.listener.ConcurrentMessageListenerContainer}
     * using the {@link org.apache.kafka.clients.consumer.Consumer}.
     *
     * @return kafka listener container factory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties().setPollTimeout(POLL_TIMEOUT);
        //factory.setConcurrency(10);
        return factory;
    }

    /**
     * Kafka message consumer bean. Instance of {@link org.bitbucket.openkilda.northbound.messaging.kafka.KafkaMessageConsumer}
     * contains {@link org.springframework.kafka.annotation.KafkaListener} to be run in {@link
     * org.springframework.kafka.listener.ConcurrentMessageListenerContainer}.
     *
     * @return kafka message consumer
     */
    @Bean
    public KafkaMessageConsumer kafkaMessageConsumer() {
        return new KafkaMessageConsumer();
    }
}
