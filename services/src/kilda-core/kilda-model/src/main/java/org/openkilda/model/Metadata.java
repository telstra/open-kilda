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

import lombok.Value;

import java.io.Serializable;

/**
 * Represents information about a metadata.
 * Uses 64 bit to encode information about the packet:
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |L|                        Reserved Prefix                      |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                          Reserved Prefix                      |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * <p>
 * L - flag indicates LLDP packet
 * </p>
 */
@Value
public class Metadata implements Serializable {
    private static final long serialVersionUID = 5505079196135886296L;

    public static final long METADATA_LLDP_VALUE = 0x0000_0000_0000_0001L;
    public static final long METADATA_LLDP_MASK =  0x0000_0000_0000_0001L;

    private final long value;

    @Override
    public String toString() {
        return toString(value);
    }

    public static String toString(long metadata) {
        return String.format("0x%016X", metadata);
    }
}
