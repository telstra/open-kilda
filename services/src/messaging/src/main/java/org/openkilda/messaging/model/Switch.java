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

import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import lombok.Builder;
import lombok.Value;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.List;
import java.util.Set;

@Value
public class Switch implements Serializable {
    @JsonProperty(value = "datapath", required = true)
    private SwitchId datapath;

    @JsonProperty(value = "ip-address", required = true)
    private InetAddress ipAddress;

    @JsonProperty(value = "features", required = true)
    private Set<Feature> features;

    @JsonProperty(value = "ports", required = true)
    private List<SwitchPort> ports;

    @Builder(toBuilder = true)
    @JsonCreator
    public Switch(
            @JsonProperty("datapath") SwitchId datapath,
            @JsonProperty("ip-address") InetAddress ipAddress,
            @JsonProperty("features") Set<Feature> features,
            @JsonProperty("ports") List<SwitchPort> ports) {
        this.datapath = datapath;
        this.ipAddress = ipAddress;
        this.features = ImmutableSet.copyOf(features);
        this.ports = ImmutableList.copyOf(ports);
    }

    public enum Feature {
        METERS,
        BFD,
        BFD_REVIEW,
        RESET_COUNTS_FLAG,
        LIMITED_BURST_SIZE
    }
}
