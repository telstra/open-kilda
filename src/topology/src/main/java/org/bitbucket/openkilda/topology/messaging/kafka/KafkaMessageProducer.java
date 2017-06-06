package org.bitbucket.openkilda.topology.messaging.kafka;

import static org.bitbucket.openkilda.messaging.Utils.MAPPER;
import static org.bitbucket.openkilda.messaging.error.ErrorType.DATA_INVALID;

import org.bitbucket.openkilda.messaging.error.MessageException;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFutureCallback;

/**
 * Kafka message producer.
 */
@Component
@PropertySource("classpath:topology.properties")
public class KafkaMessageProducer {
    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageProducer.class);
    /**
     * Kafka template.
     */
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Sends messages to WorkFlowManager.
     *
     * @param topic  kafka topic
     * @param object object to serialize and send
     */
    public void send(final String topic, final Object object) {
        String message;

        try {
            message = MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException exception) {
            logger.error("Unable to serialize object: object={}", object, exception);
            throw new MessageException(DATA_INVALID, System.currentTimeMillis());
        }

        kafkaTemplate.send(topic, message).addCallback(new ListenableFutureCallback<SendResult<String, String>>() {
            @Override
            public void onSuccess(SendResult<String, String> result) {
                logger.debug("Message sent: topic={}, message={}", topic, message);
            }

            @Override
            public void onFailure(Throwable exception) {
                logger.error("Unable to send message: topic={}, message={}", topic, message, exception);
            }
        });
    }
}
