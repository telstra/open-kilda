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

package org.openkilda.topology.messaging.kafka;

import static org.openkilda.messaging.Utils.MAPPER;
import static org.openkilda.messaging.error.ErrorType.DATA_INVALID;

import org.openkilda.messaging.error.MessageException;

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
