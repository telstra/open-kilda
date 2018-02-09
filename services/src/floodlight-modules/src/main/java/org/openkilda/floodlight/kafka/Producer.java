package org.openkilda.floodlight.kafka;

import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.messaging.Message;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Producer {
    private static final Logger logger = LoggerFactory.getLogger(Producer.class);

    private final KafkaProducer<String, String> producer;

    public Producer(Context context) {
        producer = new KafkaProducer<>(context.getKafkaConfig());
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
        producer.send(new ProducerRecord<>(topic, jsonPayload));
    }
}
