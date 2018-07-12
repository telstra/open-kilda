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
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.newCapture;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.resetToStrict;

import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.error.OfBatchException;
import org.openkilda.floodlight.error.OfErrorResponseException;
import org.openkilda.floodlight.model.OfRequestResponse;
import org.openkilda.floodlight.pathverification.PathVerificationService;
import org.openkilda.floodlight.service.batch.OfBatchService;
import org.openkilda.floodlight.service.of.InputService;
import org.openkilda.floodlight.service.ping.PingInputTranslator;
import org.openkilda.floodlight.service.ping.PingService;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.model.Ping;
import org.openkilda.messaging.model.Ping.Errors;

import com.google.common.collect.ImmutableList;
import org.easymock.Capture;
import org.easymock.Mock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFBadRequestCode;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.errormsg.OFBadRequestErrorMsg;
import org.projectfloodlight.openflow.types.U64;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PingRequestCommandWriteOkTest extends PingRequestCommandAbstractTest {
    @Mock
    private ScheduledFuture timeoutFuture;

    @Mock
    private CompletableFuture<List<OfRequestResponse>> writeFuture;

    private Capture<List<OfRequestResponse>> ioPayloadCatch = newCapture();

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        InputService inputService = createMock(InputService.class);
        inputService.addTranslator(eq(OFType.PACKET_IN), anyObject(PingInputTranslator.class));
        moduleContext.addService(InputService.class, inputService);

        expect(scheduler.schedule(
                        anyObject(Runnable.class),
                        eq(PingService.SWITCH_PACKET_OUT_TIMEOUT), eq(TimeUnit.MILLISECONDS)))
                .andReturn(timeoutFuture);

        final PingService realPingService = new PingService();
        expect(pingService.getSignature()).andDelegateTo(realPingService);
        expect(pingService.wrapData(anyObject(Ping.class), anyObject())).andDelegateTo(realPingService);

        expect(switchAlpha.getLatency()).andReturn(U64.of(1L)).anyTimes();
        expect(switchBeta.getLatency()).andReturn(U64.of(2L)).anyTimes();
        expect(switchNotCapable.getLatency()).andReturn(U64.of(3L)).anyTimes();

        expect(writeFuture.exceptionally(anyObject())).andReturn(null);

        final OfBatchService batchService = createMock(OfBatchService.class);
        moduleContext.addService(OfBatchService.class, batchService);
        expect(batchService.write(capture(ioPayloadCatch))).andReturn(writeFuture);

        replayAll();

        moduleContext.addConfigParam(new PathVerificationService(), "hmac256-secret", "secret");
        realPingService.init(moduleContext);
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
        resetToStrict(writeFuture);
        expect(writeFuture.exceptionally(anyObject())).andReturn(null);
        expect(writeFuture.isDone()).andReturn(false);
        expect(writeFuture.cancel(false)).andReturn(false);
        replay(writeFuture);

        final Ping ping = makePing(switchAlpha, switchBeta);
        PingRequestCommand command = makeCommand(ping);

        command.call();
        command.timeout(writeFuture);

        verifySentErrorResponse(ping, Errors.WRITE_FAILURE);
    }

    @Test
    public void errorResponse() throws Exception {
        reset(timeoutFuture);
        expect(timeoutFuture.cancel(false)).andReturn(false);
        replay(timeoutFuture);

        final Ping ping = makePing(switchAlpha, switchBeta);
        PingRequestCommand command = makeCommand(ping);

        command.call();

        List<OfRequestResponse> ioPayload = ioPayloadCatch.getValue();
        Assert.assertEquals(1, ioPayload.size());
        OfRequestResponse portOutRequest = ioPayload.get(0);
        final OFBadRequestErrorMsg ofError = switchAlpha.getOFFactory().errorMsgs().buildBadRequestErrorMsg()
                .setCode(OFBadRequestCode.BAD_LEN)
                .build();
        portOutRequest.setError(new OfErrorResponseException(ofError));
        command.exceptional(new OfBatchException(ImmutableList.of(portOutRequest)), timeoutFuture);

        verifySentErrorResponse(ping, Errors.WRITE_FAILURE);
    }

    private void expectSuccess(Ping ping) throws Exception {
        PingRequestCommand command = makeCommand(ping);
        command.call();

        List<Message> replies = kafkaMessageCatcher.getValues();
        Assert.assertEquals(0, replies.size());
    }

    private PingRequestCommand makeCommand(Ping ping) {
        CommandContext context = commandContextFactory.produce();
        return new PingRequestCommand(context, ping);
    }
}
