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

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;

import org.openkilda.floodlight.model.OfInput;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.internal.OFSwitch;
import org.easymock.EasyMock;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketInReason;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.ver13.OFFactoryVer13;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

public class TimestampTests {
    private byte[] timestampT0 = new byte[] {
            0x07, 0x5b, (byte) 0xcd, 0x15,         // 123456789 seconds
            0x3a, (byte) 0xde, 0x68, (byte) 0xb1}; // 987654321 nanoseconds
    private byte[] timestampT1 = new byte[] {
            0x3b, (byte) 0x9a, (byte) 0xc9, (byte) 0xff,   // 999999999 seconds
            0x3b, (byte) 0x9a, (byte) 0xc9, (byte) 0xff};  // 999999999 nanoseconds

    @Test
    public void testNoviflowTimestampToLong() {
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

        OFSwitch sw = EasyMock.createMock(OFSwitch.class);
        expect(sw.getId()).andStubReturn(DatapathId.of(0xfffe000000000001L));
        expect(sw.getLatency()).andStubReturn(U64.of(123));
        replay(sw);

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
}
