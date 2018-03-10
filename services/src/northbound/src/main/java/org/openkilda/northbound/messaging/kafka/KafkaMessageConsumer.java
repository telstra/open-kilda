/* Copyright 2017 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.northbound.messaging.kafka;

import static org.openkilda.messaging.Utils.CORRELATION_ID;
import static org.openkilda.messaging.Utils.MAPPER;
import static org.openkilda.messaging.Utils.SYSTEM_CORRELATION_ID;
import static org.openkilda.messaging.error.ErrorType.INTERNAL_ERROR;
import static org.openkilda.messaging.error.ErrorType.OPERATION_TIMED_OUT;
import org.openkilda.messaging.Topic;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.northbound.messaging.MessageConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class KafkaMessageConsumer implements MessageConsumer<Object> {
    /**
     * Error description for interrupted exception.
     */
    public static final String INTERRUPTED_ERROR_MESSAGE = "Unable to poll message";

    /**
     * Error description for timeout exception.
     */
    public static final String TIMEOUT_ERROR_MESSAGE = "Timeout for message poll";

    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageConsumer.class);

    /**
     * Messages map.
     */
    private volatile Map<String, Object> messages = new ConcurrentHashMap<>();

    /**
     * Receives messages from WorkFlowManager queue.
     *
     * @param record the message object instance
     */
    @KafkaListener(id = "northbound-listener", topics = Topic.NORTHBOUND)
    public void receive(final String record) {
        try {
            logger.trace("message received");
            Message message = MAPPER.readValue(record, Message.class);
            if (Destination.NORTHBOUND.equals(message.getDestination())) {
                logger.debug("message received: {}", record);
                messages.put(message.getCorrelationId(), message);
            } else {
                logger.trace("Skip message: {}", message);
            }
        } catch (IOException exception) {
            logger.error("Could not deserialize message: {}", record, exception);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object poll(final String correlationId) {
        try {
            for (int i = POLL_TIMEOUT / POLL_PAUSE; i < POLL_TIMEOUT; i += POLL_PAUSE) {
                if (messages.containsKey(correlationId)) {
                    return messages.remove(correlationId);
                } else if (messages.containsKey(SYSTEM_CORRELATION_ID)) {
                    return messages.remove(SYSTEM_CORRELATION_ID);
                }
                Thread.sleep(POLL_PAUSE);
            }
        } catch (InterruptedException exception) {
            logger.error("{}: {}={}", INTERRUPTED_ERROR_MESSAGE, CORRELATION_ID, correlationId);
            throw new MessageException(correlationId, System.currentTimeMillis(),
                    INTERNAL_ERROR, INTERRUPTED_ERROR_MESSAGE, Topic.NORTHBOUND);
        }
        logger.error("{}: {}={}", TIMEOUT_ERROR_MESSAGE, CORRELATION_ID, correlationId);
        throw new MessageException(correlationId, System.currentTimeMillis(),
                OPERATION_TIMED_OUT, TIMEOUT_ERROR_MESSAGE, Topic.NORTHBOUND);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        messages.clear();
    }
}
