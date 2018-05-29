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

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.newCapture;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import org.openkilda.floodlight.command.Command;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.Topic;
import org.openkilda.messaging.command.flow.UniFlowVerificationRequest;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowVerificationErrorCode;
import org.openkilda.messaging.info.flow.UniFlowVerificationResponse;

import org.easymock.Capture;
import org.junit.Assert;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.ver12.OFFactoryVer12;

import java.util.List;
import java.util.Optional;

public class VerificationDispatchCommandTest extends AbstractVerificationCommandTest {
    @Test
    public void skipIfInsufficientCapabilities() {
        Capture<Message> captureMessage = newCapture();

        expect(destSwitch.getOFFactory()).andReturn(new OFFactoryVer12()).anyTimes();
        kafkaProducerService.postMessage(eq(Topic.FLOW), capture(captureMessage));

        replay(destSwitch, kafkaProducerService);

        UniFlowVerificationRequest request = makeVerificationRequest();
        VerificationDispatchCommand subject = new VerificationDispatchCommand(context, request);

        List<Optional<Command>> subCommands = subject.produceSubCommands();
        verify(destSwitch);

        Assert.assertEquals("Produced sub operations list is not empty", 0, subCommands.size());

        Message message = captureMessage.getValue();
        Assert.assertTrue(message instanceof InfoMessage);

        InfoData rawPayload = ((InfoMessage)message).getData();
        Assert.assertTrue(rawPayload instanceof UniFlowVerificationResponse);
        Assert.assertEquals(FlowVerificationErrorCode.NOT_CAPABLE, ((UniFlowVerificationResponse) rawPayload).getError());
    }
}
