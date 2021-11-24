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

package org.openkilda.model;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Value;

import java.io.Serializable;
import java.util.Objects;

@Value
public class MacAddress implements Serializable {
    private static final long serialVersionUID = 5506319046146663699L;
    private static final String MAC_ADDRESS_REGEXP = "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$";

    String address;

    public MacAddress(String address) {
        Objects.requireNonNull(address, "Mac Address must not be null");

        if (isValid(address)) {
            this.address = address.toUpperCase().replaceAll("-", ":");
        } else {
            throw new IllegalArgumentException(String.format("'%s' is not a valid Mac Address", address));
        }
    }

    public long toLong() {
        return Long.parseUnsignedLong(address.replaceAll("[:]", ""), 16);
    }

    @JsonValue
    @Override
    public String toString() {
        return address;
    }

    /**
     * Checks if string is a valid Mac Address.
     *
     * @param address mac address
     * @return True if address is valid False otherwise
     */
    public static boolean isValid(String address) {
        if (address == null) {
            return false;
        }
        return address.matches(MAC_ADDRESS_REGEXP);
    }
}
