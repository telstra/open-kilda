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

package org.openkilda.floodlight.kafka;

import org.openkilda.floodlight.kafka.RecordHandler.Factory;
import org.openkilda.floodlight.kafka.producer.Producer;
import org.openkilda.floodlight.switchmanager.ISwitchManager;
import org.openkilda.messaging.ctrl.KafkaBreakTarget;
import org.openkilda.messaging.ctrl.KafkaBreakTrigger;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class TestAwareConsumer extends Consumer {
    private static final Logger logger = LoggerFactory.getLogger(TestAwareConsumer.class);

    private KafkaBreakTrigger breakTrigger;
    private List<KafkaBreakTrigger> expectedTriggers;

    public TestAwareConsumer(ConsumerContext context, KafkaConsumerConfig kafkaConfig, ExecutorService handlersPool,
                             Factory handlerFactory, ISwitchManager switchManager, String topic, String... moreTopics) {
        super(kafkaConfig, handlersPool, handlerFactory, switchManager, topic, moreTopics);

        breakTrigger = new KafkaBreakTrigger(KafkaBreakTarget.FLOODLIGHT_CONSUMER);

        expectedTriggers = new ArrayList<>();
        expectedTriggers.add(breakTrigger);

        Producer producer = context.getKafkaProducer().getProducer();
        if (producer instanceof TestAwareProducer) {
            expectedTriggers.add(((TestAwareProducer) producer).getBreakTrigger());
        }
    }

    @Override
    protected void handle(ConsumerRecord<String, String> record) {
        boolean isHandled = false;
        for (KafkaBreakTrigger trigger : expectedTriggers) {
            if (!trigger.handle(record.key(), record.value())) {
                continue;
            }
            isHandled = true;
            break;
        }

        if (isHandled) {
            return;
        }

        if (!breakTrigger.isCommunicationEnabled()) {
            logger.info("Suppress record - key: {}, value: {}", record.key(), record.value());
            return;
        }

        super.handle(record);
    }
}
