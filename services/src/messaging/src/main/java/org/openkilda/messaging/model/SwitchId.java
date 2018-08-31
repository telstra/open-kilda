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

package org.openkilda.messaging.model;

import org.openkilda.messaging.error.SwitchIdFormatException;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a switch id entity.
 */
public class SwitchId implements Serializable, Comparable<SwitchId> {

    private static final long serialVersionUID = 1L;

    /**
     * Switch id numerical representation.
     */
    private final long switchIdNum;

    /**
     * Construct an instance based on the colon separated representation of a switch id.
     */
    public SwitchId(String switchId) {
        Preconditions.checkArgument(Objects.nonNull(switchId), "Switch id must not be null");

        long value;
        try {
            value = Long.parseUnsignedLong(switchId.replaceAll("[-:]", ""), 16);
        } catch (NumberFormatException e) {
            throw new SwitchIdFormatException(String.format("Can not parse input string: \"%s\"", switchId));
        }

        this.switchIdNum = value;
    }

    /**
     * Construct an instance based on the long value representation of a switch id.
     */
    public SwitchId(long switchId) {
        this.switchIdNum = switchId;
    }

    /**
     * Return the switch id in long.
     *
     * @return the switch id in long.
     */
    public long toLong() {
        return switchIdNum;
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
    @JsonValue
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

    @VisibleForTesting
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
        String hexString = String.format("%016x", switchIdNum);
        return hexString.toCharArray();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SwitchId other = (SwitchId) o;

        return switchIdNum == other.switchIdNum;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(switchIdNum);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(SwitchId other) {
        return Long.compareUnsigned(switchIdNum, other.switchIdNum);
    }
}
