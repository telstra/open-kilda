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

import org.neo4j.ogm.typeconversion.CompositeAttributeConverter;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InetSocketAddressConverter implements CompositeAttributeConverter<InetSocketAddress> {

    private static final String ADDRESS = "address";
    private static final String PORT = "port";

    @Override
    public Map<String, ?> toGraphProperties(InetSocketAddress value) {
        Map<String, Object> address = new HashMap<>();
        if (value != null) {
            if (value.getAddress() != null) {
                address.put(ADDRESS, value.getAddress().getHostAddress());
            }
            address.put(PORT, value.getPort());
        }
        return address;
    }

    @Override
    public InetSocketAddress toEntityAttribute(Map<String, ?> properties) {
        int port = Optional.ofNullable((Long) properties.get(PORT)).map(Long::intValue).orElse(0);
        return Optional.ofNullable((String) properties.get(ADDRESS))
                .map(address -> convert(address, port))
                .orElse(null);
    }

    private static InetSocketAddress convert(String address, int port) {
        try {
            return new InetSocketAddress(InetAddress.getByName(address), port);
        } catch (UnknownHostException e) {
            throw new IllegalStateException(format("Switch address '%s' is invalid", address), e);
        }
    }
}
