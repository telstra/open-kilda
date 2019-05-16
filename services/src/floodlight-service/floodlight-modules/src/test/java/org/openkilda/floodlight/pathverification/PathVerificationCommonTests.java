/* Copyright 2019 Telstra Open Source
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

import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.openkilda.floodlight.pathverification.PathVerificationService.ETHERNET_HEADER_SIZE;
import static org.openkilda.floodlight.pathverification.PathVerificationService.IP_V4_HEADER_SIZE;
import static org.openkilda.floodlight.pathverification.PathVerificationService.LLDP_TLV_CHASSIS_ID_TOTAL_SIZE;
import static org.openkilda.floodlight.pathverification.PathVerificationService.LLDP_TLV_HEADER_SIZE;
import static org.openkilda.floodlight.pathverification.PathVerificationService.LLDP_TLV_OPTIONAL_HEADER_SIZE_IN_BYTES;
import static org.openkilda.floodlight.pathverification.PathVerificationService.LLDP_TLV_PORT_ID_TOTAL_SIZE;
import static org.openkilda.floodlight.pathverification.PathVerificationService.LLDP_TLV_TTL_TOTAL_SIZE;
import static org.openkilda.floodlight.pathverification.PathVerificationService.ROUND_TRIP_LATENCY_T0_OFFSET;
import static org.openkilda.floodlight.pathverification.PathVerificationService.ROUND_TRIP_LATENCY_T1_OFFSET;
import static org.openkilda.floodlight.pathverification.PathVerificationService.ROUND_TRIP_LATENCY_TIMESTAMP_SIZE;
import static org.openkilda.floodlight.pathverification.PathVerificationService.SWITCH_T0_OPTIONAL_TYPE;
import static org.openkilda.floodlight.pathverification.PathVerificationService.SWITCH_T1_OPTIONAL_TYPE;
import static org.openkilda.floodlight.pathverification.PathVerificationService.UDP_HEADER_SIZE;

import org.openkilda.floodlight.FloodlightTestCase;
import org.openkilda.floodlight.model.OfInput;
import org.openkilda.floodlight.service.FeatureDetectorService;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.LLDPTLV;
import net.floodlightcontroller.packet.UDP;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketInReason;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.ver13.OFFactoryVer13;
import org.projectfloodlight.openflow.types.OFPort;

import java.net.InetSocketAddress;

public class PathVerificationCommonTests  extends FloodlightTestCase {
    private PathVerificationService pvs;
    private IOFSwitch sw;
    private byte[] timestampT0 = new byte[] {
            0x07, 0x5b, (byte) 0xcd, 0x15,         // 123456789 seconds
            0x3a, (byte) 0xde, 0x68, (byte) 0xb1}; // 987654321 nanoseconds
    private byte[] timestampT1 = new byte[] {
            0x3b, (byte) 0x9a, (byte) 0xc9, (byte) 0xff,   // 999999999 seconds
            0x3b, (byte) 0x9a, (byte) 0xc9, (byte) 0xff};  // 999999999 nanoseconds

    @Before
    public void setUp() throws Exception {
        super.setUp();
        FloodlightModuleContext fmc = new FloodlightModuleContext();
        fmc.addService(IFloodlightProviderService.class, mockFloodlightProvider);
        fmc.addService(FeatureDetectorService.class, featureDetectorService);
        fmc.addService(IOFSwitchService.class, getMockSwitchService());

        pvs = new PathVerificationService();
        pvs.initAlgorithm("secret");

        fmc.addConfigParam(pvs, "isl_bandwidth_quotient", "0.0");
        fmc.addConfigParam(pvs, "hmac256-secret", "secret");
        fmc.addConfigParam(pvs, "bootstrap-servers", "");

        pvs.init(fmc);

        InetSocketAddress srcIpTarget = new InetSocketAddress("192.168.10.1", 200);
        long switchId = 0x112233445566L;

        OFPortDesc portDescription = EasyMock.createMock(OFPortDesc.class);
        OFDescStatsReply swDescription = factory.buildDescStatsReply().build();

        sw = buildMockIoFSwitch(switchId, portDescription, factory, swDescription, srcIpTarget);
        replay(sw);
    }

    @Test
    public void testNoviflowTimstampToLong() {
        assertEquals(123456789_987654321L, PathVerificationService.noviflowTimestamp(timestampT0));
    }

    @Test
    public void testCalcLatencyWithTransmitAndReceiveTimestamps() {
        OfInput input = createInputPacket();

        // packet has software timestamp for tx and rx
        long calculatedLatency = PathVerificationService.calcLatency(
                input, 123,
                PathVerificationService.noviflowTimestamp(timestampT0),
                PathVerificationService.noviflowTimestamp(timestampT1)
        );
        assertEquals(876543210_012345678L, calculatedLatency);
    }

    @Test
    public void testCalcLatencyWithTransmitTimestampOnly() {
        OfInput input = createInputPacket();

        // packet has timestamp for tx only
        long calculatedLatency = PathVerificationService.calcLatency(
                input, 123, PathVerificationService.noviflowTimestamp(timestampT0), 0);
        assertEquals(input.getReceiveTime() * 1000000 - PathVerificationService.noviflowTimestamp(timestampT0),
                calculatedLatency);
    }

    @Test
    public void testCalcLatencyWithReceiveTimestampOnly() {
        OfInput input = createInputPacket();
        long expectedLatency = 50;
        long sendTime = input.getReceiveTime() - expectedLatency;

        //packet has software timestamp for rx only
        long calculatedLatency = PathVerificationService.calcLatency(
                input, sendTime, 0, PathVerificationService.noviflowTimestamp(timestampT1));
        assertEquals(PathVerificationService.noviflowTimestamp(timestampT1) - sendTime * 1000000,
                calculatedLatency);
    }

    @Test
    public void testCalcLatencyWithoutTimestamps() {
        OfInput input = createInputPacket();

        long expectedLatency = 50;
        long sendTime = input.getReceiveTime() - expectedLatency;

        // packet has no software timestamps
        long calculatedLatency = PathVerificationService.calcLatency(input, sendTime, 0, 0);
        assertEquals(expectedLatency * 1000000, calculatedLatency);  // adjusted to nanoseconds
    }

    private OfInput createInputPacket() {
        OFFactory factory = new OFFactoryVer13();

        Match match = factory.buildMatch()
                .setExact(MatchField.IN_PORT, OFPort.of(1))
                .build();

        OFPacketIn ofPacketIn = factory.buildPacketIn()
                .setCookie(PathVerificationService.OF_CATCH_RULE_COOKIE)
                .setMatch(match)
                .setReason(OFPacketInReason.PACKET_OUT)
                .build();

        return new OfInput(sw, ofPacketIn, new FloodlightContext());
    }

    @Test
    public void testDiscoveryPacketHeadersSize() {
        OFPacketOut packet = pvs.generateDiscoveryPacket(sw, OFPort.of(1), true, null);
        Ethernet ethernet = (Ethernet) new Ethernet().deserialize(packet.getData(), 0, packet.getData().length);
        IPv4 ipv4 = (IPv4) ethernet.getPayload();
        UDP udp = (UDP) ipv4.getPayload();

        assertEquals(ETHERNET_HEADER_SIZE, (ethernet.serialize().length - ipv4.getTotalLength()) * 8);
        assertEquals(IP_V4_HEADER_SIZE, (ipv4.getTotalLength() - udp.getLength()) * 8);
        assertEquals(UDP_HEADER_SIZE, (udp.getLength() - udp.getPayload().serialize().length) * 8);
    }

    @Test
    public void testSizeOfMandatoryLldpTlvInDiscoveryPacket() {
        DiscoveryPacket discoveryPacket = createDiscoveryPacket();
        assertEquals(LLDP_TLV_CHASSIS_ID_TOTAL_SIZE, discoveryPacket.getChassisId().serialize().length * 8);
        assertEquals(LLDP_TLV_PORT_ID_TOTAL_SIZE, discoveryPacket.getPortId().serialize().length * 8);
        assertEquals(LLDP_TLV_TTL_TOTAL_SIZE, discoveryPacket.getTtl().serialize().length * 8);
    }

    @Test
    public void testSizeOfRoundTripLatencyTimestampsInDiscoveryPacket() {
        DiscoveryPacket discoveryPacket = createDiscoveryPacket();

        LLDPTLV switchT0Timestamp = discoveryPacket.getOptionalTlvList().get(0);
        assertRoundTripLatencyTimestamp(switchT0Timestamp, SWITCH_T0_OPTIONAL_TYPE);

        LLDPTLV switchT1Timestamp = discoveryPacket.getOptionalTlvList().get(1);
        assertRoundTripLatencyTimestamp(switchT1Timestamp, SWITCH_T1_OPTIONAL_TYPE);
    }

    private void assertRoundTripLatencyTimestamp(LLDPTLV timestamp, byte timestampType) {
        // ensure that it's needed timestamp
        assertEquals(timestampType, timestamp.getValue()[LLDP_TLV_OPTIONAL_HEADER_SIZE_IN_BYTES - 1]);
        // check that payload contains 12 bytes: 4 for optional header and 8 for timestamp
        assertEquals(LLDP_TLV_OPTIONAL_HEADER_SIZE_IN_BYTES * 8 + ROUND_TRIP_LATENCY_TIMESTAMP_SIZE,
                timestamp.getValue().length * 8);
        // check that whole packet contains 14 bytes: 2 for Lldp Tlv header, 4 for optional header and 8 for timestamp
        assertEquals(LLDP_TLV_HEADER_SIZE + LLDP_TLV_OPTIONAL_HEADER_SIZE_IN_BYTES * 8
                        + ROUND_TRIP_LATENCY_TIMESTAMP_SIZE,
                timestamp.serialize().length * 8);
    }

    @Test
    public void testRoundTripLatencyTimestampOffset() {
        OFPacketOut packet = pvs.generateDiscoveryPacket(sw, OFPort.of(1), true, null);

        assertRoundTripLatencyOffset(packet.getData(), SWITCH_T0_OPTIONAL_TYPE, ROUND_TRIP_LATENCY_T0_OFFSET);
        assertRoundTripLatencyOffset(packet.getData(), SWITCH_T1_OPTIONAL_TYPE, ROUND_TRIP_LATENCY_T1_OFFSET);
    }

    private void assertRoundTripLatencyOffset(byte[] data, byte timestampType, int timestampOffset) {
        int offsetInBytes = timestampOffset / 8;
        assertEquals(timestampType, data[offsetInBytes - 1]); // type of timestamp is placed before timestamp
        for (int i = 0; i < ROUND_TRIP_LATENCY_TIMESTAMP_SIZE / 8; i++) {
            assertEquals(0, data[offsetInBytes + i]); // Timestamp placeholder
        }
    }

    private DiscoveryPacket createDiscoveryPacket() {
        OFPacketOut packet = pvs.generateDiscoveryPacket(sw, OFPort.of(1), true, null);
        Ethernet ethernet = (Ethernet) new Ethernet().deserialize(packet.getData(), 0, packet.getData().length);
        IPv4 ipv4 = (IPv4) ethernet.getPayload();
        UDP udp = (UDP) ipv4.getPayload();

        return new DiscoveryPacket((Data) udp.getPayload(), true);
    }
}
