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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class KafkaOffsetRegistryTest {
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

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
    @SuppressWarnings("unchecked")
    public void shouldCommitOnAddIfIntervalPassed() throws InterruptedException {
        // given
        KafkaConsumer<String, String> consumer = mock(KafkaConsumer.class);
        consumer.commitSync(anyObject(Map.class));
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
    @SuppressWarnings("unchecked")
    public void shouldCommitOffsetsIfRequested() {
        // given
        KafkaConsumer<String, String> consumer = mock(KafkaConsumer.class);
        consumer.commitSync(anyObject(Map.class));
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

    @Test
    public void failTryingToAddRecordWithOutdatedOffset() {
        // given
        @SuppressWarnings("unchecked")
        KafkaConsumer<String, String> consumer = mock(KafkaConsumer.class);
        Consumer.KafkaOffsetRegistry registry = new KafkaOffsetRegistry(consumer, 10000L);

        ConsumerRecord<String, String> record = new ConsumerRecord<>("test", 1, 10, "key", "value");
        registry.addAndCommit(record);

        expectedException.expect(IllegalArgumentException.class);

        // when
        ConsumerRecord<String, String> outdated = new ConsumerRecord<>("test", 1, 1, "key2", "value2");
        registry.addAndCommit(outdated);

        // then an IllegalArgumentException is thrown
    }
}
