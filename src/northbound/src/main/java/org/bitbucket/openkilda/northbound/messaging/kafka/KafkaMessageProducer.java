package org.bitbucket.openkilda.northbound.messaging.kafka;

import static org.bitbucket.openkilda.messaging.error.ErrorType.DATA_INVALID;
import static org.bitbucket.openkilda.messaging.error.ErrorType.INTERNAL_ERROR;

import org.bitbucket.openkilda.northbound.utils.NorthboundException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Kafka message producer.
 */
@Component
@PropertySource("classpath:northbound.properties")
public class KafkaMessageProducer {
    /**
     * Timeout.
     */
    private static int TIMEOUT = 1000;
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
     * Object mapper.
     */
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Sends messages to WorkFlowManager.
     *
     * @param topic kafka topic
     * @param object object to serialize and send
     */
    public void send(final String topic, final Object object) {
        ListenableFuture<SendResult<String, String>> future;
        String message;

        try {
            message = objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException exception) {
            logger.error("Unable to serialize object: object={}", object, exception);
            throw new NorthboundException(DATA_INVALID, System.currentTimeMillis());
        }

        future = kafkaTemplate.send(topic, message);
        future.addCallback(new ListenableFutureCallback<SendResult<String, String>>() {
            @Override
            public void onSuccess(SendResult<String, String> result) {
                logger.debug("Message sent: topic={}, message={}", topic, message);
            }

            @Override
            public void onFailure(Throwable exception) {
                logger.error("Unable to send message: topic={}, message={}", topic, message, exception);
            }
        });

        try {
            SendResult<String, String> result = future.get(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException | InterruptedException exception) {
            logger.error("Unable to send message: topic={}, message={}", topic, message, exception);
            throw new NorthboundException(INTERNAL_ERROR, System.currentTimeMillis());
        }
    }
}
