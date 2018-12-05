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

package org.openkilda.floodlight.service.kafka;

import org.openkilda.messaging.ctrl.KafkaBreakTarget;
import org.openkilda.messaging.ctrl.KafkaBreakTrigger;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

// TODO(surabujin): avoid usage, replace with more correct network interrupting method
public class TestAwareKafkaProducerService extends KafkaProducerService {
    private static final Logger logger = LoggerFactory.getLogger(TestAwareKafkaProducerService.class);

    private final KafkaBreakTrigger breakTrigger = new KafkaBreakTrigger(KafkaBreakTarget.FLOODLIGHT_PRODUCER);

    @Override
    protected SendStatus produce(ProducerRecord<String, String> record, Callback callback) {
        if (!breakTrigger.isCommunicationEnabled()) {
            logger.info("Suppress record : {} <= {}", record.topic(), record.value());
            return new SendStatus(new FakeProducerFuture(record));
        }
        return super.produce(record, callback);
    }

    public KafkaBreakTrigger getBreakTrigger() {
        return breakTrigger;
    }

    private static class FakeProducerFuture implements Future<RecordMetadata> {
        private final RecordMetadata metadata;

        public FakeProducerFuture(ProducerRecord<String, String> record) {
            this.metadata = new RecordMetadata(
                    new TopicPartition(record.topic(), 0), 0, 1, -1, 0,
                    record.key() != null ? record.key().length() : 0,
                    record.value().length());
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public RecordMetadata get() {
            return metadata;
        }

        @Override
        public RecordMetadata get(long timeout, TimeUnit unit) {
            return get();
        }
    }
}
