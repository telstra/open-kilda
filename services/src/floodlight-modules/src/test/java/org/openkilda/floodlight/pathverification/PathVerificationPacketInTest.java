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
import static org.junit.Assert.assertEquals;
import static org.openkilda.floodlight.pathverification.DiscoveryPacket.CHASSIS_ID_LLDPTV_PACKET_TYPE;
import static org.openkilda.floodlight.pathverification.DiscoveryPacket.OPTIONAL_LLDPTV_PACKET_TYPE;
import static org.openkilda.floodlight.pathverification.DiscoveryPacket.PORT_ID_LLDPTV_PACKET_TYPE;
import static org.openkilda.floodlight.pathverification.DiscoveryPacket.TTL_LLDPTV_PACKET_TYPE;
import static org.openkilda.floodlight.pathverification.PathVerificationService.ORGANIZATIONALLY_UNIQUE_IDENTIFIER;
import static org.openkilda.floodlight.pathverification.PathVerificationService.PATH_ORDINAL_OPTIONAL_TYPE;
import static org.openkilda.floodlight.pathverification.PathVerificationService.REMOTE_SWITCH_OPTIONAL_TYPE;
import static org.openkilda.floodlight.pathverification.PathVerificationService.SWITCH_T0_OPTIONAL_TYPE;
import static org.openkilda.floodlight.pathverification.PathVerificationService.SWITCH_T1_OPTIONAL_TYPE;
import static org.openkilda.floodlight.pathverification.PathVerificationService.TIMESTAMP_OPTIONAL_TYPE;
import static org.openkilda.floodlight.pathverification.PathVerificationService.noviflowTimestamp;

import org.openkilda.floodlight.FloodlightTestCase;
import org.openkilda.floodlight.KildaCore;
import org.openkilda.floodlight.service.CommandProcessorService;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.service.of.InputService;
import org.openkilda.floodlight.utils.CommandContextFactory;

import com.google.common.collect.Lists;
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
import org.bouncycastle.util.Arrays;
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
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.List;

public class PathVerificationPacketInTest extends FloodlightTestCase {

    public static final byte[] REMOTE_SWITCH_ID = {1, 2, 3, 4, 5, 6, 7, 8};
    public static final byte[] TIMESTAMP = {8, 7, 6, 5, 4, 3, 2, 1};
    public static final byte[] PATH_ORDINAL = {1, 2, 3, 4};
    public static final byte[] SWITCH_T0 = {1, 2, 3, 4, 1, 2, 3, 4};
    public static final byte[] SWITCH_T1 = {4, 3, 2, 1, 4, 3, 2, 1};
    protected CommandContextFactory commandContextFactory = new CommandContextFactory();

    protected FloodlightContext cntx;
    protected OFDescStatsReply swDescription;
    protected PathVerificationService pvs;
    protected InputService inputService = new InputService(commandContextFactory);
    protected IKafkaProducerService producerService = EasyMock.createMock(IKafkaProducerService.class);
    protected FeatureDetectorService featureDetectorService = new FeatureDetectorService();

    protected String sw1HwAddrTarget;
    protected String sw2HwAddrTarget;
    protected IOFSwitch sw1;
    protected IOFSwitch sw2;
    protected OFPacketIn pktIn;
    protected InetSocketAddress srcIpTarget;
    protected InetSocketAddress dstIpTarget;

    private byte[] pkt = {
            (byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD, (byte) 0xEE, (byte) 0xFF, // src mac
            0x11, 0x22, 0x33, 0x44, 0x55, 0x66,                                    // dst mac
            0x08, 0x00,                                                            // ether-type
            // IP
            0x45, 0x00,                                                            // ver,ihl, dscp, ecn
            0x00, 0x72,                                                            // total length
            0x00, 0x00,                                                            // tcp ident
            0x00, 0x00,                                                            // flags, frag offset
            0x00,                                                                  // ttl
            0x11,                                                                  // protocol
            0x38, (byte) 0x2B,                                                     // header checksum
            (byte) 0xC0, (byte) 0xA8, 0x00, 0x01,                                  // src ip
            (byte) 0xC0, (byte) 0xA8, 0x00, (byte) 0xFF,                           // dst ip
            // UDP
            (byte) 0xEF, 0x2F,                                                     // src port
            (byte) 0xEF, 0x2F,                                                     // dst port
            0x00, 0x5E,                                                            // length
            (byte) 0xF5, (byte) 0xAF,                                              // checksum
            // LLDP TLVs
            0x02, 0x07,                                                            // type, len
            0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01,                              // chassisid
            0x04, 0x03,                                                            // type, len
            0x02, 0x00, 0x01,                                                      // port id
            0x06, 0x02,                                                            // type, len
            0x00, 0x78,                                                            // ttl
            // Optional LLDP TLVs:
            // switch T0 timestamp
            (byte) 0xfe, 0x0C,                                                     // optional type, len
            0x00, 0x26, (byte) 0xe1,                                               // organizationally unique identifier
            SWITCH_T0_OPTIONAL_TYPE,                                               // switch t0 type
            0x01, 0x02, 0x03, 0x04, 0x01, 0x02, 0x03, 0x04,                        // t0 timestamp
            // switch T1 timestamp
            (byte) 0xfe, 0x0C,                                                     // optional type, len
            0x00, 0x26, (byte) 0xe1,                                               // organizationally unique identifier
            SWITCH_T1_OPTIONAL_TYPE,                                               // switch t1 type
            0x04, 0x03, 0x02, 0x01, 0x04, 0x03, 0x02, 0x01,                        // t1 timestamp
            // dpid
            (byte) 0xfe, 0x0C,                                                     // optional type, len
            0x00, 0x26, (byte) 0xe1,                                               // organizationally unique identifier
            REMOTE_SWITCH_OPTIONAL_TYPE,                                           // dpid type
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,                        // dpid
            // timestamp
            (byte) 0xfe, 0x0C,                                                     // optional type, len
            0x00, 0x26, (byte) 0xe1,                                               // organizationally unique identifier
            TIMESTAMP_OPTIONAL_TYPE,                                               // timestamp type
            0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01,                        // timestamp
            // ordinal type
            (byte) 0xfe, 0x08,                                                     // optional type, len
            0x00, 0x26, (byte) 0xe1,                                               // organizationally unique identifier
            PATH_ORDINAL_OPTIONAL_TYPE,                                            // ordinal type
            0x01, 0x02, 0x03, 0x04,                                                // ordinal
            // End of LLDP
            0x00, 0x00
    };

    private Ethernet getPacket() {
        UDP udp = new UDP()
                .setDestinationPort(
                        TransportPort.of(PathVerificationService.DISCOVERY_PACKET_UDP_PORT))
                .setSourcePort(
                        TransportPort.of(PathVerificationService.DISCOVERY_PACKET_UDP_PORT));

        List<LLDPTLV> optional = Lists.newArrayList(
                new LLDPTLV() // switch t0
                        .setType(OPTIONAL_LLDPTV_PACKET_TYPE)
                        .setLength((short) 12)
                        .setValue(Arrays.concatenate(
                                ORGANIZATIONALLY_UNIQUE_IDENTIFIER,
                                new byte[] {SWITCH_T0_OPTIONAL_TYPE},
                                SWITCH_T0)),
                new LLDPTLV() // switch t1
                        .setType(OPTIONAL_LLDPTV_PACKET_TYPE)
                        .setLength((short) 12)
                        .setValue(Arrays.concatenate(
                                ORGANIZATIONALLY_UNIQUE_IDENTIFIER,
                                new byte[] {SWITCH_T1_OPTIONAL_TYPE},
                                SWITCH_T1)),
                new LLDPTLV() // dpid
                        .setType(OPTIONAL_LLDPTV_PACKET_TYPE)
                        .setLength((short) 12)
                        .setValue(Arrays.concatenate(
                                ORGANIZATIONALLY_UNIQUE_IDENTIFIER,
                                new byte[] {REMOTE_SWITCH_OPTIONAL_TYPE},
                                REMOTE_SWITCH_ID)),
                new LLDPTLV() // timestamp
                        .setType(OPTIONAL_LLDPTV_PACKET_TYPE)
                        .setLength((short) 12)
                        .setValue(Arrays.concatenate(
                                ORGANIZATIONALLY_UNIQUE_IDENTIFIER,
                                new byte[] {TIMESTAMP_OPTIONAL_TYPE},
                                TIMESTAMP)),
                new LLDPTLV() // path ordinal
                        .setType(OPTIONAL_LLDPTV_PACKET_TYPE)
                        .setLength((short) 8)
                        .setValue(Arrays.concatenate(
                                ORGANIZATIONALLY_UNIQUE_IDENTIFIER,
                                new byte[] {PATH_ORDINAL_OPTIONAL_TYPE},
                                PATH_ORDINAL))
                );

        DiscoveryPacket discoveryPacket = DiscoveryPacket.builder()
                .chassisId(new LLDPTLV().setType(CHASSIS_ID_LLDPTV_PACKET_TYPE).setLength((short) 7)
                        .setValue(new byte[] {0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01}))
                .portId(new LLDPTLV().setType(PORT_ID_LLDPTV_PACKET_TYPE).setLength((short) 3)
                        .setValue(new byte[] {0x02, 0x00, 0x01}))
                .ttl(new LLDPTLV().setType(TTL_LLDPTV_PACKET_TYPE).setLength((short) 2)
                        .setValue(new byte[] {0x00, 0x78}))
                .optionalTlvList(optional)
                .build();

        udp.setPayload(new Data(discoveryPacket.serialize()));

        IPv4 ip = new IPv4()
                .setSourceAddress("192.168.0.1")
                .setDestinationAddress(PathVerificationService.DISCOVERY_PACKET_IP_DST)
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
        fmc.addService(IKafkaProducerService.class, producerService);
        fmc.addService(FeatureDetectorService.class, featureDetectorService);

        KildaCore kildaCore = EasyMock.createMock(KildaCore.class);
        fmc.addService(CommandProcessorService.class, new CommandProcessorService(kildaCore, commandContextFactory));

        inputService.setup(fmc);

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

        pvs.init(fmc);

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
        expect(sw2Port1.getCurrSpeed()).andReturn(400000L).anyTimes();
        replay(sw1Port1);
        replay(sw2Port1);

        sw1 = buildMockIoFSwitch(1L, sw1Port1, factory, swDescription, srcIpTarget);
        sw2 = buildMockIoFSwitch(2L, sw2Port1, factory, swDescription, dstIpTarget);
        replay(sw1);
        replay(sw2);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testDeserialize() {
        Ethernet ethernet = (Ethernet) new Ethernet().deserialize(pkt, 0, pkt.length);
        assertArrayEquals(pkt, ethernet.serialize());

        IPacket expected = getPacket();
        assertArrayEquals(expected.serialize(), ethernet.serialize());
    }

    @Test
    public void testParseDiscoveryPacket() {
        long switchLatency = 5;
        Ethernet ethernet = getPacket();
        DiscoveryPacket discoveryPacket = pvs.deserialize(ethernet);
        DiscoveryPacketData data = pvs.parseDiscoveryPacket(discoveryPacket, switchLatency);

        assertEquals(DatapathId.of(REMOTE_SWITCH_ID).toString(), data.getRemoteSwitchId().toString());
        assertEquals(ByteBuffer.wrap(TIMESTAMP).getLong() + switchLatency, data.getTimestamp());
        assertEquals(ByteBuffer.wrap(PATH_ORDINAL).getInt(), data.getPathOrdinal());
        assertEquals(noviflowTimestamp(SWITCH_T0), data.getSwitchT0());
        assertEquals(noviflowTimestamp(SWITCH_T1), data.getSwitchT1());
    }
}
