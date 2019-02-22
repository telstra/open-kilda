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

package org.openkilda.grpc.speaker.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;

@Data
@EqualsAndHashCode
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RemoteLogServerDto {
    @NonNull
    @JsonProperty("ip_address")
    private String ipAddress;

    @NonNull
    @JsonProperty("port")
    private Integer port;

    @JsonCreator
    public RemoteLogServerDto(String ipAddress, Integer port) {
        this.ipAddress = ipAddress;
        this.port = port;
    }

    /**
     * Sets a remote log server IP address.
     *
     * @param ipAddress an ip address.
     */
    public void setIpAddress(String ipAddress) {
        if (ipAddress == null) {
            throw new IllegalArgumentException("Ip address must not be null");
        }
        this.ipAddress = ipAddress;
    }

    /**
     * Sets a remote log server port.
     *
     * @param port a port number.
     */
    public void setPort(Integer port) {
        if (port == null) {
            throw new IllegalArgumentException("Port must not be null");
        }
        this.port = port;
    }
}
