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

import org.openkilda.messaging.info.InfoData;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
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
@PropertySource("classpath:grpc-speaker.properties")
public class MessageProducerConfig {
    /**
     * Kafka bootstrap servers.
     */
    @Value("${kafka.hosts}")
    private String kafkaHosts;

    /**
     * Kafka producer config bean.
     * This {@link Map} is used by {@link MessageProducerConfig#producerFactory}.
     *
     * @return kafka properties bean
     */
    @Bean
    public Map<String, Object> producerConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaHosts);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.RETRIES_CONFIG, 0);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        return props;
    }

    /**
     * Kafka producer factory bean.
     * The strategy to produce a {@link org.apache.kafka.clients.producer.Producer} instance
     * with {@link MessageProducerConfig#producerConfigs}
     * on each {@link DefaultKafkaProducerFactory#createProducer} invocation.
     *
     * @return kafka producer factory
     */
    @Bean
    public ProducerFactory<String, InfoData> producerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfigs());
    }

    /**
     * Kafka template bean.
     * Wraps {@link org.apache.kafka.clients.producer.KafkaProducer}.
     *
     * @return kafka template
     */
    @Bean
    public KafkaTemplate<String, InfoData> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
