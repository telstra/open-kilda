package org.bitbucket.openkilda.wfm.topology;

import static org.bitbucket.openkilda.messaging.Utils.MAPPER;

import org.bitbucket.openkilda.messaging.Destination;
import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.messaging.command.CommandData;
import org.bitbucket.openkilda.messaging.command.CommandMessage;
import org.bitbucket.openkilda.messaging.error.ErrorData;
import org.bitbucket.openkilda.messaging.error.ErrorMessage;
import org.bitbucket.openkilda.messaging.info.InfoData;
import org.bitbucket.openkilda.messaging.info.InfoMessage;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import sun.security.krb5.internal.crypto.Des;

import java.io.IOException;
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
    private final Destination destination;

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
            Message message = MAPPER.readValue(recordValue, Message.class);
            if (message instanceof InfoMessage) {
                InfoData data = ((InfoMessage) message).getData();
                if (destination.equals(data.getDestination())) {
                    result = true;
                }
            } else if (message instanceof CommandMessage) {
                CommandData data = ((CommandMessage) message).getData();
                if (destination.equals(data.getDestination())) {
                    result = true;
                }
            } else if (message instanceof ErrorMessage) {
                ErrorData data = ((ErrorMessage) message).getData();
                if (destination.equals(data.getDestination())) {
                    result = true;
                }
            }
        } catch (IOException exception) {
            System.out.println(String.format("Can not deserialize %s with destination %s ", recordValue, destination));
        }
        return result;
    }
}
