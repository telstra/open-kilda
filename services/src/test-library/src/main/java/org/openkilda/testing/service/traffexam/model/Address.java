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

package org.openkilda.testing.service.traffexam.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Value;

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.InputMismatchException;
import java.util.UUID;

@Value
public class Address extends HostResource {

    @JsonSerialize(using = VlanJsonSerializer.class)
    private Vlan vlan;
    private Inet4Address address;
    private int prefix;

    public Address(Inet4Address address, int prefix, Vlan vlan) {
        super(null);
        this.address = address;
        this.prefix = prefix;
        this.vlan = vlan;
    }

    @JsonCreator
    public Address(
            @JsonProperty("idnr") UUID id,
            @JsonProperty("address") String address,
            @JsonProperty("prefix") int prefix,
            @JsonProperty("vlan")
            @JsonDeserialize(using = VlanJsonDeserializer.class) Vlan vlan) {
        super(id);

        try {
            this.address = (Inet4Address) Inet4Address.getByName(address);
        } catch (UnknownHostException e) {
            throw new InputMismatchException(String.format("Invalid value for \"address\" property: %s", address));
        }

        this.prefix = prefix;
        this.vlan = vlan;
    }
}
