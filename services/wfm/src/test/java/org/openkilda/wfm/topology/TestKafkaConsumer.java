/* Copyright 2017 Telstra Open Source
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

package org.openkilda.wfm.topology;

import static org.junit.Assert.assertNotNull;
import static org.openkilda.messaging.Utils.MAPPER;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.stream.StreamSupport;

public class TestKafkaConsumer {
    private static final long KAFKA_MESSAGE_POLL_TIMEOUT = 10000;
    private static final long KAFKA_CONSUMER_POLL_TIMEOUT = 100;
    private final KafkaConsumer<String, String> consumer;
    private final String topic;
    private final Destination destination;
    private volatile boolean isAlive = false;
    private volatile BlockingQueue<ConsumerRecord<String, String>> records = new ArrayBlockingQueue<>(100);

    public TestKafkaConsumer(final String topic, final Destination destination, final Properties properties) {
        this.consumer = new KafkaConsumer<>(properties);
        this.topic = topic;
        this.destination = destination;

        consumer.subscribe(Collections.singletonList(topic));
    }

    public void jumpToTheEnd() {
        consumer.poll(10L);
        consumer.seekToEnd(Collections.emptyList());
        consumer.poll(10L);
    }

    public ConsumerRecord<String, String> pollMessage() throws InterruptedException {
        return pollMessage(KAFKA_MESSAGE_POLL_TIMEOUT);
    }

    public ConsumerRecord<String, String> pollMessage(final long timeout) throws InterruptedException {
        long started = System.currentTimeMillis();
        ConsumerRecord<String, String> record = null;
        do {
            ConsumerRecords<String, String> polledRecords = consumer.poll(KAFKA_CONSUMER_POLL_TIMEOUT);
            if (Objects.nonNull(polledRecords)) {
                record = StreamSupport.stream(polledRecords.spliterator(), false)
                        .findFirst()
                        .orElse(null);
            }
        } while (started + timeout > System.currentTimeMillis()
                && (record == null || !checkDestination(record.value())));

        assertNotNull("Message was not received", record);
        consumer.commitSync();
        return record;
    }

    public void clear() {
        jumpToTheEnd();
    }

    public void wakeup() {
        consumer.wakeup();
    }

    public void closeConsumer() {
        consumer.unsubscribe();
        consumer.close();
    }

    public int getLastOffset() {
        PartitionInfo partitionInfo = consumer.partitionsFor(topic).get(0);
        TopicPartition topicPartition = new TopicPartition(topic, partitionInfo.partition());
        Set<TopicPartition> partitions = new HashSet<>();
        partitions.add(topicPartition);

        long endOffset = this.consumer.endOffsets(partitions).get(topicPartition);
        return Math.toIntExact(endOffset);
    }

    public void goToOffset(int offset) {
        PartitionInfo partitionInfo = consumer.partitionsFor(topic).get(0);
        TopicPartition topicPartition = new TopicPartition(topic, partitionInfo.partition());
        consumer.poll(0);
        consumer.seek(topicPartition, offset);
        consumer.poll(0);
    }

    private boolean checkDestination(final String recordValue) {
        boolean result = false;
        try {
            if (destination != null) {
                Message message = MAPPER.readValue(recordValue, Message.class);
                if (destination.equals(message.getDestination())) {
                    result = true;
                }
            } else {
                Message message = MAPPER.readValue(recordValue, Message.class);
                if (message != null) {
                    result = true;
                }
            }
        } catch (IOException exception) {
            System.out.println(String.format("Can not deserialize %s with destination %s ", recordValue, destination));
            exception.printStackTrace();
        }
        return result;
    }
}
