/* Copyright 2019 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.grpc.speaker.config;

import org.openkilda.messaging.command.CommandMessage;

import com.google.common.collect.ImmutableMap;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.Map;

/**
 * The Kafka message consumer configuration.
 */
@Configuration
@EnableKafka
@PropertySource("classpath:grpc-service.properties")
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
    @Value("#{kafkaGroupConfig.getGroupId()}")
    private String groupId;

    /**
     * Kafka group id.
     */
    @Value("${grpc.speaker.kafka.listener.threads}")
    private int kafkaListeners;

    /**
     * Kafka group id.
     */
    @Value("${grpc.speaker.kafka.session.timeout}")
    private int kafkaSessionTimeout;

    /**
     * Kafka consumer configuration bean. This {@link Map} is used by {@link MessageConsumerConfig#consumerFactory}.
     *
     * @return kafka properties
     */
    @Bean
    public Map<String, Object> consumerConfigs() {
        return ImmutableMap.<String, Object>builder()
                .put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaHosts)
                .put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class)
                .put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class)
                .put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
                .put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true)
                .put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, kafkaSessionTimeout)
                .build();
    }

    /**
     * Kafka consumer factory bean. The strategy to produce a {@link org.apache.kafka.clients.consumer.Consumer}
     * instance with {@link MessageConsumerConfig#consumerConfigs} on each
     * {@link DefaultKafkaConsumerFactory#createConsumer}
     * invocation.
     *
     * @return kafka consumer factory
     */
    @Bean
    public ConsumerFactory<String, CommandMessage> consumerFactory() {
        return new DefaultKafkaConsumerFactory<>(consumerConfigs(),
                new StringDeserializer(), new JsonDeserializer<>(CommandMessage.class));
    }

    /**
     * Kafka listener container factory bean. Returned instance builds
     * {@link org.springframework.kafka.listener.ConcurrentMessageListenerContainer}
     * using the {@link org.apache.kafka.clients.consumer.Consumer}.
     *
     * @return kafka listener container factory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CommandMessage> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, CommandMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties().setPollTimeout(POLL_TIMEOUT);
        factory.setConcurrency(kafkaListeners);
        return factory;
    }
}
