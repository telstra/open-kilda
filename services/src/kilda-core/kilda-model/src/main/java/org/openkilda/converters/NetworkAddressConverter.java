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

package org.openkilda.converters;

import static java.lang.String.format;

import org.openkilda.model.NetworkAddress;

import org.neo4j.ogm.typeconversion.AttributeConverter;

public class NetworkAddressConverter implements AttributeConverter<NetworkAddress, String> {

    @Override
    public String toGraphProperty(NetworkAddress value) {
        return value == null ? null : format("%s:%s", value.getAddress(), value.getPort());
    }

    @Override
    public NetworkAddress toEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        String[] parts = value.split(":");
        switch (parts.length) {
            case 1:
                return NetworkAddress.builder()
                        .address(parts[0])
                        .build();
            case 2:
                return NetworkAddress.builder()
                        .address(parts[0])
                        .port(Integer.parseInt(parts[1]))
                        .build();
            default:
                throw new IllegalStateException(format("Switch address '%s' is invalid", value));
        }
    }
}
