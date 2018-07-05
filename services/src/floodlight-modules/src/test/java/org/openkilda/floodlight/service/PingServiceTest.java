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

package org.openkilda.floodlight.service;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;

import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.command.ping.IPingResponseFactory;
import org.openkilda.floodlight.command.ping.PingResponseCommand;
import org.openkilda.floodlight.pathverification.PathVerificationService;
import org.openkilda.floodlight.utils.CommandContextFactory;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.messaging.model.Ping;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.packet.Ethernet;
import org.easymock.EasyMockSupport;
import org.easymock.Mock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.U64;

public class PingServiceTest extends EasyMockSupport {
    private CommandContextFactory commandContextFactory = new CommandContextFactory();
    private PingService pingService = new PingService(commandContextFactory);
    private FloodlightModuleContext moduleContext = new FloodlightModuleContext();
    private PathVerificationService pathVerificationService = new PathVerificationService();

    @Mock
    private PingResponseCommand responseCommand;

    @Mock
    private IFloodlightProviderService providerService;

    @Mock
    private IOFSwitchService switchService;

    @Mock
    private IPingResponseFactory responseFactory;

    @Mock
    private IOFSwitch swAlpha;
    private DatapathId dpIdAlpha = DatapathId.of("ff:fe:00:00:00:00:00:01");

    @Mock
    private IOFSwitch swBeta;
    private DatapathId dpIdBeta = DatapathId.of("ff:fe:00:00:00:00:00:02");

    private Ping ping = new Ping(
            (short) 0x100,
            new NetworkEndpoint(dpIdAlpha.toString(), 8),
            new NetworkEndpoint(dpIdBeta.toString(), 9));

    @Before
    public void setUp() throws Exception {
        injectMocks(this);

        commandContextFactory.init(moduleContext);

        moduleContext.addService(IOFSwitchService.class, switchService);
        moduleContext.addService(IFloodlightProviderService.class, providerService);

        moduleContext.addConfigParam(pathVerificationService, "hmac256-secret", "secret");

        providerService.addOFMessageListener(OFType.PACKET_IN, pingService);
        expectLastCall();

        expect(swAlpha.getId()).andReturn(dpIdAlpha).anyTimes();
        expect(swBeta.getId()).andReturn(dpIdBeta).anyTimes();

        expect(switchService.getSwitch(dpIdAlpha)).andReturn(swAlpha).anyTimes();
        expect(switchService.getSwitch(dpIdBeta)).andReturn(swBeta).anyTimes();

        expect(responseFactory.produce(anyObject(CommandContext.class), eq(swBeta), anyObject()))
                .andReturn(responseCommand);

        responseCommand.execute();
        expectLastCall();
    }

    @Test
    public void packetInCookieMatch() throws Exception {
        OFPacketIn ofPacketIn = createMock(OFPacketIn.class);
        // make copy of cookie value to eliminate match by == operator
        expect(ofPacketIn.getCookie()).andReturn(U64.of(PingService.OF_CATCH_RULE_COOKIE.getValue())).anyTimes();
        shouldMatch(ofPacketIn);
    }

    @Test
    public void packetInIgnoreCookieMatch() throws Exception {
        OFPacketIn ofPacketIn = createMock(OFPacketIn.class);
        expect(ofPacketIn.getCookie()).andReturn(U64.of(-1)).anyTimes();
        shouldMatch(ofPacketIn);
    }

    @Test
    public void packetInGenericMatch() throws Exception {
        OFPacketIn ofPacketIn = createMock(OFPacketIn.class);
        expect(ofPacketIn.getCookie())
                .andThrow(new UnsupportedOperationException("getCookie is not supported"))
                .anyTimes();

        shouldMatch(ofPacketIn);
    }

    private void shouldMatch(OFPacketIn ofPacketIn) throws Exception {
        replayAll();

        pingService.init(moduleContext, responseFactory);

        byte[] payload = new byte[]{1, 2, 5, 5, 3, 3, 4};
        CommandContext commandContext = commandContextFactory.produce();
        FloodlightContext floodlightContext = makeFloodlightContext(payload);
        boolean isHandled = pingService.handle(commandContext, swBeta, ofPacketIn, floodlightContext);
        Assert.assertTrue(isHandled);
    }

    private FloodlightContext makeFloodlightContext(byte[] payload) {
        Ethernet ethPackage = pingService.wrapData(ping, payload);

        FloodlightContext floodlightContext = new FloodlightContext();
        IFloodlightProviderService.bcStore.put(
                floodlightContext, IFloodlightProviderService.CONTEXT_PI_PAYLOAD, ethPackage);
        return floodlightContext;
    }
}
