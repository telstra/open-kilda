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

    private final KafkaProducer<String, String> producer;
    private Map<String, KafkaTopicOrderDescriptor> guaranteedOrder = new HashMap<>();

    public Producer(Context context) {
        producer = new KafkaProducer<>(context.getKafkaConfig());
    }

    public void enableGuaranteedOrder(String topic) {
        logger.debug("Enable predictable order for topic {}", topic);
        getOrderDescriptor(topic).enable();
    }

    public void disableGuaranteedOrder(String topic) {
        logger.debug("Disable predictable order for topic {} (due to fanout period some future messages will be forced to have predictable order)", topic);
        getOrderDescriptor(topic).disable();
    }

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
            logger.debug("Topic %s forces predictable message ordering", topic);
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
