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

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;

import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.model.PingData;
import org.openkilda.floodlight.pathverification.PathVerificationService;
import org.openkilda.floodlight.service.PingService;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.floodlight.response.PingResponse;
import org.openkilda.messaging.info.InfoMessage;

import net.floodlightcontroller.core.IFloodlightProviderService;
import org.junit.Assert;
import org.junit.Test;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.List;
import java.util.UUID;

public class PingResponseCommandTest extends AbstractTest {
    @Test
    public void success() throws Exception {
        final IFloodlightProviderService providerService = createMock(IFloodlightProviderService.class);
        moduleContext.addService(IFloodlightProviderService.class, providerService);
        providerService.addOFMessageListener(anyObject(), anyObject());

        final IPingResponseFactory responseFactory = createMock(IPingResponseFactory.class);
        final PingService realPingService = new PingService(commandContextFactory);
        expect(pingService.getSignature()).andDelegateTo(realPingService);

        replayAll();

        final DatapathId dpIdAlpha = DatapathId.of(0xfffe0000000001L);
        final DatapathId dpIdBeta = DatapathId.of(0xfffe0000000002L);
        final PingData payload = new PingData((short) 0x100, dpIdAlpha, dpIdBeta, UUID.randomUUID());

        moduleContext.addConfigParam(new PathVerificationService(), "hmac256-secret", "secret");
        realPingService.init(moduleContext, responseFactory);

        byte[] signedPayload = realPingService.getSignature().sign(payload);

        final CommandContext context = commandContextFactory.produce();
        final PingResponseCommand command = new PingResponseCommand(context, dpIdBeta, 1L, signedPayload);
        command.execute();

        final List<Message> replies = producerPostMessage.getValues();
        Assert.assertEquals(1, replies.size());
        InfoMessage response = (InfoMessage) replies.get(0);
        PingResponse pingResponse = (PingResponse) response.getData();

        Assert.assertNull(pingResponse.getError());
        Assert.assertNotNull(pingResponse.getMeters());
        Assert.assertEquals(payload.getPingId(), pingResponse.getPingId());
    }
}
