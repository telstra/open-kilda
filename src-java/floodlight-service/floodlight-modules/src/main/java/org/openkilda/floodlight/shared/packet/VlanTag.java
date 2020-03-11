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

package org.openkilda.floodlight.shared.packet;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import net.floodlightcontroller.packet.BasePacket;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import org.projectfloodlight.openflow.types.EthType;

import java.nio.ByteBuffer;

@Slf4j
@Data
@EqualsAndHashCode(callSuper = false)
public class VlanTag extends BasePacket {
    public static final int VLAN_HEADER_SIZE_IN_BYTES = 4;
    public static final int VLAN_ID_MASK = 0x0FFF;
    public static final int PRIORITY_CODE_OFFSET = 13;
    public static final int PRIORITY_CODE_MASK = 0x07;
    protected short vlanId;
    protected byte priorityCode;

    protected EthType etherType;

    static {
        Ethernet.etherTypeClassMap.put((short) EthType.VLAN_FRAME.getValue(), VlanTag.class);
        Ethernet.etherTypeClassMap.put((short) EthType.Q_IN_Q.getValue(), VlanTag.class);
        Ethernet.etherTypeClassMap.put((short) EthType.BRIDGING.getValue(), VlanTag.class);
    }

    @Override
    public byte[] serialize() {
        byte[] payloadData = new byte[0];
        if (payload != null) {
            payload.setParent(this);
            payloadData = payload.serialize();
        }

        short tci = (short) (vlanId & VLAN_ID_MASK);
        tci |= (priorityCode & PRIORITY_CODE_MASK) << PRIORITY_CODE_OFFSET;

        ByteBuffer bb = ByteBuffer.allocate(payloadData.length + VLAN_HEADER_SIZE_IN_BYTES);
        bb.putShort(tci);
        bb.putShort((short) etherType.getValue());

        bb.put(payloadData);

        return bb.array();
    }

    @Override
    public IPacket deserialize(byte[] data, int offset, int length) {
        // our own EthType is processed by parent/caller we are responsible to process "next" ethernet type header

        if (length <= 4) {
            // at least 4 byte from VLAN tag must be present
            return null;
        }

        ByteBuffer bb = ByteBuffer.wrap(data, offset, length);

        short tci = bb.getShort();
        priorityCode = (byte) ((tci >> PRIORITY_CODE_OFFSET) & PRIORITY_CODE_MASK);
        vlanId = (short) (tci & VLAN_ID_MASK);

        payload = null;

        etherType = EthType.of(bb.getShort());
        Class<? extends IPacket> clazz = Ethernet.etherTypeClassMap.get((short) etherType.getValue());
        if (Ethernet.etherTypeClassMap.containsKey((short) etherType.getValue())) {
            try {
                payload = clazz.newInstance().deserialize(data, bb.position(), bb.limit() - bb.position());
            } catch (Exception e) {
                if (log.isTraceEnabled()) {
                    log.trace(
                            "Failed to parse ethernet payload with type {}: by {}, treat as plain ethernet packet",
                            etherType, clazz.getName(), e);
                }
            }
        }
        if (payload == null) {
            byte[] buf = new byte[bb.remaining()];
            bb.get(buf);
            payload = new net.floodlightcontroller.packet.Data(buf);
        }

        payload.setParent(this);
        return this;
    }
}
