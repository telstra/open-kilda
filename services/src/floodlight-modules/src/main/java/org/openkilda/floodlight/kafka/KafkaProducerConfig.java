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

import org.openkilda.floodlight.config.KafkaFloodlightConfig;

import com.sabre.oss.conf4j.annotation.Configuration;
import com.sabre.oss.conf4j.annotation.Key;

import java.util.Properties;

@Configuration
public interface KafkaProducerConfig extends KafkaFloodlightConfig {
    @Key("testing-mode")
    String getTestingMode();

    @Key("heart-beat-interval")
    float getHeartBeatInterval();

    default boolean isTestingMode() {
        return "YES".equals(getTestingMode());
    }

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
}
