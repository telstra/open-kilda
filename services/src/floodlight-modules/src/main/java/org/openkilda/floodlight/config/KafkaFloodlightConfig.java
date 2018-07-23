/* Copyright 2018 Telstra Open Source
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

package org.openkilda.floodlight.config;

import org.openkilda.config.KafkaConsumerGroupConfig;
import org.openkilda.config.mapping.Mapping;

import com.sabre.oss.conf4j.annotation.Configuration;
import com.sabre.oss.conf4j.annotation.Default;
import com.sabre.oss.conf4j.annotation.Key;

import java.util.Properties;

@Configuration
public interface KafkaFloodlightConfig extends KafkaConsumerGroupConfig {
    @Key("kafka-groupid")
    @Default("floodlight")
    @Mapping(target = KAFKA_CONSUMER_GROUP_MAPPING)
    String getGroupId();

    @Key("bootstrap-servers")
    String getBootstrapServers();

    @Key("testing-mode")
    String getTestingMode();

    @Key("heart-beat-interval")
    String getHeartBeatInterval();

    /**
     * Returns Kafka properties built with the configuration data for Producer.
     */
    default Properties createKafkaProducerProperties() {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", getBootstrapServers());

        properties.put("acks", "all");
        properties.put("retries", 0);
        properties.put("batch.size", 4);
        properties.put("buffer.memory", 33554432);
        properties.put("linger.ms", 10);

        properties.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        properties.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        return properties;
    }

    /**
     * Returns Kafka properties built with the configuration data for Consumer.
     */
    default Properties createKafkaConsumerProperties() {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", getBootstrapServers());

        properties.put("group.id", getGroupId());
        properties.put("session.timeout.ms", "30000");
        properties.put("enable.auto.commit", "true");

        properties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

        return properties;
    }
}
