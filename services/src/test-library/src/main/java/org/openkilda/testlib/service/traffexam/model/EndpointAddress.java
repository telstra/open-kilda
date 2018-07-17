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

package org.openkilda.testlib.service.traffexam.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.InputMismatchException;

public final class EndpointAddress implements Serializable {
    public final Inet4Address address;
    public final int port;

    public EndpointAddress(Inet4Address address, int port) {
        this.address = address;
        this.port = port;
    }

    @JsonCreator
    public EndpointAddress(
            @JsonProperty("address") String address,
            @JsonProperty("port") int port) {
        try {
            this.address = (Inet4Address) Inet4Address.getByName(address);
        } catch (UnknownHostException e) {
            throw new InputMismatchException(String.format("Invalid value for \"address\" property: %s", address));
        }
        this.port = port;
    }
}
