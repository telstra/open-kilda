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

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import org.openkilda.floodlight.switchmanager.ISwitchManager;

import com.google.common.annotations.VisibleForTesting;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.InterruptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ExecutorService;

public class Consumer implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(Consumer.class);

    private final List<String> topics;
    private final KafkaConsumerConfig kafkaConfig;
    private final ExecutorService handlersPool;
    private final RecordHandler.Factory handlerFactory;
    private final ISwitchManager switchManager; // HACK alert.. adding to facilitate safeSwitchTick()
    private final OffsetResetStrategy defaultOffsetStrategy;

    public Consumer(KafkaConsumerConfig kafkaConfig, ExecutorService handlersPool,
                    RecordHandler.Factory handlerFactory, ISwitchManager switchManager,
                    String topic, OffsetResetStrategy defaultOffsetStrategy) {
        this.topics = Collections.singletonList(requireNonNull(topic));

        this.kafkaConfig = requireNonNull(kafkaConfig);
        this.handlersPool = requireNonNull(handlersPool);
        this.handlerFactory = requireNonNull(handlerFactory);
        this.switchManager = requireNonNull(switchManager);
        this.defaultOffsetStrategy = defaultOffsetStrategy;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            /*
             * Ensure we try to keep processing messages. It is possible that the consumer needs
             * to be re-created, either due to internal error, or if it fails to poll within the
             * max.poll.interval.ms seconds.
             *
             * From the Kafka source code, here are the default values for the following fields:
             *  - max.poll.interval.ms = 300000 (ie 300 seconds)
             *  - max.poll.records = 500 (must be able to process about 2 records per second
             */

            Properties consumerProperties = kafkaConfig.createKafkaConsumerProperties();
            if (defaultOffsetStrategy != null) {
                // Define what to do when there is no offset in Kafka.
                consumerProperties.setProperty("auto.offset.reset", defaultOffsetStrategy.toString().toLowerCase());
            }

            try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties)) {
                consumer.subscribe(topics);
                logger.info("Kafka consumer: start. Topics: {}", topics);

                KafkaOffsetRegistry offsetRegistry =
                        new KafkaOffsetRegistry(consumer, kafkaConfig.getAutoCommitInterval());

                while (true) {
                    try {
                        ConsumerRecords<String, String> batch = consumer.poll(100);
                        if (batch.isEmpty()) {
                            continue;
                        }

                        logger.debug("Received records batch contain {} messages", batch.count());

                        for (ConsumerRecord<String, String> record : batch) {
                            handle(record);

                            offsetRegistry.addAndCommit(record);
                        }
                    } finally {
                        // force to commit after each completed batch or in a case of an exception / error.
                        offsetRegistry.commitOffsets();
                    }

                    switchManager.safeModeTick(); // HACK alert .. should go in its own timer loop
                }
            } catch (InterruptException ex) {
                // Gracefully finish loop on thread interruption.
                logger.warn("Kafka consumer loop has been interrupted");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // Just log the exception, and start processing again with a new consumer.
                logger.error("Exception received during main kafka consumer loop: {}", e);
            }
        }
    }

    protected void handle(ConsumerRecord<String, String> record) {
        logger.trace("received message: {} - {}", record.offset(), record.value());
        handlersPool.execute(handlerFactory.produce(record));
    }

    /**
     * Holds offsets for Kafka partitions and performs sync commits of them.
     * <p/>
     * Note: the implementation is not thread-safe.
     */
    @VisibleForTesting
    static class KafkaOffsetRegistry {
        private final KafkaConsumer<String, String> consumer;
        private final long autoCommitInterval;
        private final Map<TopicPartition, Long> partitionToUncommittedOffset = new HashMap<>();
        private long lastCommitTime;

        KafkaOffsetRegistry(KafkaConsumer<String, String> consumer, long autoCommitInterval) {
            this.consumer = requireNonNull(consumer);
            checkArgument(autoCommitInterval > 0, "autoCommitInterval must be positive");
            this.autoCommitInterval = autoCommitInterval;

            lastCommitTime = System.currentTimeMillis();
        }

        /**
         * Add the record's offset to the registry and perform a commit
         * if more than autoCommitInterval ms passed since the last commit.
         */
        void addAndCommit(ConsumerRecord<String, String> record) {
            TopicPartition partition = new TopicPartition(record.topic(), record.partition());

            Long previousOffset = partitionToUncommittedOffset.get(partition);
            if (previousOffset != null && previousOffset > record.offset()) {
                throw new IllegalArgumentException(
                        format("The record has offset %d which less than the previously added %d.",
                                record.offset(), previousOffset));
            }

            partitionToUncommittedOffset.put(partition, record.offset());

            // commit offsets of processed messages
            if ((System.currentTimeMillis() - lastCommitTime) >= autoCommitInterval) {
                commitOffsets();
            }
        }

        /**
         * Commits the offsets added since the last commit.
         */
        void commitOffsets() {
            if (!partitionToUncommittedOffset.isEmpty()) {
                Map<TopicPartition, OffsetAndMetadata> partitionToMetadata = new HashMap<>();
                for (Entry<TopicPartition, Long> e : partitionToUncommittedOffset.entrySet()) {
                    partitionToMetadata.put(e.getKey(), new OffsetAndMetadata(e.getValue() + 1));
                }

                consumer.commitSync(partitionToMetadata);

                partitionToUncommittedOffset.clear();
            }

            lastCommitTime = System.currentTimeMillis();
        }
    }
}
