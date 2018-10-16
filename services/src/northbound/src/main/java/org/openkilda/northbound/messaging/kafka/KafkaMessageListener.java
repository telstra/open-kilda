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

import org.openkilda.messaging.Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.MDC.MDCCloseable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;

/**
 * Kafka message receiver.
 */
@KafkaListener(id = "northbound-listener", topics = "#{kafkaTopicsConfig.getNorthboundTopic()}")
public class KafkaMessageListener {

    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageListener.class);

    @Autowired
    private KafkaMessagingChannel messagingChannel;

    /**
     * Handles all messages from kafka and sends to corresponding component for further processing.
     * <p/>
     * @param message received message.
     */
    @KafkaHandler
    public void onMessage(Message message) {
        try (MDCCloseable closable = MDC.putCloseable(CORRELATION_ID, message.getCorrelationId())) {
            logger.debug("Message received: {} - {}", Thread.currentThread().getId(), message);
            messagingChannel.onResponse(message);
        }
    }

}
