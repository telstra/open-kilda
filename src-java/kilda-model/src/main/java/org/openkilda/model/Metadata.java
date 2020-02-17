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

package org.openkilda.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * Represents information about a metadata.
 * Uses 32 bit to encode information about the packet:
 * 0                   1                   2                   3
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |L|O|A                       Reserved Prefix                      |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *
 * <p>
 * L - flag indicates LLDP packet
 * O - flag indicates packet received by one switch flow
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)

public class Metadata implements Serializable {
    private static final long serialVersionUID = 5505079196135886296L;

    public static final long METADATA_LLDP_VALUE = 0x0000_0000_0020_0000L;
    public static final long METADATA_LLDP_MASK = 0x0000_0000_0020_0000L;

    public static final long METADATA_ONE_SWITCH_FLOW_VALUE = 0x0000_0000_0040_0000L;
    public static final long METADATA_ONE_SWITCH_FLOW_MASK = 0x0000_0000_0040_0000L;
    public static final long ENCAPSULATION_ID_MASK = 0x0000_0000_000FF_FFFFL;
    public static final long FORWARD_METADATA_FLAG = 0x0000_0000_0010_0000L;

    public static final long METADATA_ARP_VALUE = 0x0000_0000_0000_0004L;
    public static final long METADATA_ARP_MASK =  0x0000_0000_0000_0004L;

    private final long value;

    public static long getOneSwitchFlowLldpValue() {
        return METADATA_LLDP_VALUE | METADATA_ONE_SWITCH_FLOW_VALUE;
    }

    public static long getOneSwitchFlowLldpMask() {
        return METADATA_LLDP_MASK | METADATA_ONE_SWITCH_FLOW_MASK;
    }

    public static long getOneSwitchFlowArpValue() {
        return METADATA_ARP_VALUE | METADATA_ONE_SWITCH_FLOW_VALUE;
    }

    public static long getOneSwitchFlowArpMask() {
        return METADATA_ARP_MASK | METADATA_ONE_SWITCH_FLOW_MASK;
    }

    public static long getTunnelIdValue(long tunnelId) {
        return tunnelId | ENCAPSULATION_ID_MASK;
    }

    public static long markAsForward(long metadata) {
        return metadata | FORWARD_METADATA_FLAG;
    }

    /**
     * Encode metadata for the mirroring.
     * @param tunnelId target tunnel id
     * @param forward forward flag
     * @return metadata value
     */
    public static long getAppForwardingValue(long tunnelId, boolean forward) {
        long metadata = getTunnelIdValue(tunnelId);
        if (forward) {
            metadata = markAsForward(metadata);
        }
        return metadata;
    }

    @Override
    public String toString() {
        return toString(value);
    }

    public static String toString(long metadata) {
        return String.format("0x%016X", metadata);
    }
}
