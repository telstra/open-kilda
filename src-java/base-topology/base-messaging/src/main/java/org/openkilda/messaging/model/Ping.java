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

import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Value
public class Ping implements Serializable {
    public enum Errors {
        TIMEOUT,
        WRITE_FAILURE,
        NOT_CAPABLE,
        SOURCE_NOT_AVAILABLE,
        DEST_NOT_AVAILABLE
    }

    @JsonProperty(value = "ping_id", required = true)
    private UUID pingId;

    @JsonProperty("ingress_vlan")
    private int ingressVlanId;

    @JsonProperty("ingress_inner_vlan")
    private int ingressInnerVlanId;

    @JsonProperty(value = "source", required = true)
    private NetworkEndpoint source;

    @JsonProperty(value = "dest", required = true)
    private NetworkEndpoint dest;

    @JsonCreator
    public Ping(
            @JsonProperty("ping_id") UUID pingId,
            @JsonProperty("ingress_vlan") int ingressVlanId,
            @JsonProperty("ingress_inner_vlan") int ingressInnerVlanId,
            @JsonProperty("source") NetworkEndpoint source,
            @JsonProperty("dest") NetworkEndpoint dest) {

        this.pingId = pingId;

        this.ingressVlanId = ingressVlanId;
        this.ingressInnerVlanId = ingressInnerVlanId;

        this.source = source;
        this.dest = dest;
    }

    public Ping(int ingressVlanId, int ingressInnerVlanId, NetworkEndpoint source, NetworkEndpoint dest) {
        this(UUID.randomUUID(), ingressVlanId, ingressInnerVlanId, source, dest);
    }

    @Override
    public String toString() {
        String sourceEndpoint = formatEndpoint(
                source.getDatapath(), source.getPortNumber(),
                FlowEndpoint.makeVlanStack(ingressVlanId, ingressInnerVlanId));
        return String.format("%s ===( ping{%s} )===> %s", sourceEndpoint, pingId, dest.getDatapath());
    }

    /**
     * Make string representation of ping endpoint(ingress). Used mostly for logging.
     */
    public static String formatEndpoint(SwitchId swId, int portNumber, List<Integer> vlanStack) {
        String endpoint = String.format("%s-%d", swId, portNumber);
        if (!vlanStack.isEmpty()) {
            endpoint += ":" + vlanStack.stream().map(String::valueOf).collect(Collectors.joining(":"));
        }
        return endpoint;
    }
}
