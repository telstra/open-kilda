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
import static org.openkilda.messaging.error.ErrorType.INTERNAL_ERROR;
import static org.openkilda.messaging.error.ErrorType.OPERATION_TIMED_OUT;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.northbound.messaging.MessageConsumer;

import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;

/**
 * Kafka message consumer.
 */
@Component
@PropertySource("classpath:northbound.properties")
public class KafkaMessageConsumer implements MessageConsumer<Message> {
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

    @Value("${northbound.messages.expiration.minutes}")
    private int expiredTime;

    @Value("#{kafkaTopicsConfig.getNorthboundTopic()}")
    private String northboundTopic;

    /**
     * Messages map.
     */
    private Map<String, Message> messages;

    @PostConstruct
    public void setUp() {
        messages = new PassiveExpiringMap<>(expiredTime, TimeUnit.MINUTES, new ConcurrentHashMap<String, Message>());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Message poll(final String correlationId) {
        try {
            for (int i = POLL_TIMEOUT / POLL_PAUSE; i < POLL_TIMEOUT; i += POLL_PAUSE) {
                if (messages.containsKey(correlationId)) {
                    return messages.remove(correlationId);
                }
                Thread.sleep(POLL_PAUSE);
            }
        } catch (InterruptedException exception) {
            logger.error("{}: {}={}", INTERRUPTED_ERROR_MESSAGE, CORRELATION_ID, correlationId);
            throw new MessageException(correlationId, System.currentTimeMillis(),
                    INTERNAL_ERROR, INTERRUPTED_ERROR_MESSAGE, northboundTopic);
        }
        logger.error("{}: {}={}", TIMEOUT_ERROR_MESSAGE, CORRELATION_ID, correlationId);
        throw new MessageException(correlationId, System.currentTimeMillis(),
                OPERATION_TIMED_OUT, TIMEOUT_ERROR_MESSAGE, northboundTopic);
    }

    @Override
    public void onResponse(Message message) {
        messages.put(message.getCorrelationId(), message);
    }
}
