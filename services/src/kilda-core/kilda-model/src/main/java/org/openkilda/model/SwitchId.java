/* Copyright 2018 Telstra Open Source
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

import java.util.Objects;

/**
 * Represents a switch id entity.
 */
@Value
public class SwitchId implements Comparable<SwitchId> {

    private final long switchId;

    /**
     * Construct an instance based on the long value representation of a switch id.AttributeConverter
     */
    public SwitchId(long switchId) {
        this.switchId = switchId;
    }

    /**
     * Construct an instance based on the colon separated representation of a switch id.
     */
    public SwitchId(String switchId) {
        Objects.requireNonNull(switchId, "Switch id must not be null");

        try {
            this.switchId = Long.parseUnsignedLong(switchId.replaceAll("[-:]", ""), 16);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(String.format("Can not parse input string: \"%s\"", switchId));
        }
    }

    /**
     * Return the switch id in long.
     *
     * @return the switch id in long.
     */
    public long toLong() {
        return switchId;
    }

    /**
     * Return the MAC address of switch.
     *
     * @return the MAC address of switch.
     */
    public String toMacAddress() {
        return colonSeparatedBytes(toHexArray(), 4);
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public String toString() {
        return colonSeparatedBytes(toHexArray(), 0);
    }

    /**
     * Return the switch id in otsd format.
     *
     * @return the switch id in otsd format.
     */
    public String toOtsdFormat() {
        return "SW" + new String(toHexArray()).toUpperCase();
    }

    //TODO: @VisibleForTesting
    String colonSeparatedBytes(char[] hex, int offset) {
        if (offset < 0 || offset % 2 != 0 || offset >= hex.length) {
            throw new IllegalArgumentException(String.format(
                    "Illegal offset value %d (expect offset > 0 and offset %% 2 == 0 and offset < hex.length)", offset));
        }

        int length = hex.length - offset;
        length += length / 2 - 1;
        char[] buffer = new char[length];
        int dst = 0;
        for (int src = offset; src < hex.length; src++) {
            if (offset < src && src % 2 == 0) {
                buffer[dst++] = ':';
            }
            buffer[dst++] = hex[src];
        }

        return new String(buffer);
    }

    private char[] toHexArray() {
        String hexString = String.format("%016x", switchId);
        return hexString.toCharArray();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(SwitchId other) {
        return Long.compareUnsigned(switchId, other.switchId);
    }
}
