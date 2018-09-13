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

package org.openkilda.messaging.info.switches;

import org.openkilda.messaging.info.InfoData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Value
@Builder
public class PortDescription extends InfoData {

    @JsonProperty("port_number")
    private int portNumber;

    @JsonProperty("hardware_address")
    private String hardwareAddress;

    @JsonProperty("name")
    private String name;

    @JsonProperty("config")
    private String[] config;

    @JsonProperty("state")
    private String[] state;

    @JsonProperty("current_features")
    private String[] currentFeatures;

    @JsonProperty("advertised_features")
    private String[] advertisedFeatures;

    @JsonProperty("supported_features")
    private String[] supportedFeatures;

    @JsonProperty("peer_features")
    private String[] peerFeatures;

    @JsonProperty("curr_speed")
    private long currentSpeed;

    @JsonProperty("max_speed")
    private long maxSpeed;

    @JsonCreator
    public PortDescription(
            @JsonProperty("port_number") int portNumber,
            @JsonProperty("hardware_address") String hardwareAddress,
            @JsonProperty("name") String name,
            @JsonProperty("config") String[] config,
            @JsonProperty("state") String[] state,
            @JsonProperty("current_features") String[] currentFeatures,
            @JsonProperty("advertised_features") String[] advertisedFeatures,
            @JsonProperty("supported_features") String[] supportedFeatures,
            @JsonProperty("peer_features") String[] peerFeatures,
            @JsonProperty("curr_speed") long currentSpeed,
            @JsonProperty("max_speed") long maxSpeed) {
        this.portNumber = portNumber;
        this.hardwareAddress = hardwareAddress;
        this.name = name;
        this.config = config;
        this.state = state;
        this.currentFeatures = currentFeatures;
        this.advertisedFeatures = advertisedFeatures;
        this.supportedFeatures = supportedFeatures;
        this.peerFeatures = peerFeatures;
        this.currentSpeed = currentSpeed;
        this.maxSpeed = maxSpeed;
    }
}
