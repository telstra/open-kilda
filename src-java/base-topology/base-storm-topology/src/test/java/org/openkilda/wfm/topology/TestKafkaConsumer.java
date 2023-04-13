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

import static java.lang.String.format;
import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class TestKafkaConsumer extends Thread {
    private static final long CONSUMER_QUEUE_OFFER_TIMEOUT = 1000;
    private static final long KAFKA_CONSUMER_POLL_TIMEOUT = 100;
    private final KafkaConsumer<String, String> consumer;
    private final String topic;
    private final Destination destination;
    private long kafkaMessagePollTimeout = 30000;
    private boolean checkDestination = true;
    private volatile BlockingQueue<ConsumerRecord<String, String>> records = new ArrayBlockingQueue<>(100);


    public TestKafkaConsumer(final String topic, final Destination destination, final Properties properties) {
        this.consumer = new KafkaConsumer<>(properties);
        this.topic = topic;
        this.destination = destination;
    }

    public TestKafkaConsumer(final String topic, final Properties properties) {
        this(topic, null, properties);
        checkDestination = false;
    }

    public TestKafkaConsumer(final String topic, final Properties properties, long kafkaMessagePollTimeout) {
        this(topic, null, properties);
        checkDestination = false;
        this.kafkaMessagePollTimeout = kafkaMessagePollTimeout;
    }

    /**
     * Starts kafka consumer.
     * */
    public void run() {
        System.out.println("Starting Kafka Consumer for " + topic);
        consumer.subscribe(Collections.singletonList(topic));
        try {
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(KAFKA_CONSUMER_POLL_TIMEOUT);
                for (ConsumerRecord<String, String> record : records) {
                    if (checkDestination(record.value())) {
                        this.records.offer(record, CONSUMER_QUEUE_OFFER_TIMEOUT, TimeUnit.MILLISECONDS);
                        consumer.commitSync();
                        System.out.println(String.format("Received message with destination %s: %s",
                                destination, record.value()));
                    }
                }
            }
        } catch (WakeupException e) {
            System.out.println("Stopping Kafka Consumer for " + topic);
        } catch (InterruptedException e) {
            System.out.println("Interrupting Kafka Consumer for " + topic);
        } finally {
            consumer.unsubscribe();
            consumer.close();
        }
    }

    public ConsumerRecord<String, String> pollMessage() throws InterruptedException {
        return pollMessage(kafkaMessagePollTimeout);
    }

    public ConsumerRecord<String, String> pollMessage(final long timeout) throws InterruptedException {
        return records.poll(timeout, TimeUnit.MILLISECONDS);
    }

    public String pollMessageValue() throws InterruptedException {
        return Optional.ofNullable(pollMessage()).map(ConsumerRecord::value).orElse(null);
    }

    /**
     * Polls messages from kafka until expectedCount is reached.
     *
     * @param expectedCount expected count of messages
     * @param clazz         class of messages
     * @return list of messages
     */
    public <T> List<T> assertNAndPoll(final int expectedCount, Class<T> clazz) {
        List<T> messages = new ArrayList<>();
        ConsumerRecord<String, String> record = null;
        for (int i = 0; i < expectedCount; i++) {
            try {
                record = pollMessage();
                if (record == null) {
                    throw new AssertionError(format("Could not get %d records. %d records gotten",
                            expectedCount, messages.size()));
                }
                messages.add(MAPPER.readValue(record.value(), clazz));
            } catch (InterruptedException e) {
                throw new AssertionError(format("Could not get %d records. %d records gotten", expectedCount,
                        messages.size()));
            } catch (IOException e) {
                throw new AssertionError(format("Could not parse %s object: '%s'", clazz.getName(), record.value()));
            }
        }
        // ensure there are no more records
        if (!isEmpty()) {
            throw new AssertionError(format("Got more than %d records", expectedCount));
        }
        return messages;
    }

    public void clear() {
        records.clear();
    }

    public void wakeup() {
        consumer.wakeup();
    }

    public boolean isEmpty() {
        return records.isEmpty();
    }

    private boolean checkDestination(final String recordValue) {

        if (!checkDestination) {
            return true;
        }

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
