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

import static org.openkilda.messaging.Utils.HEALTH_CHECK_NON_OPERATIONAL_STATUS;
import static org.openkilda.messaging.Utils.HEALTH_CHECK_OPERATIONAL_STATUS;
import static org.openkilda.messaging.Utils.MAPPER;
import static org.openkilda.messaging.error.ErrorType.DATA_INVALID;
import static org.openkilda.messaging.error.ErrorType.INTERNAL_ERROR;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.MessageException;
import org.openkilda.northbound.messaging.MessageProducer;
import org.openkilda.northbound.service.HealthCheckService;

import com.fasterxml.jackson.core.JsonProcessingException;
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
public class KafkaMessageProducer implements MessageProducer {
    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageProducer.class);
    /**
     * Kafka template.
     */
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private HealthCheckService healthCheckService;

    /**
     * {@inheritDoc}
     */
    @Override
    public void send(final String topic, final Message message) {
        ListenableFuture<SendResult<String, String>> future;
        String messageToSend;

        try {
            messageToSend = MAPPER.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            String errorMessage = "Unable to serialize object";
            logger.error("{}: object={}", errorMessage, message, exception);
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    DATA_INVALID, errorMessage, message.toString());
        }

        future = kafkaTemplate.send(topic, messageToSend);
        future.addCallback(new ListenableFutureCallback<SendResult<String, String>>() {
            @Override
            public void onSuccess(SendResult<String, String> result) {
                healthCheckService.updateKafkaStatus(HEALTH_CHECK_OPERATIONAL_STATUS);
                logger.debug("Message sent: topic={}, message={}", topic, messageToSend);
            }

            @Override
            public void onFailure(Throwable exception) {
                healthCheckService.updateKafkaStatus(HEALTH_CHECK_NON_OPERATIONAL_STATUS);
                logger.error("Unable to send message: topic={}, message={}", topic, messageToSend, exception);
            }
        });

        try {
            SendResult<String, String> result = future.get(TIMEOUT, TimeUnit.MILLISECONDS);
            logger.debug("Record sent: record={}, metadata={}", result.getProducerRecord(), result.getRecordMetadata());
        } catch (TimeoutException | ExecutionException | InterruptedException exception) {
            String errorMessage = "Unable to send message";
            logger.error("{}: topic={}, message={}", errorMessage, topic, message, exception);
            throw new MessageException(message.getCorrelationId(), System.currentTimeMillis(),
                    INTERNAL_ERROR, errorMessage, messageToSend);
        }
    }
}
