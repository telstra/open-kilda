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

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

@Slf4j
public class KafkaConsumerSetup {
    private final HashSet<String> topicsSet = new HashSet<>();

    private final HashMap<String, String> configOverride = new HashMap<>();

    private boolean forceSeekToEnd = false;

    public KafkaConsumerSetup(String topic, String... extraTopics) {
        topicsSet.add(topic);
        topicsSet.addAll(Arrays.asList(extraTopics));
    }

    public KafkaConsumerSetup withOffsetResetStrategy(OffsetResetStrategy strategy) {
        configOverride.put(AUTO_OFFSET_RESET_CONFIG, strategy.toString().toLowerCase());
        return this;
    }

    public KafkaConsumerSetup withForceSeekToEnd(boolean value) {
        forceSeekToEnd = value;
        return this;
    }

    /**
     * List of kafka-topics consumer will be subscribed to.
     */
    public List<String> getTopics() {
        ArrayList<String> topics = new ArrayList<>(topicsSet);
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
        if (forceSeekToEnd) {
            seekToEnd(consumer);
        }
    }

    private void seekToEnd(KafkaConsumer<?, ?> consumer) {
        // Force partitions assignment, because this is initial read and seek to end is forced we can ignore any
        // available now records (i.e. ignore results of .poll() call)
        consumer.poll(0L);

        // Seek to end assigned for current consumer partitions (other consumers for this consumer group will do the
        // same) i.e. seek to the end will be applied to all partitions
        List<TopicPartition> partitionsToSeek = new ArrayList<>(consumer.assignment());
        log.info("Seek kafka partitions to the end: \"{}\"", partitionsToSeek.stream()
                .map(TopicPartition::toString)
                .collect(Collectors.joining("\", \"")));
        consumer.seekToEnd(partitionsToSeek);
    }
}
