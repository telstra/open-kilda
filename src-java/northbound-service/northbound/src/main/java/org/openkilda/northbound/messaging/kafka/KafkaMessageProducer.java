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

import org.openkilda.messaging.Message;
import org.openkilda.northbound.messaging.MessageProducer;
import org.openkilda.northbound.service.HealthCheckService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

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
    private KafkaTemplate<String, Message> kafkaTemplate;

    @Autowired
    private HealthCheckService healthCheckService;

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<SendResult<String, Message>> send(final String topic, final Message message) {
        CompletableFuture<SendResult<String, Message>> future =
                kafkaTemplate.send(topic, message.getCorrelationId(), message);
        future.whenComplete((success, error) -> {
            if (error == null) {
                healthCheckService.updateKafkaStatus(HEALTH_CHECK_OPERATIONAL_STATUS);
                logger.debug("Message sent: topic={}, message={}", topic, message);
            } else {
                healthCheckService.updateKafkaStatus(HEALTH_CHECK_NON_OPERATIONAL_STATUS);
                logger.error("Unable to send message: topic={}, message={}", topic, message, error);
            }
        });

        return future;
    }
}
