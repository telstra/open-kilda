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

package org.openkilda.simulator.messages;

import static com.google.common.base.MoreObjects.toStringHelper;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;

@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(value = {
        "latency",
        "local_port",
        "peer_switch",
        "peer_port"})

public class LinkMessage implements Serializable {
    @JsonProperty("latency")
    private int latency;

    @JsonProperty("local_port")
    private int localPort;

    @JsonProperty("peer_switch")
    private String peerSwitch;

    @JsonProperty("peer_port")
    private int peerPort;

    public LinkMessage(@JsonProperty("latency") int latency,
                       @JsonProperty("local-port") int localPort,
                       @JsonProperty("peer_switch") String peerSwitch,
                       @JsonProperty("peer_port") int peerPort) {
        this.latency = latency;
        this.localPort = localPort;
        this.peerSwitch = peerSwitch;
        this.peerPort = peerPort;
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("latency", latency)
                .toString();
    }

    public int getLatency() {
        return latency;
    }

    public int getLocalPort() {
        return localPort;
    }

    public String getPeerSwitch() {
        return peerSwitch;
    }

    public int getPeerPort() {
        return peerPort;
    }
}
