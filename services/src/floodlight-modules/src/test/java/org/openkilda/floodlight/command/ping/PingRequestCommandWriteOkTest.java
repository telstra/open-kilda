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
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;

import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.model.OfBatchResult;
import org.openkilda.floodlight.pathverification.PathVerificationService;
import org.openkilda.floodlight.service.PingService;
import org.openkilda.floodlight.service.batch.OfBatchService;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.model.Ping;
import org.openkilda.messaging.model.Ping.Errors;

import net.floodlightcontroller.core.IFloodlightProviderService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.types.U64;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class PingRequestCommandWriteOkTest extends PingRequestCommandAbstractTest {
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        final IFloodlightProviderService providerService = createMock(IFloodlightProviderService.class);
        moduleContext.addService(IFloodlightProviderService.class, providerService);
        providerService.addOFMessageListener(anyObject(), anyObject());

        final PingService realPingService = new PingService(commandContextFactory);
        expect(pingService.getSignature()).andDelegateTo(realPingService);
        expect(pingService.wrapData(anyObject(Ping.class), anyObject())).andDelegateTo(realPingService);

        expect(switchAlpha.getLatency()).andReturn(U64.of(1L)).anyTimes();
        expect(switchBeta.getLatency()).andReturn(U64.of(2L)).anyTimes();
        expect(switchNotCapable.getLatency()).andReturn(U64.of(3L)).anyTimes();

        @SuppressWarnings("unchecked")
        Future<OfBatchResult> futureMock = createMock(Future.class);
        expect(futureMock.get(PingService.SWITCH_PACKET_OUT_TIMEOUT, TimeUnit.MILLISECONDS)).andReturn(null);

        final OfBatchService batchService = createMock(OfBatchService.class);
        moduleContext.addService(OfBatchService.class, batchService);
        expect(batchService.write(anyObject())).andReturn(futureMock);

        final IPingResponseFactory responseFactory = createMock(IPingResponseFactory.class);

        replayAll();

        moduleContext.addConfigParam(new PathVerificationService(), "hmac256-secret", "secret");
        realPingService.init(moduleContext, responseFactory);
    }

    @Test
    public void normalWorkflow() throws Exception {
        expectSuccess(makePing(switchAlpha, switchBeta));
    }

    @Test
    public void sourceSwitchIsNotCapable() throws Exception {
        expectSuccess(makePing(switchNotCapable, switchBeta));
    }

    @Test
    public void writeTimeout() throws Exception {
        OfBatchService batchService = moduleContext.getServiceImpl(OfBatchService.class);
        Future<OfBatchResult> defaultFutureMock = batchService.write(null);
        reset(defaultFutureMock);  // deactivate futureMock created in setUp method
        reset(batchService);

        @SuppressWarnings("unchecked")
        Future<OfBatchResult> futureMock = createStrictMock(Future.class);
        expect(futureMock.get(PingService.SWITCH_PACKET_OUT_TIMEOUT, TimeUnit.MILLISECONDS))
                .andThrow(new TimeoutException("force timeout on future object"));
        expect(futureMock.cancel(false)).andReturn(true);

        expect(batchService.write(anyObject())).andReturn(futureMock);

        replay(batchService, futureMock, defaultFutureMock);

        CommandContext context = commandContextFactory.produce();
        Ping ping = makePing(switchAlpha, switchBeta);
        PingRequestCommand command = new PingRequestCommand(context, ping);

        command.execute();

        verifySentErrorResponse(ping, Errors.WRITE_FAILURE);
    }

    private void expectSuccess(Ping ping) {
        CommandContext context = commandContextFactory.produce();
        PingRequestCommand command = new PingRequestCommand(context, ping);

        command.execute();

        List<Message> replies = producerPostMessage.getValues();
        Assert.assertEquals(0, replies.size());
    }
}
