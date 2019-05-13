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

package org.openkilda.floodlight.pathverification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.EqualsAndHashCode;
import net.floodlightcontroller.packet.BasePacket;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.LLDPTLV;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

@Builder()
@lombok.Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
public class VerificationPacket extends BasePacket {
    public static final byte CHASSIS_ID_LLDPTV_PACKET_TYPE = (byte) 1;
    public static final byte PORT_ID_LLDPTV_PACKET_TYPE = (byte) 2;
    public static final byte TTL_LLDPTV_PACKET_TYPE = (byte) 3;
    public static final byte OPTIONAL_LLDPTV_PACKET_TYPE = (byte) 127;

    private LLDPTLV chassisId;
    private LLDPTLV portId;
    private LLDPTLV ttl;
    @Default
    private List<LLDPTLV> optionalTlvList = new ArrayList<>();

    VerificationPacket(Data data) {
        deserialize(data.getData(), 0, data.getData().length);
    }

    /**
     * Makes serialization to byte array.
     *
     * @return the byte array.
     */
    public byte[] serialize() {
        int length = 2 + this.chassisId.getLength() + 2 + this.portId.getLength()
                + 2 + this.ttl.getLength() + 2;
        for (LLDPTLV tlv : this.optionalTlvList) {
            if (tlv != null) {
                length += 2 + tlv.getLength();
            }
        }

        byte[] data = new byte[length];
        ByteBuffer bb = ByteBuffer.wrap(data);
        bb.put(this.chassisId.serialize());
        bb.put(this.portId.serialize());
        bb.put(this.ttl.serialize());
        for (LLDPTLV tlv : this.optionalTlvList) {
            if (tlv != null) {
                bb.put(tlv.serialize());
            }
        }
        bb.putShort((short) 0); // End of LLDPDU

        return data;
    }

    /**
     * Deserialize the {@code}IPacket{@code} class from the byte array.
     *
     * @param data the raw data
     * @param offset the offset
     * @param length the length of data
     * @return the {@code}IPacket{@code}
     */
    public IPacket deserialize(byte[] data, int offset, int length) {
        this.optionalTlvList = new ArrayList<>();
        ByteBuffer bb = ByteBuffer.wrap(data, offset, length);
        LLDPTLV tlv;
        do {
            tlv = new LLDPTLV().deserialize(bb);

            // if there was a failure to deserialize stop processing TLVs
            if (tlv == null) {
                break;
            }
            switch (tlv.getType()) {
                case 0x0:
                    // can throw this one away, its just an end delimiter
                    break;
                case CHASSIS_ID_LLDPTV_PACKET_TYPE:
                    this.chassisId = tlv;
                    break;
                case PORT_ID_LLDPTV_PACKET_TYPE:
                    this.portId = tlv;
                    break;
                case TTL_LLDPTV_PACKET_TYPE:
                    this.ttl = tlv;
                    break;
                default:
                    this.optionalTlvList.add(tlv);
                    break;
            }
        } while (tlv.getType() != 0 && bb.hasRemaining());
        return this;
    }
}
