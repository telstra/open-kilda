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

package org.openkilda.floodlight.service.ping.packet;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import net.floodlightcontroller.packet.BasePacket;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.PacketParsingException;
import org.projectfloodlight.openflow.types.MacAddress;

import java.nio.ByteBuffer;

@Slf4j
@Data
@EqualsAndHashCode(callSuper = false)
public class EthernetHeader extends BasePacket {
    protected MacAddress destinationMacAddress;
    protected MacAddress sourceMacAddress;

    private static int length = MacAddress.NONE.getLength() * 2;

    @Override
    public byte[] serialize() {
        byte[] payloadData = new byte[0];
        if (payload != null) {
            payload.setParent(this);
            payloadData = payload.serialize();
        }

        byte[] data = new byte[payloadData.length + length];
        ByteBuffer bb = ByteBuffer.wrap(data);
        bb.put(destinationMacAddress.getBytes());
        bb.put(sourceMacAddress.getBytes());

        bb.put(payloadData);

        return data;
    }

    @Override
    public IPacket deserialize(byte[] data, int offset, int length) throws PacketParsingException {
        if (length <= length) {
            // Safe check on required amount of data
            return null;
        }

        ByteBuffer bb = ByteBuffer.wrap(data, offset, length);
        destinationMacAddress = deserializeMacAddress(bb);
        sourceMacAddress = deserializeMacAddress(bb);

        byte[] buf = new byte[bb.remaining()];
        bb.get(buf);
        this.payload = new net.floodlightcontroller.packet.Data(buf);

        return this;
    }

    private MacAddress deserializeMacAddress(ByteBuffer bb) {
        byte[] dstAddr = new byte[MacAddress.NONE.getLength()];
        bb.get(dstAddr);
        return MacAddress.of(dstAddr);
    }
}
