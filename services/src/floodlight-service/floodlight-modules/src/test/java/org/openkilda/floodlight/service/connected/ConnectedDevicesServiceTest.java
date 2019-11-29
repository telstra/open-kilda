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

import static org.junit.Assert.assertArrayEquals;

import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.IPacket;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.Test;

public class ConnectedDevicesServiceTest {
    private byte[] lldpHeaderWithVlan = new byte[]{
            0x6, 0x22,               // vlan
            (byte) 0x88, (byte) 0xCC // LLDP eth type
    };
    private byte[] lldpHeaderWithQinQ = new byte[]{
            0x6, 0x21,               // other vlan
            (byte) 0x81, 0x00,       // inner vlan eth type
            0x6, 0x22,               // inner vlan
            (byte) 0x88, (byte) 0xCC // LLDP eth type
    };
    private byte[] lldpPacketWithVlan = ArrayUtils.addAll(lldpHeaderWithVlan, LldpPacketTest.packet);
    private byte[] lldpPacketWithQinQ = ArrayUtils.addAll(lldpHeaderWithQinQ, LldpPacketTest.packet);

    private ConnectedDevicesService service = new ConnectedDevicesService();

    @Test
    public void removeVlanTest() {
        IPacket packet = new Data(lldpPacketWithVlan);
        assertArrayEquals(LldpPacketTest.packet, service.removeVlanTag(packet));
    }

    @Test
    public void removeQinQTest() {
        IPacket packet = new Data(lldpPacketWithQinQ);
        assertArrayEquals(LldpPacketTest.packet, service.removeOuterAndInnerVlanTag(packet));
    }
}
