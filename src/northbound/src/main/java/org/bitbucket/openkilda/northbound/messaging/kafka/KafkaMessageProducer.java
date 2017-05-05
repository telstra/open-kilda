package org.bitbucket.openkilda.northbound.messaging.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

/**
 * Kafka message producer.
 */
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
     * @param topic   kafka topic
     * @param message kafka message
     */
    public void send(final String topic, final String message) {
        ListenableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, message);
        future.addCallback(new ListenableFutureCallback<SendResult<String, String>>() {

            @Override
            public void onSuccess(SendResult<String, String> result) {
                logger.debug("message sent: topic={}, message={}", topic, message);
            }

            @Override
            public void onFailure(Throwable exception) {
                logger.error("unable to send message: topic={}, message={}", topic, message, exception);
            }
        });
    }
}
