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

import org.openkilda.floodlight.shared.packet.VlanTag;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import org.projectfloodlight.openflow.types.EthType;

import java.util.List;

public final class EthernetPacketToolbox {
    /**
     * Wrap packet payload with VLAN header. Useful for creating nested VLAN headers.
     */
    public static IPacket injectVlan(IPacket payload, int vlanId, EthType type) {
        VlanTag vlan = new VlanTag();
        vlan.setVlanId((short) vlanId);
        vlan.setEtherType(type);
        vlan.setPayload(payload);
        return vlan;
    }

    /**
     * Read through intermediate vlan headers up to actual packet payload. Return both payload and vlan stack.
     */
    public static IPacket extractPayload(Ethernet packet, List<Integer> vlanStack) {
        short rootVlan = packet.getVlanID();
        if (0 < rootVlan) {
            vlanStack.add((int) rootVlan);
        }

        IPacket payload = packet.getPayload();
        while (payload instanceof VlanTag) {
            short vlanId = ((VlanTag) payload).getVlanId();
            vlanStack.add((int) vlanId);
            payload = payload.getPayload();
        }

        return payload;
    }

    private EthernetPacketToolbox() {
        // hide public constructor
    }
}
