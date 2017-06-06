package org.bitbucket.openkilda.wfm.topology;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;

import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Created by atopilin on 20/04/2017.
 */
public class TestKafkaConsumer extends Thread {
    private static final long CONSUMER_QUEUE_OFFER_TIMEOUT = 1000;
    private static final long KAFKA_MESSAGE_POLL_TIMEOUT = 10000;
    private static final long KAFKA_CONSUMER_POLL_TIMEOUT = 100;
    private volatile BlockingQueue<ConsumerRecord<String, String>> records = new ArrayBlockingQueue<>(100);
    private final KafkaConsumer<String, String> consumer;
    private final String topic;

    public TestKafkaConsumer(final String topic, final Properties properties) {
        this.consumer = new KafkaConsumer<>(properties);
        this.topic = topic;
    }

    public void run() {
        System.out.println("Starting Kafka Consumer for " + topic);
        consumer.subscribe(Collections.singletonList(topic));
        try {
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(KAFKA_CONSUMER_POLL_TIMEOUT);
                for (ConsumerRecord<String, String> record : records) {
                    this.records.offer(record, CONSUMER_QUEUE_OFFER_TIMEOUT, TimeUnit.MILLISECONDS);
                    consumer.commitSync();
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
}
