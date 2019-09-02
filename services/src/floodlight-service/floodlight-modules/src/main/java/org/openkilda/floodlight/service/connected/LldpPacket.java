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

import static java.util.Arrays.copyOfRange;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import net.floodlightcontroller.packet.LLDPTLV;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv6Address;
import org.projectfloodlight.openflow.types.MacAddress;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

@Builder()
@lombok.Data
@AllArgsConstructor
public class LldpPacket {
    public static final byte END_OF_LLDPTV_TYPE = 0x00;
    public static final byte CHASSIS_ID_LLDPTV_TYPE = 0x01;
    public static final byte PORT_ID_LLDPTV_TYPE = 0x02;
    public static final byte TTL_LLDPTV_TYPE = 0x03;
    public static final byte PORT_DESCRIPTION_TYPE = 0x04;
    public static final byte SYSTEM_NAME_TYPE = 0x05;
    public static final byte SYSTEM_DESCRIPTION_TYPE = 0x06;
    public static final byte SYSTEM_CAPABILITIES_TYPE = 0x07;
    public static final byte MANAGEMENT_ADDRESS_TYPE = 0x08;
    public static final byte OPTIONAL_LLDPTV_PACKET_TYPE = (byte) 0x7f;

    public static final byte CHASSIS_ID_SUBTYPE_MAC = 0x04;
    public static final byte CHASSIS_ID_SUBTYPE_LOCALLY_ASSIGNED = 0x07;

    public static final byte PORT_ID_SUBTYPE_MAC = 0x03;
    public static final byte PORT_ID_SUBTYPE_LOCALLY_ASSIGNED = 0x07;

    public static final byte MANAGEMENT_ADDRESS_SUBTYPE_IPV4 = 0x01;
    public static final byte MANAGEMENT_ADDRESS_SUBTYPE_IPV6 = 0x02;

    public static final int MAC_ADDRESS_LENGTH = 6;
    public static final int IPV4_LENGTH = 4;
    public static final int IPV6_LENGTH = 16;

    public static final String MAC_DESCRIPTION = "Mac Addr";
    public static final String LOCALLY_ASSIGNED_DESCRIPTION = "Locally Assigned";
    public static final String IPV4_DESCRIPTION = "IPv4";
    public static final String IPV6_DESCRIPTION = "IPv6";


    private LLDPTLV chassisId;
    private LLDPTLV portId;
    private LLDPTLV ttl;
    private LLDPTLV portDescription;
    private LLDPTLV systemName;
    private LLDPTLV systemDescription;
    private LLDPTLV systemCapabilities;
    private LLDPTLV managementAddress;

    @Default
    private List<LLDPTLV> optionalTlvList = new ArrayList<>();

    LldpPacket(byte[] data) {
        deserialize(data);
    }

    String getParsedChassisId() {
        if (chassisId == null || chassisId.getValue().length == 0) {
            return null;
        }

        switch (chassisId.getValue()[0]) {
            case CHASSIS_ID_SUBTYPE_MAC:
                MacAddress macAddress = MacAddress.of(
                        copyOfRange(chassisId.getValue(), 1, chassisId.getValue().length));
                return addDescription(MAC_DESCRIPTION, macAddress.toString());
            case CHASSIS_ID_SUBTYPE_LOCALLY_ASSIGNED:
                String value = new String(copyOfRange(chassisId.getValue(), 1, 1 + MAC_ADDRESS_LENGTH));
                return addDescription(LOCALLY_ASSIGNED_DESCRIPTION, value);
            default:
                return toHexString(chassisId);
        }
    }

    String getParsedPortId() {
        if (portId == null || portId.getValue().length == 0) {
            return null;
        }

        switch (portId.getValue()[0]) {
            case PORT_ID_SUBTYPE_MAC:
                MacAddress macAddress = MacAddress.of(copyOfRange(portId.getValue(), 1, 1 + MAC_ADDRESS_LENGTH));
                return addDescription(MAC_DESCRIPTION, macAddress.toString());
            case PORT_ID_SUBTYPE_LOCALLY_ASSIGNED:
                String value = new String(copyOfRange(portId.getValue(), 1, portId.getValue().length));
                return addDescription(LOCALLY_ASSIGNED_DESCRIPTION, value);
            default:
                return toHexString(portId);
        }
    }

    Integer getParsedTtl() {
        if (ttl == null || ttl.getValue().length == 0) {
            return null;
        }

        ByteBuffer portBb = ByteBuffer.wrap(ttl.getValue(), 0, ttl.getLength());
        return (int) portBb.getShort();
    }

    String getParsedSystemDescription() {
        if (systemDescription == null) {
            return null;
        }
        return new String(systemDescription.getValue());
    }

    String getParsedSystemName() {
        if (systemName == null) {
            return null;
        }
        return new String(systemName.getValue());
    }

    String getParsedPortDescription() {
        if (portDescription == null) {
            return null;
        }
        return new String(portDescription.getValue());
    }

    String getParsedSystemCapabilities() {
        if (systemCapabilities == null) {
            return null;
        }
        return toHexString(systemCapabilities);
    }

    String getParsedManagementAddress() {
        if (managementAddress == null || managementAddress.getValue().length == 0) {
            return null;
        }

        ByteBuffer buffer = ByteBuffer.wrap(managementAddress.getValue());
        int addressLength = buffer.get() - 1;
        int subType = buffer.get();

        switch (subType) {
            case MANAGEMENT_ADDRESS_SUBTYPE_IPV4:
                byte[] v4Address = new byte[addressLength];
                buffer.get(v4Address);
                return addDescription(IPV4_DESCRIPTION, IPv4Address.of(v4Address).toString());
            case MANAGEMENT_ADDRESS_SUBTYPE_IPV6:
                byte[] v6Address = new byte[addressLength];
                buffer.get(v6Address);
                return addDescription(IPV6_DESCRIPTION, IPv6Address.of(v6Address).toString());
            default:
                return toHexString(managementAddress);
        }
    }

    private void deserialize(byte[] data) {
        this.optionalTlvList = new ArrayList<>();
        ByteBuffer bb = ByteBuffer.wrap(data, 0, data.length);
        LLDPTLV tlv;
        do {
            tlv = new LLDPTLV().deserialize(bb);

            // if there was a failure to deserialize stop processing TLVs
            if (tlv == null) {
                break;
            }
            switch (tlv.getType()) {
                case END_OF_LLDPTV_TYPE:
                    // can throw this one away, its just an end delimiter
                    break;
                case CHASSIS_ID_LLDPTV_TYPE:
                    this.chassisId = tlv;
                    break;
                case PORT_ID_LLDPTV_TYPE:
                    this.portId = tlv;
                    break;
                case TTL_LLDPTV_TYPE:
                    this.ttl = tlv;
                    break;
                case PORT_DESCRIPTION_TYPE:
                    this.portDescription = tlv;
                    break;
                case SYSTEM_NAME_TYPE:
                    this.systemName = tlv;
                    break;
                case SYSTEM_DESCRIPTION_TYPE:
                    this.systemDescription = tlv;
                    break;
                case SYSTEM_CAPABILITIES_TYPE:
                    this.systemCapabilities = tlv;
                    break;
                case MANAGEMENT_ADDRESS_TYPE:
                    this.managementAddress = tlv;
                    break;
                default:
                    this.optionalTlvList.add(tlv);
                    break;
            }
        } while (tlv.getType() != 0 && bb.hasRemaining());
    }

    private String toHexString(LLDPTLV lldptlv) {
        StringBuilder sb = new StringBuilder();
        for (byte b : lldptlv.getValue()) {
            sb.append(String.format("0x%X ", b));
        }
        return sb.toString();
    }

    static String addDescription(String description, String value) {
        return String.format("%s: %s", description, value);
    }

    @Override
    public String toString() {
        return "LldpPacket{"
                + "chassisId=" + getParsedChassisId()
                + ", portId=" + getParsedPortId()
                + ", ttl=" + getParsedTtl()
                + ", portDescription=" + getParsedPortDescription()
                + ", systemName=" + getParsedSystemName()
                + ", systemDescription=" + getParsedSystemDescription()
                + ", systemCapabilities=" + getParsedSystemCapabilities()
                + ", managementAddress=" + getParsedManagementAddress()
                + ", optionalTlvList=" + optionalTlvList
                + '}';
    }
}
