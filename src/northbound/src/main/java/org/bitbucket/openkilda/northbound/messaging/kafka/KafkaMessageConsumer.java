package org.bitbucket.openkilda.northbound.messaging.kafka;

import static org.bitbucket.openkilda.messaging.error.ErrorType.INTERNAL_ERROR;
import static org.bitbucket.openkilda.messaging.error.ErrorType.OPERATION_TIMED_OUT;
import static org.bitbucket.openkilda.northbound.utils.Constants.CORRELATION_ID;

import org.bitbucket.openkilda.messaging.Message;
import org.bitbucket.openkilda.northbound.utils.NorthboundException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kafka message consumer.
 */
@Component
@PropertySource("classpath:northbound.properties")
public class KafkaMessageConsumer {
    /**
     * Kafka message queue poll timeout.
     */
    private static final int POLL_TIMEOUT = 5000;

    /**
     * Kafka message queue poll pause.
     */
    private static final int POLL_PAUSE = 100;

    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageConsumer.class);

    /**
     * Messages map.
     */
    private volatile Map<String, Object> messages = new ConcurrentHashMap<>();

    /**
     * Object mapper.
     */
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Receives messages from WorkFlowManager queue.
     *
     * @param record the message object instance
     */
    @KafkaListener(topics = "${kafka.topic.wfm.nb}")
    public void receive(final String record) {
        logger.debug("message received: {}", record);
        try {
            Message message = objectMapper.readValue(record, Message.class);
            messages.put(message.getCorrelationId(), message);
        } catch (IOException exception) {
            logger.error("Could not deserialize message: {}", record, exception);
        }
    }

    /**
     * Polls Kafka message queue.
     *
     * @param correlationId correlation id
     * @return received message
     */
    public Object poll(final String correlationId) {
        try {
            for (int i = POLL_TIMEOUT / POLL_PAUSE; i < POLL_TIMEOUT; i += POLL_PAUSE) {
                if (messages.containsKey(correlationId)) {
                    return messages.remove(correlationId);
                }
                Thread.sleep(POLL_PAUSE);
            }
        } catch (InterruptedException exception) {
            logger.error("Unable to poll message: {}={}", CORRELATION_ID, correlationId);
            throw new NorthboundException(INTERNAL_ERROR, System.currentTimeMillis());
        }
        throw new NorthboundException(OPERATION_TIMED_OUT, System.currentTimeMillis());
    }
}
