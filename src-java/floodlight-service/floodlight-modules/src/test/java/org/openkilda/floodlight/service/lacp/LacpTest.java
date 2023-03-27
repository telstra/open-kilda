/* Copyright 2022 Telstra Open Source
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

package org.openkilda.floodlight.service.lacp;

import static org.junit.Assert.assertEquals;

import org.openkilda.floodlight.shared.packet.Lacp;
import org.openkilda.floodlight.test.standard.PacketTestBase;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.PacketParsingException;
import org.junit.Assert;
import org.junit.Test;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.MacAddress;

public class LacpTest extends PacketTestBase {
    private final byte[] srcAndDstMacAddresses = new byte[] {
            0x1, (byte) 0x80, (byte) 0xC2, 0x0, 0x0, 0xE,           // src mac address
            0x10, 0x4E, (byte) 0xF1, (byte) 0xF9, 0x6D, (byte) 0xFA // dst mac address
    };

    private final byte[] lacpPacket = new byte[] {
            0x01,                                      // LACP version
            0x01,                                      // TLV type: actor
            0x14,                                      // TLV length
            (byte) 0x91, (byte) 0xf4,                  // Actor system priority
            0x00, 0x04, (byte) 0x96, 0x1f, 0x50, 0x6a, // Actor system ID
            (byte) 0x80, 0x00,                         // Actor key
            0x00, 0x11,                                // Actor port priority
            0x00, 0x12,                                // Actor port
            0x47,                                      // Actor state
            0x00, 0x00, 0x00,                          // Reserved
            0x02,                                      // TLV type: partner
            0x14,                                      // TLV length
            0x12, 0x34,                                // Partner system priority
            0x12, 0x34, 0x56, 0x78, (byte) 0x9a, 0x77, // Partner system ID
            0x44, 0x55,                                // Partner key
            0x11, 0x22,                                // Partner port priority
            0x00, 0x17,                                // Partner port
            0x3b,                                      // Partner state
            0x00, 0x00, 0x00,                          // Reserved
            0x03,                                      // TLV type: collector information
            0x10,                                      // TLV length
            0x00, 0x02,                                // Max delay
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, // Reserved
            0x00,                                      // TLV type: terminator
            0x00,                                      // TLV length
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 // Zeros

    };

    LacpService service = new LacpService();

    @Test
    public void deserializeLacpTest() throws PacketParsingException {
        Lacp lacp = buildLacpPacket(lacpPacket);
        assertLacp(lacp);
        Assert.assertArrayEquals(lacpPacket, lacp.serialize());
    }

    @Test
    public void serializeLacpTest() throws PacketParsingException {
        Lacp lacp = buildLacpPacket(lacpPacket);
        lacp.getPartner().setPortPriority(0xffff);
        Lacp lacp1 = (Lacp) new Lacp().deserialize(lacp.serialize(), 0, lacp.serialize().length);
        assertEquals(lacp, lacp1);
    }

    @Test
    public void serializeEthWithLacpTest() {
        Ethernet ethernet = buildEthernet(srcAndDstMacAddresses,
                ethTypeToByteArray(EthType.SLOW_PROTOCOLS), new byte[] {0x01}, lacpPacket);

        Lacp lacp = service.deserializeLacp(ethernet, null, 0);
        assertLacp(lacp);
    }

    private void assertLacp(Lacp lacp) {
        assertEquals(0x91f4, lacp.getActor().getSystemPriority());
        assertEquals(MacAddress.of("00:04:96:1f:50:6a"), lacp.getActor().getSystemId());
        assertEquals(0x8000, lacp.getActor().getKey());
        assertEquals(0x11, lacp.getActor().getPortPriority());
        assertEquals(0x12, lacp.getActor().getPortNumber());

        assertEquals(0x1234, lacp.getPartner().getSystemPriority());
        assertEquals(MacAddress.of("12:34:56:78:9a:77"), lacp.getPartner().getSystemId());
        assertEquals(0x4455, lacp.getPartner().getKey());
        assertEquals(0x1122, lacp.getPartner().getPortPriority());
        assertEquals(0x17, lacp.getPartner().getPortNumber());

        assertEquals(3, lacp.getCollectorInformation().getType());
        assertEquals(2, lacp.getCollectorInformation().getMaxDelay());
    }

    static Lacp buildLacpPacket(byte[] packet) throws PacketParsingException {
        Lacp lacp = new Lacp();
        lacp.deserialize(packet, 0, packet.length);
        return lacp;
    }
}
