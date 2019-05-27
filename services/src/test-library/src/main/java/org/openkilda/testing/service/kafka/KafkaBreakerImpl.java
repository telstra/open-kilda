/* Copyright 2018 Telstra Open Source
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

package org.openkilda.testing.service.kafka;

import org.openkilda.messaging.ctrl.KafkaBreakTarget;
import org.openkilda.messaging.ctrl.KafkaBreakerAction;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Properties;
import java.util.concurrent.ExecutionException;

/**
 * Allows to break connection between Floodlight and Kafka.
 */
@Slf4j
@Service
public class KafkaBreakerImpl implements KafkaBreaker {
    @Value("#{kafkaTopicsConfig.getSpeakerRegionTopic()}")
    private String speakerTopic;

    private KafkaProducer<String, String> producer;

    public KafkaBreakerImpl(@Qualifier("kafkaProducerProperties") Properties kafkaConfig) {
        producer = new KafkaProducer<>(kafkaConfig);
    }

    public void shutoff(KafkaBreakTarget target, int region) throws KafkaBreakException {
        setState(target, KafkaBreakerAction.TERMINATE, region);
    }

    public void restore(KafkaBreakTarget target, int region) throws KafkaBreakException {
        setState(target, KafkaBreakerAction.RESTORE, region);
    }

    private void setState(KafkaBreakTarget target, KafkaBreakerAction action, int region) throws KafkaBreakException {
        log.info("Target: {}, region: {}, action: {}", target.toString(), region, action.toString());
        ProducerRecord<String, String> record = new ProducerRecord<>(
                setRegion(speakerTopic, region), target.toString(), action.toString());
        try {
            producer.send(record).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new KafkaBreakException(e.getMessage(), e);
        }
    }

    private String setRegion(String speakerTopic, int region) {
        return String.format("%s_%s", speakerTopic, region);
    }
}
