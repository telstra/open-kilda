package org.openkilda.floodlight.kafka;

import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.messaging.Message;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class Producer {
    private static final Logger logger = LoggerFactory.getLogger(Producer.class);

    private final org.apache.kafka.clients.producer.Producer<String, String> producer;
    private Map<String, KafkaTopicOrderDescriptor> guaranteedOrder = new HashMap<>();

    public Producer(Context context) {
        this(new KafkaProducer<>(context.getKafkaConfig()));
    }

    Producer(org.apache.kafka.clients.producer.Producer<String, String> producer) {
        this.producer = producer;
    }

    /**
     * Enable guaranteed message order for topic.
     */
    public synchronized void enableGuaranteedOrder(String topic) {
        logger.debug("Enable predictable order for topic {}", topic);
        getOrderDescriptor(topic).enable();
    }

    /**
     * Disable guaranteed message order for topic.
     */
    public synchronized void disableGuaranteedOrder(String topic) {
        logger.debug(
                "Disable predictable order for topic {} (due to effect of transition period some future messages will "
                + "be forced to have predictable order)", topic);
        getOrderDescriptor(topic).disable();
    }

    /**
     * Disable guaranteed message order for topic, with defined transition period.
     */
    public synchronized void disableGuaranteedOrder(String topic, long transitionPeriod) {
        logger.debug(
                "Disable predictable order for topic {} (transition period {} ms)", topic, transitionPeriod);
        getOrderDescriptor(topic).disable(transitionPeriod);
    }

    /**
     * Send message into topic.
     */
    public void handle(String topic, Message payload) {
        try {
            String messageString = MAPPER.writeValueAsString(payload);
            send(topic, messageString);
        } catch (JsonProcessingException e) {
            logger.error("Can not serialize message: {}", payload, e);
        }
    }

    protected void send(String topic, String jsonPayload) {
        // (crimi) - uncomment for development only .. some messages (stats) fill up the log too quickly
        logger.debug("Posting: topic={}, message={}", topic, jsonPayload);
        ProducerRecord<String, String> record;

        KafkaTopicOrderDescriptor orderDescriptor = getOrderDescriptor(topic);
        if (orderDescriptor.isEnabled()) {
            logger.debug("Topic {} forces predictable message ordering", topic);
            record = new ProducerRecord<>(topic, orderDescriptor.getPartition(), null, jsonPayload);
        } else {
            record = new ProducerRecord<>(topic, jsonPayload);
        }

        producer.send(record);
    }

    private KafkaTopicOrderDescriptor getOrderDescriptor(String topic) {
        return guaranteedOrder.computeIfAbsent(
                topic, t ->  new KafkaTopicOrderDescriptor(producer, t));
    }
}
