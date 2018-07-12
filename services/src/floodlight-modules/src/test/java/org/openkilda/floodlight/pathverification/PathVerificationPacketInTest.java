/* Copyright 2017 Telstra Open Source
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

package org.openkilda.floodlight.pathverification;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertArrayEquals;

import org.openkilda.config.KafkaTopicsConfig;
import org.openkilda.floodlight.FloodlightTestCase;
import org.openkilda.floodlight.config.provider.ConfigurationProvider;
import org.openkilda.floodlight.service.CommandProcessorService;
import org.openkilda.floodlight.service.of.InputService;
import org.openkilda.floodlight.utils.CommandContextFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.LLDPTLV;
import net.floodlightcontroller.packet.UDP;
import org.apache.commons.codec.binary.Hex;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketInReason;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;

import java.net.InetSocketAddress;

public class PathVerificationPacketInTest extends FloodlightTestCase {

    protected CommandContextFactory commandContextFactory = new CommandContextFactory();

    protected FloodlightContext cntx;
    protected OFDescStatsReply swDescription;
    protected PathVerificationService pvs;
    protected InputService inputService = new InputService(commandContextFactory);

    protected String sw1HwAddrTarget, sw2HwAddrTarget;
    protected IOFSwitch sw1, sw2;
    protected OFPacketIn pktIn;
    protected InetSocketAddress srcIpTarget, dstIpTarget;
    protected InetSocketAddress swIp = new InetSocketAddress("192.168.10.1", 200);

    private byte[] pkt = {(byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD, (byte) 0xEE, (byte) 0xFF, // src mac
            0x11, 0x22, 0x33, 0x44, 0x55, 0x66,                                           // dst mac
            0x08, 0x00,                                                                   // ether-type
            // IP
            0x45, 0x00,                                                                   // ver,ihl, dscp, ecn
            0x00, 0x30,                                                                   // total length
            0x00, 0x00,                                                                   // tcp ident
            0x00, 0x00,                                                                   // flags, frag offset
            0x00,                                                                         // ttl
            0x11,                                                                         // protocol
            0x38, (byte) 0x6d,                                                            // header checksum
            (byte) 0xC0, (byte) 0xA8, 0x00, 0x01,                                         // src ip
            (byte) 0xC0, (byte) 0xA8, 0x00, (byte) 0xFF,                                  // dst ip
            // UDP
            (byte) 0xEF, 0x2F,                                                            // src port
            (byte) 0xEF, 0x2F,                                                            // dst port
            0x00, 0x1c,                                                                   // length
            (byte) 0x8e, 0x7D,                                                            // checksum
            // LLDP TLVs
            0x02, 0x07,                                                                   // type, len
            0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01,                                     // chassisid
            0x04, 0x03,                                                                   // type, len
            0x02, 0x00, 0x01,                                                             // port id
            0x06, 0x02,                                                                   // type, len
            0x00, 0x78,                                                                   // ttl
            0x00, 0x00
    };

    protected IPacket getPacket() {
        UDP udp = new UDP()
                .setDestinationPort(
                        TransportPort.of(PathVerificationService.VERIFICATION_PACKET_UDP_PORT))
                .setSourcePort(
                        TransportPort.of(PathVerificationService.VERIFICATION_PACKET_UDP_PORT));

        VerificationPacket verificationPacket = new VerificationPacket()
                .setChassisId(new LLDPTLV().setType((byte) 1).setLength((short) 7)
                        .setValue(new byte[] {0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01}))
                .setPortId(new LLDPTLV().setType((byte) 2).setLength((short) 3)
                        .setValue(new byte[] {0x02, 0x00, 0x01}))
                .setTtl(new LLDPTLV().setType((byte) 3).setLength((short) 2)
                        .setValue(new byte[] {0x00, 0x78}));

        udp.setPayload(new Data(verificationPacket.serialize()));

        IPv4 ip = new IPv4()
                .setSourceAddress("192.168.0.1")
                .setDestinationAddress(PathVerificationService.VERIFICATION_PACKET_IP_DST)
                .setProtocol(IpProtocol.UDP);

        Ethernet eth = new Ethernet()
                .setDestinationMACAddress("AA:BB:CC:DD:EE:FF")
                .setSourceMACAddress("11:22:33:44:55:66")
                .setEtherType(EthType.IPv4);

        eth.setPayload(ip);
        ip.setPayload(udp);

        return eth;
    }

    @Before
    public void setUp() throws Exception {

        super.setUp();

        cntx = new FloodlightContext();
        mockFloodlightProvider = getMockFloodlightProvider();
        swFeatures = factory.buildFeaturesReply().setNBuffers(1000).build();
        swDescription = factory.buildDescStatsReply().build();

        FloodlightModuleContext fmc = new FloodlightModuleContext();
        fmc.addService(IFloodlightProviderService.class, mockFloodlightProvider);
        fmc.addService(IOFSwitchService.class, getMockSwitchService());
        fmc.addService(InputService.class, inputService);
        fmc.addService(CommandProcessorService.class, new CommandProcessorService(commandContextFactory));

        inputService.init(fmc);

        OFPacketIn.Builder packetInBuilder = factory.buildPacketIn();
        packetInBuilder
                .setMatch(factory.buildMatch().setExact(MatchField.IN_PORT, OFPort.of(1)).build())
                .setData(pkt)
                .setReason(OFPacketInReason.NO_MATCH);
        pktIn = packetInBuilder.build();
        System.out.print(Hex.encodeHexString(pktIn.getData()));

        pvs = new PathVerificationService();

        fmc.addConfigParam(pvs, "isl_bandwidth_quotient", "0.0");
        fmc.addConfigParam(pvs, "hmac256-secret", "secret");
        fmc.addConfigParam(pvs, "bootstrap-servers", "");
        ConfigurationProvider provider = new ConfigurationProvider(fmc, pvs);
        KafkaTopicsConfig topicsConfig = provider.getConfiguration(KafkaTopicsConfig.class);
        PathVerificationServiceConfig serviceConfig = provider.getConfiguration(PathVerificationServiceConfig.class);

        pvs.initConfiguration(topicsConfig, serviceConfig);
        pvs.initServices(fmc);

        srcIpTarget = new InetSocketAddress("192.168.10.1", 200);
        dstIpTarget = new InetSocketAddress("192.168.10.101", 100);
        sw1HwAddrTarget = "11:22:33:44:55:66";
        sw2HwAddrTarget = "AA:BB:CC:DD:EE:FF";

        OFPortDesc sw1Port1 = EasyMock.createMock(OFPortDesc.class);
        expect(sw1Port1.getHwAddr()).andReturn(MacAddress.of(sw1HwAddrTarget)).anyTimes();
        expect(sw1Port1.getVersion()).andReturn(OFVersion.OF_12).anyTimes();
        expect(sw1Port1.getCurrSpeed()).andReturn(100000L).anyTimes();

        OFPortDesc sw2Port1 = EasyMock.createMock(OFPortDesc.class);
        expect(sw2Port1.getHwAddr()).andReturn(MacAddress.of(sw2HwAddrTarget)).anyTimes();
        expect(sw2Port1.getVersion()).andReturn(OFVersion.OF_12).anyTimes();
        expect(sw2Port1.getCurrSpeed()).andReturn(100000L).anyTimes();
        replay(sw1Port1);
        replay(sw2Port1);

        sw1 = buildMockIOFSwitch(1L, sw1Port1, factory, swDescription, srcIpTarget);
        sw2 = buildMockIOFSwitch(2L, sw2Port1, factory, swDescription, dstIpTarget);
        replay(sw1);
        replay(sw2);
    }


    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testDeserialize() {
        Ethernet ethernet = (Ethernet) new Ethernet().deserialize(pkt, 0, pkt.length);
        assertArrayEquals(pkt, ethernet.serialize());

        IPacket expected = getPacket();
        assertArrayEquals(expected.serialize(), ethernet.serialize());
    }




}
