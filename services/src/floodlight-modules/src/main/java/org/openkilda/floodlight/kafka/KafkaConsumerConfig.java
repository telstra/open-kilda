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

package org.openkilda.floodlight.kafka;

import org.openkilda.config.KafkaConsumerGroupConfig;
import org.openkilda.config.mapping.Mapping;
import org.openkilda.floodlight.config.KafkaFloodlightConfig;

import com.sabre.oss.conf4j.annotation.Configuration;
import com.sabre.oss.conf4j.annotation.Default;
import com.sabre.oss.conf4j.annotation.Key;

import java.util.Properties;

@Configuration
public interface KafkaConsumerConfig extends KafkaFloodlightConfig, KafkaConsumerGroupConfig {
    @Key("kafka-groupid")
    @Default("floodlight")
    @Mapping(target = KAFKA_CONSUMER_GROUP_MAPPING)
    String getGroupId();

    @Key("testing-mode")
    String getTestingMode();

    @Key("consumer.executors")
    @Default("10")
    int getExecutorCount();

    @Key("consumer.auto-commit-interval")
    @Default("1000")
    long getAutoCommitInterval();

    default boolean isTestingMode() {
        return "YES".equals(getTestingMode());
    }

    /**
     * Returns Kafka properties built with the configuration data for Consumer.
     */
    default Properties createKafkaConsumerProperties() {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", getBootstrapServers());

        properties.put("group.id", getGroupId());
        properties.put("session.timeout.ms", "30000");
        properties.put("enable.auto.commit", "false");

        properties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        properties.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

        return properties;
    }
}
