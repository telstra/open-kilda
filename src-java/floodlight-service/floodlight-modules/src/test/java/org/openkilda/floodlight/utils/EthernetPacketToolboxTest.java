/* Copyright 2020 Telstra Open Source
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

package org.openkilda.floodlight.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.openkilda.floodlight.shared.packet.VlanTag;
import org.openkilda.floodlight.test.standard.PacketTestBase;

import lombok.extern.slf4j.Slf4j;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import org.junit.BeforeClass;
import org.junit.Test;
import org.projectfloodlight.openflow.types.EthType;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class EthernetPacketToolboxTest extends PacketTestBase {

    @BeforeClass
    public static void setUpClass() throws Exception {
        log.info("Force loading of {}", Class.forName(VlanTag.class.getName()));
    }

    @Test
    public void extractEthernetPayloadTest() {
        short vlan1 = 1234;
        short vlan2 = 2345;
        short vlan3 = 4000;
        byte[] originPayload = new byte[]{0x55, (byte) 0xAA};
        Ethernet ethernet = buildEthernet(
                new byte[]{
                        0x01, 0x0A, 0x0A, 0x0A, 0x0A, 0x0A, // src mac address
                        0x01, 0x0B, 0x0B, 0x0B, 0x0B, 0x0B  // dst mac address
                },
                ethTypeToByteArray(EthType.Q_IN_Q), shortToByteArray(vlan1),
                ethTypeToByteArray(EthType.BRIDGING), shortToByteArray(vlan2),
                ethTypeToByteArray(EthType.VLAN_FRAME), shortToByteArray(vlan3),
                ethTypeToByteArray(EthType.IPv4), originPayload);

        List<Integer> vlans = new ArrayList<>();
        IPacket payload = EthernetPacketToolbox.extractPayload(ethernet, vlans);

        assertEquals(3, vlans.size());
        assertEquals(Integer.valueOf(vlan1), vlans.get(0));
        assertEquals(Integer.valueOf(vlan2), vlans.get(1));
        assertEquals(Integer.valueOf(vlan3), vlans.get(2));

        assertArrayEquals(originPayload, payload.serialize());
    }
}
