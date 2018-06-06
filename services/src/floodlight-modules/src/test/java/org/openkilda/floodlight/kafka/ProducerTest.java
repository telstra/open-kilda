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

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMockSupport;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

public class ProducerTest extends EasyMockSupport {
    private static final String TOPIC = "A";

    private org.apache.kafka.clients.producer.Producer<String, String> kafkaProducer;
    private Producer subject;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        kafkaProducer = strictMock(org.apache.kafka.clients.producer.Producer.class);
        subject = new Producer(kafkaProducer);
    }

    @Test
    public void partitionSpreading() {
        TopicPartition[] partitions = new TopicPartition[]{
                new TopicPartition(TOPIC, 0),
                new TopicPartition(TOPIC, 1)
        };
        RecordMetadata[] sendResults = new RecordMetadata[]{
                new RecordMetadata(partitions[0], -1L, 0L, System.currentTimeMillis(), 0, 0, 0),
                new RecordMetadata(partitions[1], -1L, 0L, System.currentTimeMillis(), 0, 0, 0),
                new RecordMetadata(partitions[0], -1L, 0L, System.currentTimeMillis(), 0, 0, 0),
                new RecordMetadata(partitions[0], -1L, 0L, System.currentTimeMillis(), 0, 0, 0),
                new RecordMetadata(partitions[1], -1L, 0L, System.currentTimeMillis(), 0, 0, 0),
                new RecordMetadata(partitions[0], -1L, 0L, System.currentTimeMillis(), 0, 0, 0),
        };

        ArrayList<PartitionInfo> partitionsForResult = new ArrayList<>(2);
        for (TopicPartition p : partitions) {
            partitionsForResult.add(new PartitionInfo(p.topic(), p.partition(), null, null, null));
        }
        expect(kafkaProducer.partitionsFor(TOPIC)).andReturn(partitionsForResult).anyTimes();

        Capture<ProducerRecord<String, String>> sendArguments = Capture.newInstance(CaptureType.ALL);
        setupSendCapture(sendArguments, sendResults);

        replay(kafkaProducer);

        subject.send(TOPIC, "before");
        subject.send(TOPIC, "before");
        subject.enableGuaranteedOrder(TOPIC);
        try {
            subject.send(TOPIC, "order");
            subject.send(TOPIC, "order");
        } finally {
            subject.disableGuaranteedOrder(TOPIC, 0L);
        }
        subject.send(TOPIC, "after");
        subject.send(TOPIC, "after");

        verify(kafkaProducer);

        List<ProducerRecord<String, String>> values = sendArguments.getValues();
        for (int i = 0; i < values.size(); i++) {
            ProducerRecord<String, String> record = values.get(i);
            String asserDetails = String.format(
                    "%d: Invalid partition argument for message \"%s\" - %s", i, record.value(), record.partition());

            if ("order".equals(record.value())) {
                Assert.assertNotNull(asserDetails, record.partition());
            } else {
                Assert.assertNull(asserDetails, record.partition());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void setupSendCapture(Capture<ProducerRecord<String, String>> trap, RecordMetadata[] sendResults) {
        for (RecordMetadata metadata : sendResults) {
            Future promise = mock(Future.class);
            replay(promise);
            expect(kafkaProducer.send(capture(trap))).andReturn(promise);
        }
    }
}
