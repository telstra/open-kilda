/* Copyright 2021 Telstra Open Source
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
public class IPv4Address implements Serializable {
    private static final long serialVersionUID = 972927305176942157L;
    private static final String IP_V4_ADDRESS_REGEXP = "^([0-9]{1,3}[.]){3}([0-9]{1,3})$";

    String address;

    public IPv4Address(String address) {
        Objects.requireNonNull(address, "IPv4 Address must not be null");
        if (isValid(address)) {
            this.address = address;
        } else {
            throw new IllegalArgumentException(String.format("'%s' is not a valid IPv4 Address", address));
        }
    }

    @JsonValue
    @Override
    public String toString() {
        return address;
    }

    /**
     * Checks if string is a valid IPv4 Address.
     *
     * @param address IPv4 address
     * @return True if address is valid False otherwise
     */
    public static boolean isValid(String address) {
        if (address == null) {
            return false;
        }
        return address.matches(IP_V4_ADDRESS_REGEXP);
    }
}
