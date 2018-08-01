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

import org.openkilda.floodlight.kafka.producer.Producer;
import org.openkilda.floodlight.kafka.producer.SendStatus;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.ctrl.KafkaBreakTarget;
import org.openkilda.messaging.ctrl.KafkaBreakTrigger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO(surabujin): avoid usage, replace with more correct network interrupting method
public class TestAwareProducer extends Producer {
    private static final Logger logger = LoggerFactory.getLogger(TestAwareProducer.class);

    private KafkaBreakTrigger breakTrigger;

    public TestAwareProducer(KafkaProducerConfig kafkaConfig) {
        super(kafkaConfig);

        breakTrigger = new KafkaBreakTrigger(KafkaBreakTarget.FLOODLIGHT_PRODUCER);
    }

    @Override
    public void sendMessageAndTrack(String topic, Message message) {
        if (!breakTrigger.isCommunicationEnabled()) {
            logger.info("Suppress record : {} <= {}", topic, message);
            return;
        }
        super.sendMessageAndTrack(topic, message);
    }

    @Override
    public SendStatus sendMessage(String topic, Message message) {
        if (!breakTrigger.isCommunicationEnabled()) {
            throw new IllegalArgumentException(String.format(
                    "Can't emulate behaviour of %s.sendMessage in network outage state",
                    getClass().getCanonicalName()));
        }
        return super.sendMessage(topic, message);
    }

    public KafkaBreakTrigger getBreakTrigger() {
        return breakTrigger;
    }
}
