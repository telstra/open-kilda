package org.bitbucket.openkilda.wfm.topology;

import static org.bitbucket.openkilda.messaging.Utils.MAPPER;

import org.bitbucket.openkilda.messaging.Destination;
import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.messaging.info.InfoData;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;

import java.io.IOException;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class TestKafkaConsumer extends Thread {
    private static final long CONSUMER_QUEUE_OFFER_TIMEOUT = 1000;
    private static final long KAFKA_MESSAGE_POLL_TIMEOUT = 10000;
    private static final long KAFKA_CONSUMER_POLL_TIMEOUT = 100;
    private final KafkaConsumer<String, String> consumer;
    private final String topic;
    private final Destination destination;
    private volatile BlockingQueue<ConsumerRecord<String, String>> records = new ArrayBlockingQueue<>(100);

    public TestKafkaConsumer(final String topic, final Destination destination, final Properties properties) {
        this.consumer = new KafkaConsumer<>(properties);
        this.topic = topic;
        this.destination = destination;
    }

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
        return pollMessage(KAFKA_MESSAGE_POLL_TIMEOUT);
    }

    public ConsumerRecord<String, String> pollMessage(final long timeout) throws InterruptedException {
        return records.poll(timeout, TimeUnit.MILLISECONDS);
    }

    public void clear() {
        records.clear();
    }

    public void wakeup() {
        consumer.wakeup();
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
                InfoData infoData = MAPPER.readValue(recordValue, InfoData.class);
                if (infoData != null) {
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
