/* Copyright 2020 Telstra Open Source
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

package org.openkilda.bluegreen.kafka.interceptors;

import static java.lang.String.format;
import static org.openkilda.bluegreen.kafka.Utils.CONSUMER_COMPONENT_NAME_PROPERTY;
import static org.openkilda.bluegreen.kafka.Utils.CONSUMER_RUN_ID_PROPERTY;
import static org.openkilda.bluegreen.kafka.Utils.CONSUMER_ZOOKEEPER_CONNECTION_STRING_PROPERTY;
import static org.openkilda.bluegreen.kafka.Utils.MESSAGE_VERSION_HEADER;
import static org.openkilda.bluegreen.kafka.Utils.getValue;

import org.openkilda.bluegreen.kafka.DeserializationError;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class VersioningConsumerInterceptor<K, V> extends VersioningInterceptorBase
        implements ConsumerInterceptor<K, V> {

    public VersioningConsumerInterceptor() {
        log.debug("Initializing VersioningConsumerInterceptor");
    }

    @Override
    public ConsumerRecords<K, V> onConsume(ConsumerRecords<K, V> records) {
        if (!watchDog.isConnectedAndValidated()) {
            if (isZooKeeperConnectTimeoutPassed()) {
                log.error("Component {} with id {} tries to reconnect to ZooKeeper with connection string: {}",
                        componentName, runId, connectionString);
                cantConnectToZooKeeperTimestamp = Instant.now();
            }
            watchDog.safeRefreshConnection();
        }

        Map<TopicPartition, List<ConsumerRecord<K, V>>> filteredRecordMap = new HashMap<>();

        for (TopicPartition partition : records.partitions()) {
            List<ConsumerRecord<K, V>> filteredRecords = new ArrayList<>();

            for (ConsumerRecord<K, V> record : records.records(partition)) {
                if (checkRecordVersion(record)) {
                    filteredRecords.add(record);

                    if (record.value() instanceof DeserializationError) {
                        log.error("Can't deserialize message from topic: {}, partition: {}, error: {}",
                                record.topic(), record.partition(),
                                ((DeserializationError) record.value()).getDeserializationErrorMessage());
                    }
                }
            }

            filteredRecordMap.put(partition, filteredRecords);
        }
        return new ConsumerRecords<>(filteredRecordMap);
    }

    private boolean checkRecordVersion(ConsumerRecord<K, V> record) {
        List<Header> headers = Lists.newArrayList(record.headers().headers(MESSAGE_VERSION_HEADER));

        if (version == null) {
            if (isVersionTimeoutPassed()) {
                // We will write this log every 60 seconds to do not spam in logs
                log.warn("Messaging version is not set for component {} with id {}. Skip record {}",
                        componentName, runId, record);
                versionIsNotSetTimestamp = Instant.now();
            }
            return false;
        }

        if (headers.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("Missed {} header for record {}", MESSAGE_VERSION_HEADER, record);
            }
            return false;
        }

        if (headers.size() > 1) {
            log.warn("Found more than one {} headers for record {}", MESSAGE_VERSION_HEADER, record);
            return false;
        }

        if (!Arrays.equals(version, headers.get(0).value())) {
            if (log.isDebugEnabled()) {
                log.debug("Skip record {} with version {}. Target version is {}",
                        record, new String(headers.get(0).value()), getVersionAsString());
            }
            return false;
        }
        return true;
    }

    @Override
    public void configure(Map<String, ?> configs) {
        connectionString = getValue(configs, CONSUMER_ZOOKEEPER_CONNECTION_STRING_PROPERTY, String.class);
        runId = getValue(configs, CONSUMER_RUN_ID_PROPERTY, String.class);
        componentName = getValue(configs, CONSUMER_COMPONENT_NAME_PROPERTY, String.class);
        log.info("Configuring VersioningConsumerInterceptor for component {} with id {} and connection string {}",
                componentName, runId, connectionString);
        initWatchDog();
    }

    @Override
    public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {
        // nothing to do here
    }

    @Override
    public void close() {
        watchDog.unsubscribe(this);
        try {
            watchDog.close();
        } catch (InterruptedException e) {
            log.error(format("Could not close connection to zookeeper %s Error: %s",
                    connectionString, e.getMessage()), e);
        }
    }

    @Override
    public void handle(String buildVersion) {
        log.info("Updating consumer kafka messaging version from {} to {} for component {} with id {}",
                getVersionAsString(), buildVersion, componentName, runId);
        version = buildVersion.getBytes();
    }
}
