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
public class SlowProtocols extends BasePacket {
    public static final int SLOW_PROTOCOL_LENGTH_IN_BYTES = 1;
    public static final byte LACP_SUBTYPE = 0x01;

    private byte subtype;

    static {
        Ethernet.etherTypeClassMap.put((short) EthType.SLOW_PROTOCOLS.getValue(), SlowProtocols.class);
    }

    @Override
    public byte[] serialize() {
        byte[] payloadData = new byte[0];
        if (payload != null) {
            payload.setParent(this);
            payloadData = payload.serialize();
        }
        ByteBuffer bb = ByteBuffer.allocate(SLOW_PROTOCOL_LENGTH_IN_BYTES + payloadData.length);
        bb.put(subtype);
        bb.put(payloadData);
        return bb.array();
    }

    @Override
    public IPacket deserialize(byte[] data, int offset, int length) {
        if (length == 0) {
            return null;
        }
        ByteBuffer bb = ByteBuffer.wrap(data, offset, length);

        subtype = bb.get();
        if (subtype == LACP_SUBTYPE) {
            try {
                payload = new Lacp().deserialize(data, bb.position(), bb.limit() - bb.position());
            } catch (Exception e) {
                if (log.isTraceEnabled()) {
                    log.trace(String.format("Failed to parse Slow Protocols with subtype LACP: %s", e.getMessage()), e);
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
