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

package org.openkilda.floodlight.command.ping;

import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.newCapture;

import org.openkilda.floodlight.KafkaChannel;
import org.openkilda.floodlight.command.AbstractCommandTest;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.service.kafka.KafkaUtilityService;
import org.openkilda.floodlight.service.ping.PingService;
import org.openkilda.messaging.Message;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.Mock;
import org.junit.Before;

public abstract class PingCommandTest extends AbstractCommandTest {
    protected static final String PING_KAFKA_TOPIC = "ping.topic";

    protected Capture<Message> kafkaMessageCatcher = newCapture(CaptureType.ALL);

    @Mock
    protected IKafkaProducerService producerService;

    @Mock
    protected PingService pingService;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        moduleContext.addService(IKafkaProducerService.class, producerService);
        moduleContext.addService(PingService.class, pingService);

        KafkaChannel topics = createMock(KafkaChannel.class);
        expect(topics.getPingTopic()).andReturn(PING_KAFKA_TOPIC).anyTimes();

        KafkaUtilityService kafkaUtility = createMock(KafkaUtilityService.class);
        expect(kafkaUtility.getKafkaChannel()).andReturn(topics).anyTimes();
        moduleContext.addService(KafkaUtilityService.class, kafkaUtility);

        producerService.sendMessageAndTrack(anyString(), capture(kafkaMessageCatcher));
        expectLastCall().andVoid().anyTimes();
    }
}
