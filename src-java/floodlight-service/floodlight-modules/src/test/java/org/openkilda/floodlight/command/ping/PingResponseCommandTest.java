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
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;

import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.model.OfInput;
import org.openkilda.floodlight.model.PingData;
import org.openkilda.floodlight.pathverification.PathVerificationService;
import org.openkilda.floodlight.service.of.InputService;
import org.openkilda.floodlight.service.ping.PingService;
import org.openkilda.floodlight.switchmanager.ISwitchManager;
import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.floodlight.response.PingResponse;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.messaging.model.Ping;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.SwitchId;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import org.easymock.Mock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketInReason;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.ver13.OFFactoryVer13;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.U64;

import java.util.List;

public class PingResponseCommandTest extends PingCommandTest {
    private final DatapathId dpId = DatapathId.of(0x0000fffe00000001L);

    @Mock
    private PingService pingService;

    @Mock
    private IOFSwitch iofSwitch;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        moduleContext.addService(PingService.class, pingService);

        expect(iofSwitch.getId()).andReturn(dpId).anyTimes();
        expect(iofSwitch.getLatency()).andReturn(U64.of(8)).anyTimes();
    }

    @Test
    public void skipByCookie() throws Exception {
        replayAll();

        OFFactory ofFactory = new OFFactoryVer13();
        OFMessage message = ofFactory.buildPacketIn()
                .setReason(OFPacketInReason.ACTION).setXid(1)
                .setCookie(U64.of(PingService.OF_CATCH_RULE_COOKIE.hashCode() + 1))
                .build();
        FloodlightContext floodlightContext = new FloodlightContext();
        OfInput input = new OfInput(iofSwitch, message, floodlightContext);

        PingResponseCommand command = makeCommand(input);
        expectSkip(command);
    }

    @Test
    public void foreignPackage() throws Exception {
        expect(pingService.unwrapData(eq(dpId), anyObject())).andReturn(null);

        OfInput input = createMock(OfInput.class);
        expect(input.packetInCookieMismatchAll(anyObject(), anyObject(), anyObject())).andReturn(false);
        expect(input.getPacketInPayload()).andReturn(new Ethernet());
        expect(input.getDpId()).andReturn(dpId);

        replayAll();

        PingResponseCommand command = makeCommand(input);
        expectSkip(command);
    }

    @Test
    public void success() throws Exception {
        final PingService realPingService = new PingService();
        moduleContext.addService(PingService.class, realPingService);

        final ISwitchManager realSwitchManager = new SwitchManager();
        moduleContext.addService(ISwitchManager.class, realSwitchManager);

        InputService inputService = createMock(InputService.class);
        moduleContext.addService(InputService.class, inputService);

        inputService.addTranslator(eq(OFType.PACKET_IN), anyObject());

        replayAll();

        final DatapathId dpIdBeta = DatapathId.of(0x0000fffe000002L);
        final Ping ping = new Ping(new NetworkEndpoint(new SwitchId(dpIdBeta.getLong()), 8),
                new NetworkEndpoint(new SwitchId(dpId.getLong()), 9),
                new FlowTransitEncapsulation(2, FlowEncapsulationType.TRANSIT_VLAN), 3);
        final PingData payload = PingData.of(ping);

        moduleContext.addConfigParam(new PathVerificationService(), "hmac256-secret", "secret");
        realPingService.setup(moduleContext);

        byte[] signedPayload = realPingService.getSignature().sign(payload);
        byte[] wireData = realPingService.wrapData(ping, signedPayload).serialize();

        OFFactory ofFactory = new OFFactoryVer13();
        OFPacketIn message = ofFactory.buildPacketIn()
                .setReason(OFPacketInReason.ACTION).setXid(1L)
                .setCookie(PingService.OF_CATCH_RULE_COOKIE)
                .setData(wireData)
                .build();

        FloodlightContext metadata = new FloodlightContext();
        IPacket decodedEthernet = new Ethernet().deserialize(wireData, 0, wireData.length);
        Assertions.assertTrue(decodedEthernet instanceof Ethernet);
        IFloodlightProviderService.bcStore.put(
                metadata, IFloodlightProviderService.CONTEXT_PI_PAYLOAD, (Ethernet) decodedEthernet);
        OfInput input = new OfInput(iofSwitch, message, metadata);
        final PingResponseCommand command = makeCommand(input);

        command.call();

        final List<Message> replies = kafkaMessageCatcher.getValues();
        Assertions.assertEquals(1, replies.size());
        InfoMessage response = (InfoMessage) replies.get(0);
        PingResponse pingResponse = (PingResponse) response.getData();

        Assertions.assertNull(pingResponse.getError());
        Assertions.assertNotNull(pingResponse.getMeters());
        Assertions.assertEquals(payload.getPingId(), pingResponse.getPingId());
    }

    private PingResponseCommand makeCommand(OfInput input) {
        return new PingResponseCommand(new CommandContext(moduleContext), input);
    }

    private void expectSkip(PingResponseCommand command) throws Exception {
        Assertions.assertNull(command.call());
        Assertions.assertFalse(kafkaMessageCatcher.hasCaptured());
    }
}
