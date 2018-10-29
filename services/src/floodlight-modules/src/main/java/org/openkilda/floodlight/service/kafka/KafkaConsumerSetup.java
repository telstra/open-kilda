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

package org.openkilda.floodlight.service.kafka;

import static org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class KafkaConsumerSetup {
    private final HashSet<String> topicsSet = new HashSet<>();

    private final HashMap<String, String> configOverride = new HashMap<>();

    public KafkaConsumerSetup(String topic, String... extraTopics) {
        topicsSet.add(topic);
        topicsSet.addAll(Arrays.asList(extraTopics));
    }

    public void offsetResetStrategy(OffsetResetStrategy strategy) {
        configOverride.put(AUTO_OFFSET_RESET_CONFIG, strategy.toString().toLowerCase());
    }

    /**
     * List of kafka-topics consumer will be subscribed to.
     */
    public List<String> getTopics() {
        ArrayList<String> topics = new ArrayList<>(topicsSet.size());
        Collections.sort(topics);
        return topics;
    }

    /**
     * Mangle consumer configuration.
     */
    public Properties applyConfig(Properties config) {
        for (Map.Entry<String, String> entry : configOverride.entrySet()) {
            config.setProperty(entry.getKey(), entry.getValue());
        }
        return config;
    }

    /**
     * Apply setup on kafka-consumer.
     */
    public void applyInstance(KafkaConsumer<?, ?> consumer) {
        consumer.subscribe(topicsSet);
    }
}
