package org.bitbucket.openkilda.northbound.messaging.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.PropertySource;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;

/**
 * Kafka message consumer.
 */
@Component
@PropertySource("classpath:northbound.properties")
public class KafkaMessageConsumer {
    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageConsumer.class);

    private CountDownLatch latch = new CountDownLatch(1);

    /**
     * Receives messages from WorkFlowManager queue.
     *
     * @param message the message object instance
     */
    @KafkaListener(topics = "${kafka.topic.wfm.nb}")
    public void receive(String message) {
        logger.debug("message received: {}", message);
        latch.countDown();
    }
}
