/*
 * Copyright 2017 Telstra Open Source
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openkilda.floodlight.command.flow;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.newCapture;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;

import org.openkilda.floodlight.exc.InsufficientCapabilitiesException;
import org.openkilda.floodlight.model.flow.VerificationData;
import org.openkilda.messaging.Topic;
import org.openkilda.messaging.command.flow.UniFlowVerificationRequest;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowVerificationErrorCode;
import org.openkilda.messaging.info.flow.UniFlowVerificationResponse;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.ver12.OFFactoryVer12;
import org.projectfloodlight.openflow.protocol.ver13.OFFactoryVer13;

import java.util.concurrent.ScheduledExecutorService;

public class VerificationListenCommandTest extends AbstractVerificationCommandTest {
    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        ScheduledExecutorService scheduler = EasyMock.createMock(ScheduledExecutorService.class);
        expect(threadPoolService.getScheduledExecutor()).andReturn(scheduler).anyTimes();
        replay(threadPoolService);

        expect(destSwitch.getOFFactory()).andReturn(new OFFactoryVer13()).anyTimes();
        replay(destSwitch);
    }

    @After
    public void tearDown() {
        verify(threadPoolService, destSwitch);
    }

    @Test(expected = InsufficientCapabilitiesException.class)
    public void lackCapabilities() throws Exception {
        reset(destSwitch);
        expect(destSwitch.getOFFactory()).andReturn(new OFFactoryVer12()).anyTimes();
        replay(destSwitch);

        UniFlowVerificationRequest request = makeVerificationRequest();
        try {
            new VerificationListenCommand(context, request);
        } finally {
            verify(destSwitch);
        }
    }

    @Test
    public void run() throws Exception {
        UniFlowVerificationRequest request = makeVerificationRequest();
        VerificationListenCommand subject = new VerificationListenCommand(context, request);

        flowVerificationService.subscribe(eq(subject));
        expectLastCall().once();
        replay(flowVerificationService);

        subject.run();

        verify(flowVerificationService);
    }

    @Test
    public void packetIn() throws Exception {
        UniFlowVerificationRequest request = makeVerificationRequest();
        VerificationData shouldSkip = VerificationData.of(makeVerificationRequest());
        VerificationData shouldMatch = VerificationData.of(request);

        VerificationListenCommand subject = new VerificationListenCommand(context, request);

        replay(sourceSwitch);
        Assert.assertFalse("False positive VerificationData match", subject.packetIn(destSwitch, shouldSkip));

        kafkaProducerService.postMessage(eq(Topic.FLOW), anyObject());
        expectLastCall().once();
        replay(kafkaProducerService);

        Assert.assertTrue("False negative VerificationData match", subject.packetIn(destSwitch, shouldMatch));

        verify(kafkaProducerService);
    }

    @Test
    public void timeout() throws Exception {
        UniFlowVerificationRequest request = makeVerificationRequest();
        VerificationListenCommand subject = new VerificationListenCommand(context, request);

        VerificationListenCommand.TimeoutNotification timeout = new VerificationListenCommand.TimeoutNotification(
                subject);

        flowVerificationService.unsubscribe(eq(subject));
        expectLastCall().once();
        replay(flowVerificationService);

        Capture<InfoMessage> outputCapture = newCapture(CaptureType.LAST);

        kafkaProducerService.postMessage(eq(Topic.FLOW), capture(outputCapture));
        expectLastCall().once();
        replay(kafkaProducerService);

        timeout.run();

        verify(destSwitch, flowVerificationService, kafkaProducerService);

        InfoMessage output = outputCapture.getValue();
        UniFlowVerificationResponse response = (UniFlowVerificationResponse) output.getData();

        Assert.assertEquals(response.getError(), FlowVerificationErrorCode.TIMEOUT);
    }
}
