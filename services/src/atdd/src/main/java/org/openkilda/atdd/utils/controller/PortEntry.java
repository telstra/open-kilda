/* Copyright 2017 Telstra Open Source
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

package org.openkilda.atdd.utils.controller;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;
import java.util.List;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PortEntry implements Serializable {

    @JsonProperty("port_number")
    private String portNumber;

    @JsonProperty("hardware_address")
    private String hardwareAddress;

    @JsonProperty("name")
    private String name;

    @JsonProperty("config")
    private List<String> config;

    @JsonProperty("state")
    private List<String> state;

    @JsonProperty("current_features")
    private List<String> currentFeatures;

    @JsonProperty("advertised_features")
    private List<String> advertisedFeatures;

    @JsonProperty("supported_features")
    private List<String> supportedFeatures;

    @JsonProperty("peer_features")
    private List<String> peerFeatures;

    @JsonProperty("curr_speed")
    private String currSpeed;

    @JsonProperty("max_speed")
    private String maxSpeed;

    @JsonCreator
    public PortEntry(
            @JsonProperty("port_number") String portNumber,
            @JsonProperty("hardware_address") String hardwareAddress,
            @JsonProperty("name") String name,
            @JsonProperty("config") List<String> config,
            @JsonProperty("state") List<String> state,
            @JsonProperty("current_features") List<String> currentFeatures,
            @JsonProperty("advertised_features") List<String> advertisedFeatures,
            @JsonProperty("supported_features") List<String> supportedFeatures,
            @JsonProperty("peer_features") List<String> peerFeatures,
            @JsonProperty("curr_speed") String currSpeed,
            @JsonProperty("max_speed") String maxSpeed) {
        this.portNumber = portNumber;
        this.hardwareAddress = hardwareAddress;
        this.name = name;
        this.config = config;
        this.state = state;
        this.currentFeatures = currentFeatures;
        this.advertisedFeatures = advertisedFeatures;
        this.supportedFeatures = supportedFeatures;
        this.advertisedFeatures = advertisedFeatures;
        this.peerFeatures = peerFeatures;
        this.currSpeed = currSpeed;
        this.maxSpeed = maxSpeed;
    }

    public String getPortNumber() {
        return portNumber;
    }

    public String getHardwareAddress() {
        return hardwareAddress;
    }

    public String getName() {
        return name;
    }

    public List<String> getConfig() {
        return config;
    }

    public List<String> getState() {
        return state;
    }

    public List<String> getCurrentFeatures() {
        return currentFeatures;
    }

    public List<String> getAdvertisedFeatures() {
        return advertisedFeatures;
    }

    public List<String> getSupportedFeatures() {
        return supportedFeatures;
    }

    public List<String> getPeerFeatures() {
        return peerFeatures;
    }

    public String getCurrSpeed() {
        return currSpeed;
    }

    public String getMaxSpeed() {
        return maxSpeed;
    }
}
