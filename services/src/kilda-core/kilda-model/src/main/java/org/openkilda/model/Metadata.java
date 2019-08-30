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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Value;

import java.io.Serializable;

/**
 * Represents information about a metadata.
 * Uses 64 bit to encode information about the flow:
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |            Payload Reserved           |D|                     |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                          Reserved Prefix                      |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * <p>
 * D - flag indicates direction.
 * </p>
 */
@Value
public class Metadata implements Comparable<Metadata>, Serializable {
    private static final long serialVersionUID = 487013596500006404L;

    private static final long PAYLOAD_MASK = 0x0000_0000_000F_FFFFL;
    private static final long FORWARD_METADATA_FLAG = 0x0000_0000_0010_0000L;

    private final long value;

    @JsonCreator
    public Metadata(long value) {
        this.value = value;
    }

    public static Metadata buildMetadata(int payload, boolean forward) {
        long directionFlag = forward ? FORWARD_METADATA_FLAG : 0L;
        return new Metadata((payload & PAYLOAD_MASK) | directionFlag);
    }

    @JsonValue
    public long getValue() {
        return value;
    }

    @Override
    public String toString() {
        return toString(value);
    }

    public static String toString(long metadata) {
        return String.format("0x%016X", metadata);
    }

    @Override
    public int compareTo(Metadata compareWith) {
        return Long.compare(value, compareWith.value);
    }
}
