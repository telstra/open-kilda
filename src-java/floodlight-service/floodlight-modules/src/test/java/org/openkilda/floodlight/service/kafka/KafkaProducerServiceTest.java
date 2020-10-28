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

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import org.openkilda.floodlight.service.zookeeper.ZooKeeperService;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.PortChangeType;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.model.SwitchId;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.easymock.IAnswer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class KafkaProducerServiceTest extends EasyMockSupport {
    private static final String TOPIC = "A";
    private static final TopicPartition[] partitions = new TopicPartition[]{
            new TopicPartition(TOPIC, 0),
            new TopicPartition(TOPIC, 1)
    };


    private KafkaProducerService subject;

    @SuppressWarnings("unchecked")
    private Producer<String, String> kafkaProducer = (Producer<String, String>) strictMock(Producer.class);

    @Before
    public void setUp() {
        injectMocks(this);

        FloodlightModuleContext moduleContext = new FloodlightModuleContext();

        KafkaUtilityService kafkaUtility = createMock(KafkaUtilityService.class);
        expect(kafkaUtility.makeProducer()).andReturn(kafkaProducer);
        moduleContext.addService(KafkaUtilityService.class, kafkaUtility);
        moduleContext.addService(ZooKeeperService.class, createMock(ZooKeeperService.class));

        replay(kafkaUtility);

        subject = new KafkaProducerService();
        subject.setup(moduleContext);

        ArrayList<PartitionInfo> partitionsForResult = new ArrayList<>(2);
        for (TopicPartition p : partitions) {
            partitionsForResult.add(new PartitionInfo(p.topic(), p.partition(), null, null, null));
        }
        expect(kafkaProducer.partitionsFor(TOPIC)).andReturn(partitionsForResult).anyTimes();
    }

    @Test
    public void errorReporting() throws Exception {
        final ExecutionException error = new ExecutionException("Emulate kafka send error", new IOException());

        Future promise = mock(Future.class);
        expect(promise.get()).andThrow(error).anyTimes();
        replay(promise);
        expect(kafkaProducer.send(anyObject(), anyObject(Callback.class)))
                .andAnswer(new IAnswer<Future<RecordMetadata>>() {
                    @Override
                    public Future<RecordMetadata> answer() {
                        Callback callback = (Callback) getCurrentArguments()[1];
                        callback.onCompletion(null, error);
                        return promise;
                    }
                });

        replay(kafkaProducer);
        subject.sendMessageAndTrack(TOPIC, makePayload());
        verify(kafkaProducer);

        // This test does not do any assertions, because the only action is log message with error
        // you can locate this message in test's output.
    }

    @Test
    public void errorDetection() throws Exception {
        Future promise = mock(Future.class);
        final ExecutionException error = new ExecutionException("Emulate kafka send error", new IOException());
        expect(promise.get()).andThrow(error).anyTimes();
        replay(promise);

        expect(kafkaProducer.send(anyObject(), anyObject(Callback.class))).andReturn(promise);

        replay(kafkaProducer);
        SendStatus status = subject.sendMessage(TOPIC, makePayload());
        verify(kafkaProducer);

        Boolean isThrown;
        try {
            status.waitTillComplete();
            isThrown = false;
        } catch (ExecutionException e) {
            isThrown = true;
        }
        Assert.assertTrue(String.format(
                "Exception was not thrown by %s object", status.getClass().getCanonicalName()), isThrown);
    }

    private InfoMessage makePayload() {
        return new InfoMessage(
                new PortInfoData(new SwitchId("ff:fe:00:00:00:00:00:01"), 8, PortChangeType.UP),
                System.currentTimeMillis(), getClass().getCanonicalName() + "-test");
    }

    @SuppressWarnings("unchecked")
    private void setupSendCapture(Capture<ProducerRecord<String, String>> trap, RecordMetadata[] sendResults)
            throws Exception {
        for (RecordMetadata metadata : sendResults) {
            Future promise = mock(Future.class);
            expect(promise.get()).andReturn(metadata);
            replay(promise);

            expect(kafkaProducer.send(EasyMock.capture(trap), anyObject(Callback.class)))
                    .andReturn(promise);
        }
    }
}
