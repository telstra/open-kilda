/* Copyright 2017 Telstra Open Source
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

package org.openkilda.northbound.config;

import static org.openkilda.bluegreen.kafka.Utils.PRODUCER_COMPONENT_NAME_PROPERTY;
import static org.openkilda.bluegreen.kafka.Utils.PRODUCER_RUN_ID_PROPERTY;
import static org.openkilda.bluegreen.kafka.Utils.PRODUCER_ZOOKEEPER_CONNECTION_STRING_PROPERTY;
import static org.openkilda.bluegreen.kafka.Utils.PRODUCER_ZOOKEEPER_RECONNECTION_DELAY_PROPERTY;
import static org.openkilda.northbound.config.KafkaNorthboundConfig.NORTHBOUND_COMPONENT_NAME;

import org.openkilda.bluegreen.kafka.interceptors.VersioningProducerInterceptor;
import org.openkilda.messaging.Message;
import org.openkilda.northbound.messaging.MessageProducer;
import org.openkilda.northbound.messaging.kafka.KafkaMessageProducer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka message producer configuration.
 */
@Configuration
public class MessageProducerConfig {
    /**
     * Kafka bootstrap servers.
     */
    @Value("${kafka.hosts}")
    private String kafkaHosts;

    /**
     * Kilda blue green-mode.
     */
    @Value("${BLUE_GREEN_MODE:blue}")
    private String blueGreenMode;

    /**
     * ZooKeeper hosts.
     */
    @Value("${zookeeper.connect_string}")
    private String zookeeperConnectString;

    /**
     * Zookeeper reconnect delay.
     */
    @Value("${zookeeper.reconnect_delay:100}")
    private long zookeeperReconnectDelayMs;

    /**
     * Kafka producer config bean.
     * This {@link Map} is used by {@link MessageProducerConfig#producerFactory}.
     *
     * @return kafka properties bean
     */
    private Map<String, Object> producerConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaHosts);
        props.put(ProducerConfig.RETRIES_CONFIG, 0);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, VersioningProducerInterceptor.class.getName());
        props.put(PRODUCER_COMPONENT_NAME_PROPERTY, NORTHBOUND_COMPONENT_NAME);
        props.put(PRODUCER_RUN_ID_PROPERTY, blueGreenMode);
        props.put(PRODUCER_ZOOKEEPER_CONNECTION_STRING_PROPERTY, zookeeperConnectString);
        props.put(PRODUCER_ZOOKEEPER_RECONNECTION_DELAY_PROPERTY, Long.toString(zookeeperReconnectDelayMs));
        return props;
    }

    /**
     * Kafka producer factory bean.
     * The strategy to produce a {@link org.apache.kafka.clients.producer.Producer} instance
     * with {@link MessageProducerConfig#producerConfigs}
     * on each {@link org.springframework.kafka.core.DefaultKafkaProducerFactory#createProducer} invocation.
     *
     * @return kafka producer factory
     */
    @Bean
    public ProducerFactory<String, Message> producerFactory(ObjectMapper objectMapper) {
        return new DefaultKafkaProducerFactory<>(producerConfigs(), new StringSerializer(),
                new JsonSerializer<>(objectMapper));
    }

    /**
     * Kafka template bean.
     * Wraps {@link org.apache.kafka.clients.producer.KafkaProducer}.
     *
     * @return kafka template
     */
    @Bean
    public KafkaTemplate<String, Message> kafkaTemplate(ProducerFactory<String, Message> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    /**
     * Kafka message producer bean.
     * Instance of {@link org.openkilda.northbound.messaging.kafka.KafkaMessageProducer}
     * contains {@link org.springframework.kafka.core.KafkaTemplate}
     * to be used to send messages.
     *
     * @return kafka message producer
     */
    @Bean
    public MessageProducer messageProducer() {
        return new KafkaMessageProducer();
    }
}
