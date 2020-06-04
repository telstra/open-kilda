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

package org.openkilda.floodlight.service.ping;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;

import org.openkilda.floodlight.KildaCore;
import org.openkilda.floodlight.KildaCoreConfig;
import org.openkilda.floodlight.config.provider.FloodlightModuleConfigurationProvider;
import org.openkilda.floodlight.model.PingWiredView;
import org.openkilda.floodlight.pathverification.PathVerificationService;
import org.openkilda.floodlight.service.of.InputService;
import org.openkilda.floodlight.shared.packet.Vxlan;
import org.openkilda.floodlight.switchmanager.ISwitchManager;
import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.messaging.model.Ping;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.SwitchId;

import lombok.extern.slf4j.Slf4j;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import org.easymock.EasyMock;
import org.easymock.EasyMockSupport;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.TransportPort;

@Slf4j
public class PingServiceTest extends EasyMockSupport {
    private static final DatapathId dpIdAlpha = DatapathId.of(0x0000fffe00000001L);
    private static final DatapathId dpIdBeta = DatapathId.of(0x0000fffe00000002L);

    private PingService pingService = new PingService();
    private FloodlightModuleContext moduleContext = new FloodlightModuleContext();
    private PathVerificationService pathVerificationService = new PathVerificationService();

    @Before
    public void setUp() {
        injectMocks(this);

        KildaCore kildaCore = EasyMock.createMock(KildaCore.class);
        FloodlightModuleConfigurationProvider provider = FloodlightModuleConfigurationProvider.of(
                moduleContext, KildaCore.class);
        KildaCoreConfig coreConfig = provider.getConfiguration(KildaCoreConfig.class);
        expect(kildaCore.getConfig()).andStubReturn(coreConfig);
        EasyMock.replay(kildaCore);

        moduleContext.addService(KildaCore.class, kildaCore);
        moduleContext.addService(IOFSwitchService.class, createMock(IOFSwitchService.class));
        moduleContext.addService(InputService.class, createMock(InputService.class));
        moduleContext.addService(ISwitchManager.class, createMock(ISwitchManager.class));
        moduleContext.addConfigParam(pathVerificationService, "hmac256-secret", "secret");
    }

    @Test
    public void testWrapUnwrapCycleVlan() throws Exception {
        Ping ping = new Ping(
                new NetworkEndpoint(new SwitchId(dpIdAlpha.getLong()), 8),
                new NetworkEndpoint(new SwitchId(dpIdBeta.getLong()), 9),
                new FlowTransitEncapsulation(2, FlowEncapsulationType.TRANSIT_VLAN), 3);

        moduleContext.getServiceImpl(InputService.class)
                .addTranslator(eq(OFType.PACKET_IN), anyObject(PingInputTranslator.class));

        replayAll();

        pingService.setup(moduleContext);

        byte[] payload = new byte[]{0x31, 0x32, 0x33, 0x34, 0x35};
        byte[] wrapped = pingService.wrapData(ping, payload).serialize();
        IPacket decoded = new Ethernet().deserialize(wrapped, 0, wrapped.length);
        Assert.assertTrue(decoded instanceof Ethernet);
        PingWiredView parsed = pingService.unwrapData(dpIdBeta, (Ethernet) decoded);

        Assert.assertNotNull(parsed);
        Assert.assertArrayEquals(payload, parsed.getPayload());

        Assert.assertEquals(ping.getTransitEncapsulation().getId(), parsed.getVlanStack().get(0));
    }

    @Test
    public void testWrapUnwrapCycleVxlan() throws Exception {
        Ping ping = new Ping(
                new NetworkEndpoint(new SwitchId(dpIdAlpha.getLong()), 8),
                new NetworkEndpoint(new SwitchId(dpIdBeta.getLong()), 9),
                new FlowTransitEncapsulation(2, FlowEncapsulationType.VXLAN), 3);

        moduleContext.getServiceImpl(InputService.class)
                .addTranslator(eq(OFType.PACKET_IN), anyObject(PingInputTranslator.class));

        replayAll();

        pingService.setup(moduleContext);

        byte[] payload = new byte[]{0x31, 0x32, 0x33, 0x34, 0x35};
        byte[] wrapped = pingService.wrapData(ping, payload).serialize();
        IPacket ethernet = new Ethernet().deserialize(wrapped, 0, wrapped.length);
        Assert.assertTrue(ethernet instanceof Ethernet);

        IPacket ipv4 = ethernet.getPayload();
        Assert.assertTrue(ipv4 instanceof IPv4);

        IPacket udp = ipv4.getPayload();
        Assert.assertTrue(udp instanceof UDP);
        Assert.assertEquals(((UDP) udp).getSourcePort(), TransportPort.of(SwitchManager.STUB_VXLAN_UDP_SRC));
        Assert.assertEquals(((UDP) udp).getDestinationPort(), TransportPort.of(SwitchManager.VXLAN_UDP_DST));

        byte[] udpPayload = udp.getPayload().serialize();
        Vxlan vxlan = (Vxlan) new Vxlan().deserialize(udpPayload, 0, udpPayload.length);
        Assert.assertEquals((int) ping.getTransitEncapsulation().getId(), vxlan.getVni());

        byte[] vxlanPayload = vxlan.getPayload().serialize();
        IPacket decoded = new Ethernet().deserialize(vxlanPayload, 0, vxlanPayload.length);
        Assert.assertTrue(decoded instanceof Ethernet);
        PingWiredView parsed = pingService.unwrapData(dpIdBeta, (Ethernet) decoded);

        Assert.assertNotNull(parsed);
        Assert.assertArrayEquals(payload, parsed.getPayload());
        Assert.assertTrue(parsed.getVlanStack().isEmpty());
    }
}
