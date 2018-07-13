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

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.mock;

import org.openkilda.floodlight.kafka.Consumer.KafkaOffsetRegistry;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.easymock.EasyMock;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class KafkaOffsetRegistryTest {
    @Test
    public void shouldNotCommitRightOnAdd() {
        // given
        @SuppressWarnings("unchecked")
        KafkaConsumer<String, String> consumer = mock(KafkaConsumer.class);
        EasyMock.replay(consumer);
        Consumer.KafkaOffsetRegistry registry = new KafkaOffsetRegistry(consumer, 10000L);

        // when
        ConsumerRecord<String, String> record = new ConsumerRecord<>("test", 1, 1, "key", "value");
        registry.addAndCommit(record);

        // then
        EasyMock.verify(consumer);
    }

    @Test
    public void shouldCommitOnAddIfIntervalPassed() throws InterruptedException {
        // given
        @SuppressWarnings("unchecked")
        KafkaConsumer<String, String> consumer = mock(KafkaConsumer.class);
        consumer.commitSync(anyObject());
        EasyMock.expectLastCall();
        EasyMock.replay(consumer);
        Consumer.KafkaOffsetRegistry registry = new KafkaOffsetRegistry(consumer, 1L);

        // when
        TimeUnit.MILLISECONDS.sleep(10);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("test", 1, 1, "key", "value");
        registry.addAndCommit(record);

        // then
        EasyMock.verify(consumer);
    }

    @Test
    public void shouldCommitOffsetsIfRequested() {
        // given
        @SuppressWarnings("unchecked")
        KafkaConsumer<String, String> consumer = mock(KafkaConsumer.class);
        consumer.commitSync(anyObject());
        EasyMock.expectLastCall();
        EasyMock.replay(consumer);
        Consumer.KafkaOffsetRegistry registry = new KafkaOffsetRegistry(consumer, 10000L);

        // when
        ConsumerRecord<String, String> record = new ConsumerRecord<>("test", 1, 1, "key", "value");
        registry.addAndCommit(record);
        registry.commitOffsets();

        // then
        EasyMock.verify(consumer);
    }
}
