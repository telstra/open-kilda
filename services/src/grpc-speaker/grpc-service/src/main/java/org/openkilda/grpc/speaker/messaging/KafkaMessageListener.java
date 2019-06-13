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

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandMessage;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka message receiver.
 */
@Slf4j
@Component
@KafkaListener(id = "grpc-listener", topics = "#{kafkaTopicsConfig.getGrpcSpeakerTopic()}")
public class KafkaMessageListener {

    @Autowired
    MessageProcessor messageProcessor;

    /**
     * Handles all messages from kafka and sends to corresponding component for further processing.
     *
     * @param message received  message.
     */
    @KafkaHandler
    public void onMessage(CommandMessage message) {
        if (!isValid(message)) {
            log.warn("Skipping invalid message: {}", message);
            return;
        }
        log.debug("Message received: {} - {}", Thread.currentThread().getId(), message);
        messageProcessor.processRequest(message);
    }

    private boolean isValid(Message message) {
        if (StringUtils.isEmpty(message.getCorrelationId())) {
            log.warn("Received message without correlation id: {}", message);
            return false;
        }

        //if (!(message instanceof InfoMessage) && !(message instanceof ErrorMessage)) {
        //    log.warn("Received message has unsupported format: {}", message);
        //    return false;
        //}

        return true;
    }
}
