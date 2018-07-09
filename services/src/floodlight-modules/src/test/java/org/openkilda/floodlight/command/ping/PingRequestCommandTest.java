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
import org.openkilda.floodlight.model.OfBatchResult;
import org.openkilda.floodlight.pathverification.PathVerificationService;
import org.openkilda.floodlight.service.ConfigService;
import org.openkilda.floodlight.service.PingService;
import org.openkilda.floodlight.service.batch.OfBatchService;
import org.openkilda.floodlight.utils.DataSignature;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.floodlight.response.PingResponse;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.messaging.model.Ping;
import org.openkilda.messaging.model.Ping.Errors;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import org.easymock.Mock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.ver12.OFFactoryVer12;
import org.projectfloodlight.openflow.protocol.ver13.OFFactoryVer13;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.U64;

import java.util.List;
import java.util.concurrent.Future;

public class PingRequestCommandTest extends AbstractTest {
    @Mock
    private IOFSwitchService switchService;

    @Mock
    private IOFSwitch switchAlpha;

    @Mock
    private IOFSwitch switchBeta;

    @Mock
    private IOFSwitch switchMissing;

    @Mock
    private IOFSwitch switchNotCapable;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        moduleContext.addService(ConfigService.class, configService);
        moduleContext.addService(IOFSwitchService.class, switchService);

        final DatapathId dpIdAlpha = DatapathId.of(0xfffe000001L);
        final DatapathId dpIdBeta = DatapathId.of(0xfffe000002L);
        final DatapathId dpIdGamma = DatapathId.of(0xfffe000003L);
        final DatapathId dpIdDelta = DatapathId.of(0xfffe000004L);

        OFFactory ofFactory = new OFFactoryVer13();

        expect(switchAlpha.getId()).andReturn(dpIdAlpha).anyTimes();
        expect(switchBeta.getId()).andReturn(dpIdBeta).anyTimes();
        expect(switchMissing.getId()).andReturn(dpIdGamma).anyTimes();
        expect(switchNotCapable.getId()).andReturn(dpIdDelta).anyTimes();

        expect(switchAlpha.getOFFactory()).andReturn(ofFactory).anyTimes();
        expect(switchBeta.getOFFactory()).andReturn(ofFactory).anyTimes();
        expect(switchNotCapable.getOFFactory()).andReturn(new OFFactoryVer12()).anyTimes();

        expect(switchService.getActiveSwitch(dpIdAlpha)).andReturn(switchAlpha).anyTimes();
        expect(switchService.getActiveSwitch(dpIdBeta)).andReturn(switchBeta).anyTimes();
        expect(switchService.getActiveSwitch(dpIdGamma)).andReturn(null).anyTimes();
        expect(switchService.getActiveSwitch(dpIdDelta)).andReturn(switchNotCapable).anyTimes();
    }

    @Test
    public void success() throws Exception {
        final IFloodlightProviderService providerService = createMock(IFloodlightProviderService.class);
        moduleContext.addService(IFloodlightProviderService.class, providerService);
        providerService.addOFMessageListener(anyObject(), anyObject());

        final PingService pingService = createMock(PingService.class);
        moduleContext.addService(PingService.class, pingService);
        expect(pingService.getSignature()).andReturn(new DataSignature("secret"));

        final PingService realPingService = new PingService(commandContextFactory);
        expect(pingService.wrapData(anyObject(Ping.class), anyObject())).andDelegateTo(realPingService);

        expect(switchAlpha.getLatency()).andReturn(U64.of(1L)).anyTimes();
        expect(switchBeta.getLatency()).andReturn(U64.of(2L)).anyTimes();

        @SuppressWarnings("unchecked")
        Future<OfBatchResult> futureMock = createMock(Future.class);
        expect(futureMock.get()).andReturn(null);

        final OfBatchService batchService = createMock(OfBatchService.class);
        moduleContext.addService(OfBatchService.class, batchService);
        expect(batchService.write(anyObject())).andReturn(futureMock);

        IPingResponseFactory responseFactory = createMock(IPingResponseFactory.class);

        replayAll();

        moduleContext.addConfigParam(new PathVerificationService(), "hmac256-secret", "secret");
        realPingService.init(moduleContext, responseFactory);

        CommandContext context = commandContextFactory.produce();
        PingRequestCommand command = new PingRequestCommand(context, makePing(switchAlpha, switchBeta));

        command.execute();

        List<Message> replies = producerPostMessage.getValues();
        Assert.assertEquals(0, replies.size());
    }

    @Test
    public void missingSourceSwitch() throws Exception {
        replayAll();

        final CommandContext context = commandContextFactory.produce();
        final Ping ping = makePing(switchMissing, switchBeta);
        final PingRequestCommand command = new PingRequestCommand(context, ping);

        command.execute();
        expectErrorResponse(ping, Errors.SOURCE_NOT_AVAILABLE);
    }

    @Test
    public void missingDestSwitch() {
        replayAll();

        final CommandContext context = commandContextFactory.produce();
        final Ping ping = makePing(switchAlpha, switchMissing);
        final PingRequestCommand command = new PingRequestCommand(context, ping);

        command.execute();
        expectErrorResponse(ping, Errors.DEST_NOT_AVAILABLE);
    }

    @Test
    public void unableToPing() {
        replayAll();

        final CommandContext context = commandContextFactory.produce();
        final Ping ping = makePing(switchAlpha, switchNotCapable);
        final PingRequestCommand command = new PingRequestCommand(context, ping);

        command.execute();
        expectErrorResponse(ping, Errors.NOT_CAPABLE);
    }

    private void expectErrorResponse(Ping ping, Ping.Errors errorCode) {
        List<Message> replies = producerPostMessage.getValues();
        Assert.assertEquals(1, replies.size());

        InfoMessage message = (InfoMessage) replies.get(0);
        PingResponse response = (PingResponse) message.getData();

        Assert.assertEquals(ping.getPingId(), response.getPingId());
        Assert.assertEquals(errorCode, response.getError());
    }

    private Ping makePing(IOFSwitch source, IOFSwitch dest) {
        return new Ping(
                (short) 0x100,
                new NetworkEndpoint(source.getId().toString(), 8),
                new NetworkEndpoint(dest.getId().toString(), 9));
    }
}
