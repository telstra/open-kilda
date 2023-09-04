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

package org.openkilda.wfm.topology.opentsdb;

import static java.lang.String.format;
import static org.openkilda.bluegreen.kafka.Utils.CONSUMER_COMPONENT_NAME_PROPERTY;
import static org.openkilda.bluegreen.kafka.Utils.CONSUMER_RUN_ID_PROPERTY;
import static org.openkilda.bluegreen.kafka.Utils.CONSUMER_ZOOKEEPER_CONNECTION_STRING_PROPERTY;
import static org.openkilda.bluegreen.kafka.Utils.CONSUMER_ZOOKEEPER_RECONNECTION_DELAY_PROPERTY;
import static org.openkilda.bluegreen.kafka.Utils.MESSAGE_VERSION_HEADER;
import static org.openkilda.bluegreen.kafka.Utils.getValue;

import org.openkilda.bluegreen.kafka.TransportAdapter;
import org.openkilda.bluegreen.kafka.interceptors.VersioningInterceptorBase;
import org.openkilda.messaging.info.InfoData;

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
public class VersioningConsumerInterceptor2<K> extends VersioningInterceptorBase
        implements ConsumerInterceptor<K, InfoData> {

    public VersioningConsumerInterceptor2() {
        log.debug("Initializing VersioningConsumerInterceptor");
    }

    @Override
    public ConsumerRecords<K, InfoData> onConsume(ConsumerRecords<K, InfoData> records) {
        if (!watchDog.isConnectedAndValidated()) {
            if (isZooKeeperConnectTimeoutPassed()) {
                log.error("Component {} with id {} tries to reconnect to ZooKeeper with connection string: {}",
                        componentName, runId, connectionString);
                cantConnectToZooKeeperTimestamp = Instant.now();
            }
            watchDog.safeRefreshConnection();
        }

        Map<TopicPartition, List<ConsumerRecord<K, InfoData>>> filteredRecordMap = new HashMap<>();

        for (TopicPartition partition : records.partitions()) {
            List<ConsumerRecord<K, InfoData>> filteredRecords = new ArrayList<>();

            for (ConsumerRecord<K, InfoData> record : records.records(partition)) {
                record.value().setInterceptorInTimestamp(System.currentTimeMillis());
                if (! checkRecordVersion(record)) {
                    continue;
                }

                if (! checkDeserializationError(record.value())) {
                    filteredRecords.add(record);
                }
            }

            if (! filteredRecords.isEmpty()) {
                filteredRecordMap.put(partition, filteredRecords);
            }
        }
        for (List<ConsumerRecord<K, InfoData>> value : filteredRecordMap.values()) {
            for (ConsumerRecord<K, InfoData> record : value) {
                record.value().setInterceptorOutTimestamp(System.currentTimeMillis());
            }
        }
        return new ConsumerRecords<>(filteredRecordMap);
    }

    private boolean checkRecordVersion(ConsumerRecord<K, InfoData> record) {
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

    private boolean checkDeserializationError(InfoData value) {
        if (value instanceof TransportAdapter) {
            return checkDeserializationError((TransportAdapter) value);
        }
        return false;
    }

    private boolean checkDeserializationError(TransportAdapter adapter) {
        return adapter.getErrorReport()
                .map(report -> {
                    log.error(
                            "Failed to deserialize message in kafka-topic \"{}\" using base class {}: {}",
                            report.getKafkaTopic(), report.getBaseClassName(), report.getError().getMessage());
                    log.debug(
                            "Not deserializable entry in kafka-topic \"{}\": {}",
                            report.getKafkaTopic(), report.getRawSource());
                    return true;
                })
                .orElse(false);
    }

    @Override
    public void configure(Map<String, ?> configs) {
        connectionString = getValue(configs, CONSUMER_ZOOKEEPER_CONNECTION_STRING_PROPERTY, String.class);
        runId = getValue(configs, CONSUMER_RUN_ID_PROPERTY, String.class);
        componentName = getValue(configs, CONSUMER_COMPONENT_NAME_PROPERTY, String.class);
        reconnectDelayMs = Long.parseLong(getValue(configs, CONSUMER_ZOOKEEPER_RECONNECTION_DELAY_PROPERTY,
                String.class));
        log.info("Configuring VersioningConsumerInterceptor for component {} with id {} and connection string {}",
                componentName, runId, connectionString);
        initWatchDog();
        log.info("Consumer interceptor was configured for component {} and id {} with kafka messaging version '{}'",
                componentName, runId, getVersionAsString());
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
