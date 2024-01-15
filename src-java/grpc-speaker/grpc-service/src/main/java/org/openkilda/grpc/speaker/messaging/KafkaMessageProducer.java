/* Copyright 2019 Telstra Open Source
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

package org.openkilda.grpc.speaker.messaging;

import static org.openkilda.messaging.Utils.HEALTH_CHECK_NON_OPERATIONAL_STATUS;
import static org.openkilda.messaging.Utils.HEALTH_CHECK_OPERATIONAL_STATUS;

import org.openkilda.grpc.speaker.service.HealthCheckService;
import org.openkilda.messaging.Message;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka message producer.
 */
@Slf4j
@Component
public class KafkaMessageProducer {

    @Autowired
    private KafkaTemplate<String, Message> kafkaTemplate;

    @Autowired
    private HealthCheckService healthCheckService;

    /**
     * Sends message to kafka topic.
     */
    public CompletableFuture<SendResult<String, Message>> send(String topic, String key, Message message) {
        CompletableFuture<SendResult<String, Message>> future = kafkaTemplate.send(topic, key, message);
        future.whenComplete((success, error) -> {
                    if (error == null) {
                        log.debug("Response has been sent: topic={}, message={}", topic, message);
                        healthCheckService.updateKafkaStatus(HEALTH_CHECK_OPERATIONAL_STATUS);
                    } else {
                        log.error("Unable to send message: topic={}, message={}", topic, message, error);
                        healthCheckService.updateKafkaStatus(HEALTH_CHECK_NON_OPERATIONAL_STATUS);
                    }
                }
        );
        return future;
    }
}
