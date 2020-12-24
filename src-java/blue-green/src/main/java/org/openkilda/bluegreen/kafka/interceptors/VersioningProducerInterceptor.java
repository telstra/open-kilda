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
import static org.openkilda.bluegreen.kafka.Utils.MESSAGE_VERSION_HEADER;
import static org.openkilda.bluegreen.kafka.Utils.PRODUCER_COMPONENT_NAME_PROPERTY;
import static org.openkilda.bluegreen.kafka.Utils.PRODUCER_RUN_ID_PROPERTY;
import static org.openkilda.bluegreen.kafka.Utils.PRODUCER_ZOOKEEPER_CONNECTION_STRING_PROPERTY;
import static org.openkilda.bluegreen.kafka.Utils.getValue;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.time.Instant;
import java.util.Map;

@Slf4j
public class VersioningProducerInterceptor<K, V> extends VersioningInterceptorBase
        implements ProducerInterceptor<K, V> {

    public VersioningProducerInterceptor() {
        log.debug("Initializing VersioningProducerInterceptor");
    }

    @Override
    public ProducerRecord<K, V> onSend(ProducerRecord<K, V> record) {
        if (!watchDog.isConnectedAndValidated()) {
            if (isZooKeeperConnectTimeoutPassed()) {
                log.error("Component {} with id {} tries to reconnect to ZooKeeper with connection string: {}",
                        componentName, runId, connectionString);
                cantConnectToZooKeeperTimestamp = Instant.now();
            }
            watchDog.safeRefreshConnection(); // try to reconnect
        }

        if (record.headers().headers(MESSAGE_VERSION_HEADER).iterator().hasNext()) {
            log.info("Kafka record {} already has header {}: {}",
                    record, MESSAGE_VERSION_HEADER,
                    new String(record.headers().headers(MESSAGE_VERSION_HEADER).iterator().next().value()));
            record.headers().remove(MESSAGE_VERSION_HEADER);
        }

        if (version == null) {
            if (isVersionTimeoutPassed()) {
                // We will write this log every 60 seconds to do not spam in logs
                log.warn("Messaging version is not set for component {} with id {}. Produce record {} without version",
                        componentName, runId, record);
                versionIsNotSetTimestamp = Instant.now();
            }
            // We can't return null record. We will return record without version.
            // Such records wouldn't be read by Kafka Consumer with VersioningConsumerInterceptor
            return record;
        }

        record.headers().add(MESSAGE_VERSION_HEADER, version);
        return record;
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        // nothing to do
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
    public void configure(Map<String, ?> configs) {
        connectionString = getValue(configs, PRODUCER_ZOOKEEPER_CONNECTION_STRING_PROPERTY, String.class);
        runId = getValue(configs, PRODUCER_RUN_ID_PROPERTY, String.class);
        componentName = getValue(configs, PRODUCER_COMPONENT_NAME_PROPERTY, String.class);
        log.info("Configuring VersioningProducerInterceptor for component {} with id {} and connection string {}",
                componentName, runId, connectionString);
        initWatchDog();
    }

    @Override
    public void handle(String buildVersion) {
        log.info("Updating producer kafka messaging version from {} to {} for component {} with id {}",
                getVersionAsString(), buildVersion, componentName, runId);
        version = buildVersion.getBytes();
    }
}
