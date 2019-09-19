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

package org.openkilda.floodlight.service.connected;

import static org.junit.Assert.assertEquals;
import static org.openkilda.floodlight.service.connected.LldpPacket.IPV4_DESCRIPTION;
import static org.openkilda.floodlight.service.connected.LldpPacket.LOCALLY_ASSIGNED_DESCRIPTION;
import static org.openkilda.floodlight.service.connected.LldpPacket.MAC_DESCRIPTION;
import static org.openkilda.floodlight.service.connected.LldpPacket.PORT_ID_LLDPTV_TYPE;
import static org.openkilda.floodlight.service.connected.LldpPacket.PORT_ID_SUBTYPE_LOCALLY_ASSIGNED;
import static org.openkilda.floodlight.service.connected.LldpPacket.PORT_ID_SUBTYPE_MAC;
import static org.openkilda.floodlight.service.connected.LldpPacket.addDescription;

import net.floodlightcontroller.packet.LLDPTLV;
import org.junit.Test;

public class LldpPacketTest {
    private byte[] packet = new byte[]{
            0x02, 0x07,                                                       // chassis ID type, len
            0x04,                                                             // chassis ID sub type (Mac address)
            (byte) 0xAE, 0x59, 0x21, 0x13, 0x41, 0x36,                        // Mac address
            0x04, 0x07,                                                       // port ID type, len
            0x03,                                                             // port ID sub type (Mac address)
            (byte) 0xE2, 0x4C, 0x63, 0x33, (byte) 0xF3, (byte) 0xEB,          // Mac address
            0x06, 0x02,                                                       // type, len
            0x00, 0x78,                                                       // ttl (120)
            0x0A, 0x0D,                                                       // System Name type, len
            0x74, 0x72, 0x61, 0x66, 0x67, 0x65, 0x6E, 0x30, 0x32, 0x2E, 0x6C, 0x6F, 0x6E, // System name
            0x0C, 0x57,                                                       // system description type, len
            0x55, 0x62, 0x75, 0x6E, 0x74, 0x75, 0x20, 0x31, 0x38, 0x2E, 0x31, 0x30, 0x20, 0x4C, 0x69, 0x6E, 0x75, 0x78,
            0x20, 0x34, 0x2E, 0x31, 0x38, 0x2E, 0x30, 0x2D, 0x31, 0x30, 0x2D, 0x67, 0x65, 0x6E, 0x65, 0x72, 0x69, 0x63,
            0x20, 0x23, 0x31, 0x31, 0x2D, 0x55, 0x62, 0x75, 0x6E, 0x74, 0x75, 0x20, 0x53, 0x4D, 0x50, 0x20, 0x54, 0x68,
            0x75, 0x20, 0x4F, 0x63, 0x74, 0x20, 0x31, 0x31, 0x20, 0x31, 0x35, 0x3A, 0x31, 0x33, 0x3A, 0x35, 0x35, 0x20,
            0x55, 0x54, 0x43, 0x20, 0x32, 0x30, 0x31, 0x38, 0x20, 0x78, 0x38, 0x36, 0x5F, 0x36, 0x34, //description
            0x0E, 0x04,                                                       // system capabilities type, len
            0x00, (byte) 0x9C, 0x00, 0x14,
            0x10, 0x0C,                                                       // management address type, len
            0x05,                                                             // address length + 1
            0x01,                                                             // address subtype (IPv4)
            (byte) 0xAC, 0x11, 0x00, 0x02,                                    // IPv4
            0x02,                                                             // interface numbering subtype
            0x00, 0x00, 0x0C, (byte) 0xA4,
            0x00,                                                             // oid string length
            0x08, 0x08,                                                       // port description type, len
            0x74, 0x67, 0x31, 0x2D, 0x65, 0x74, 0x68, 0x30                    // description};
    };

    @Test
    public void errorReporting() {
        LldpPacket lldpPacket = new LldpPacket(packet);
        assertEquals(addDescription(MAC_DESCRIPTION, "ae:59:21:13:41:36"), lldpPacket.getParsedChassisId());
        assertEquals(addDescription(MAC_DESCRIPTION, "e2:4c:63:33:f3:eb"), lldpPacket.getParsedPortId());
        assertEquals(120, (int) lldpPacket.getParsedTtl());
        assertEquals(addDescription(IPV4_DESCRIPTION, "172.17.0.2"), lldpPacket.getParsedManagementAddress());
        assertEquals("tg1-eth0", lldpPacket.getParsedPortDescription());
        assertEquals("trafgen02.lon", lldpPacket.getParsedSystemName());
        assertEquals("Ubuntu 18.10 Linux 4.18.0-10-generic #11-Ubuntu SMP Thu Oct 11 15:13:55 UTC 2018 x86_64",
                lldpPacket.getParsedSystemDescription());
    }

    @Test
    public void portLocallyAssigmentTest() {
        LLDPTLV portTvl = new LLDPTLV().setType((byte) 0x03).setLength((short) 3)
                .setValue(new byte[] {PORT_ID_SUBTYPE_LOCALLY_ASSIGNED, 0x33, 0x33});
        LldpPacket lldpPacket = LldpPacket.builder().portId(portTvl).build();
        assertEquals(addDescription(LOCALLY_ASSIGNED_DESCRIPTION, "33"), lldpPacket.getParsedPortId());
    }

    @Test
    public void portMacTest() {
        LLDPTLV portTvl = new LLDPTLV().setType(PORT_ID_LLDPTV_TYPE).setLength((short) 7)
                .setValue(new byte[] {PORT_ID_SUBTYPE_MAC, 0x01, (byte) 0x80, (byte) 0xc2, 0x00, 0x00, 0x0e});
        LldpPacket lldpPacket = LldpPacket.builder().portId(portTvl).build();
        assertEquals(addDescription(MAC_DESCRIPTION, "01:80:c2:00:00:0e"), lldpPacket.getParsedPortId());
    }
}
