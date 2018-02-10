package org.openkilda.atdd.floodlight;

import org.openkilda.messaging.ctrl.KafkaBreakTarget;
import org.openkilda.messaging.ctrl.KafkaBreakerAction;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.ExecutionException;

public class KafkaBreaker {
    private static final Logger logger = LoggerFactory.getLogger(KafkaBreaker.class);

    private int MAX_ATTEMPTS = 3;

    private KafkaProducer<String, String> producer;

    public KafkaBreaker(Properties kafkaConfig) {
        producer = new KafkaProducer<>(kafkaConfig);
    }

    public void shutoff(KafkaBreakTarget target) throws KafkaBreakException {
        setState(target, KafkaBreakerAction.TERMINATE);
    }

    public void restore(KafkaBreakTarget target) throws KafkaBreakException {
        setState(target, KafkaBreakerAction.RESTORE);
    }

    private void setState(KafkaBreakTarget target, KafkaBreakerAction action) throws KafkaBreakException {
        String topic;

        switch (target) {
            case FLOODLIGHT_CONSUMER:
            case FLOODLIGHT_PRODUCER:
                topic = "kilda.speaker";  // FIXME(surabujin) - wait till @nmarchenko push kafka related stuff for ATDD
                break;
            default:
                throw new KafkaBreakException(String.format("Unsupported target: %s", target.toString()));
        }

        ProducerRecord<String, String> record = new ProducerRecord<>(
                topic, target.toString(), action.toString());
        try {
            for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt += 1) try {
                logger.debug(
                        "Send {} to {} ({} of {}})",
                        record.value(), target.toString(), attempt + 1, MAX_ATTEMPTS);
                producer.send(record).get();
                break;
            } catch (InterruptedException e) {
                logger.warn("producer was interrupted (attempts {} of {})", attempt, MAX_ATTEMPTS);
            }
        } catch (ExecutionException e) {
            throw new KafkaBreakException("Unable to publish control message", e);
        }
    }
}
