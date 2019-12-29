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

package org.openkilda.floodlight.service.connected;

import static com.google.common.primitives.Bytes.concat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.openkilda.floodlight.service.connected.ConnectedDevicesService.PacketData;
import org.openkilda.floodlight.service.ping.packet.VlanTag;

import lombok.extern.slf4j.Slf4j;
import net.floodlightcontroller.packet.Ethernet;
import org.junit.BeforeClass;
import org.junit.Test;
import org.projectfloodlight.openflow.types.EthType;

@Slf4j
public class ConnectedDevicesServiceTest {
    private byte[] srcAndDstMacAddresses = new byte[] {
            0x1, (byte) 0x80, (byte) 0xC2, 0x0, 0x0, 0xE,           // src mac address
            0x10, 0x4E, (byte) 0xF1, (byte) 0xF9, 0x6D, (byte) 0xFA // dst mac address
    };

    private ConnectedDevicesService service = new ConnectedDevicesService();

    @BeforeClass
    public static void setUpClass() throws Exception {
        log.info("Force loading of {}", Class.forName(VlanTag.class.getName()));
    }

    @Test
    public void deserializeLldpTest() {
        byte[] ethernetWithLldp = concat(
                srcAndDstMacAddresses, ethTypeToByteArray(EthType.LLDP), LldpPacketTest.packet);
        Ethernet ethernet = new Ethernet();
        ethernet.deserialize(ethernetWithLldp, 0, ethernetWithLldp.length);

        PacketData data = service.deserializeLldp(ethernet, null, 0);
        assertTrue(data.getVlans().isEmpty());
        assertEquals(LldpPacketTest.buildLldpPacket(LldpPacketTest.packet), data.getLldpPacket());
    }

    @Test
    public void deserializeLldpWithVlanTest() {
        short vlan = 1234;
        byte[] ethernetWithLldp = concat(srcAndDstMacAddresses, ethTypeToByteArray(EthType.VLAN_FRAME),
                shortToByteArray(vlan), ethTypeToByteArray(EthType.LLDP), LldpPacketTest.packet);
        Ethernet ethernet = new Ethernet();
        ethernet.deserialize(ethernetWithLldp, 0, ethernetWithLldp.length);

        PacketData data = service.deserializeLldp(ethernet, null, 0);
        assertEquals(1, data.getVlans().size());
        assertEquals(Integer.valueOf(vlan), data.getVlans().get(0));
        assertEquals(LldpPacketTest.buildLldpPacket(LldpPacketTest.packet), data.getLldpPacket());
    }

    @Test
    public void deserializeLldpWithTwoVlanTest() {
        short innerVlan = 1234;
        short outerVlan = 2345;
        byte[] ethernetWithLldp = concat(srcAndDstMacAddresses, ethTypeToByteArray(EthType.VLAN_FRAME),
                shortToByteArray(outerVlan), ethTypeToByteArray(EthType.VLAN_FRAME), shortToByteArray(innerVlan),
                ethTypeToByteArray(EthType.LLDP), LldpPacketTest.packet);
        Ethernet ethernet = new Ethernet();
        ethernet.deserialize(ethernetWithLldp, 0, ethernetWithLldp.length);

        PacketData data = service.deserializeLldp(ethernet, null, 0);
        assertNotNull(data);
        assertEquals(2, data.getVlans().size());
        assertEquals(Integer.valueOf(outerVlan), data.getVlans().get(0));
        assertEquals(Integer.valueOf(innerVlan), data.getVlans().get(1));
        assertEquals(LldpPacketTest.buildLldpPacket(LldpPacketTest.packet), data.getLldpPacket());
    }

    private byte[] ethTypeToByteArray(EthType ethType) {
        return shortToByteArray((short) ethType.getValue());
    }

    private byte[] shortToByteArray(short s) {
        return new byte[] {(byte) (s >> 8), (byte) s};
    }
}
