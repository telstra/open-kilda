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

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketInReason;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.ver13.OFFactoryVer13;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

public class TimestampTests {
    private byte[] timestampT0 = new byte[] {
            0x5b, (byte) 0x8c, (byte) 0xf5, (byte) 0xad,
            0x14, (byte) 0xe0, (byte) 0xf8, 0x3d};
    private byte[] timestampT1 = new byte[] {
            0x5b, (byte) 0x8c, (byte) 0xf5, (byte) 0xad,
            0x1c, 0x08, 0x06, 0x3d};

    OfInput input;
    OFFactory ofFactory = new OFFactoryVer13();
    OFPacketIn ofPacketIn;

    @Before
    public void setUp() {
        input = EasyMock.createMock(OfInput.class);
        Match match = ofFactory.buildMatch()
                .setExact(MatchField.IN_PORT, OFPort.of(1))
                .build();

        ofPacketIn = ofFactory.buildPacketIn()
                .setCookie(PathVerificationService.OF_CATCH_RULE_COOKIE)
                .setMatch(match)
                .setReason(OFPacketInReason.PACKET_OUT)
                .build();

    }

    @Test
    public void testNoviflowTimstampToLong() {
        assertEquals(1535964589350287933L, PathVerificationService.noviflowTimestamp(timestampT0));
    }


    @Test
    public void testLatency() {
        long latency = 50;
        long now = System.currentTimeMillis();
        long sendTime = now - latency;

        expect(input.getReceiveTime()).andStubReturn(now);
        expect(input.getMessage()).andStubReturn(ofPacketIn);
        expect(input.getDpId()).andStubReturn(DatapathId.of(0xfffe000000000001L));
        replay(input);

        // packet has software timestamp for tx and rx
        long calculatedLatency = PathVerificationService.calcLatency(
                input,
                sendTime,
                PathVerificationService.noviflowTimestamp(timestampT0),
                PathVerificationService.noviflowTimestamp(timestampT1)
        );
        assertEquals(120000000L, calculatedLatency);

        // packet has software timestamp for tx only
        calculatedLatency = PathVerificationService.calcLatency(
                input, sendTime, PathVerificationService.noviflowTimestamp(timestampT0), 0);
        assertEquals(input.getReceiveTime() * 1000000 - PathVerificationService.noviflowTimestamp(timestampT0),
                calculatedLatency);

        //packet has software timestamp for rx only
        calculatedLatency = PathVerificationService.calcLatency(
                input, sendTime, 0, PathVerificationService.noviflowTimestamp(timestampT1));
        assertEquals(PathVerificationService.noviflowTimestamp(timestampT1) - sendTime * 1000000,
                calculatedLatency);

        // packet has no software timestamps
        calculatedLatency = PathVerificationService.calcLatency(input, sendTime, 0, 0);
        assertEquals(latency * 1000000, calculatedLatency);  // adjusted to nanoseconds
    }
}
