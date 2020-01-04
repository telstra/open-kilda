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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.io.Serializable;
import java.util.UUID;

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

    @JsonProperty("source_vlan")
    private Short sourceVlanId;

    @JsonProperty(value = "source", required = true)
    private NetworkEndpoint source;

    @JsonProperty(value = "dest", required = true)
    private NetworkEndpoint dest;

    @JsonCreator
    public Ping(
            @JsonProperty("ping_id") UUID pingId,
            @JsonProperty("source_vlan") Short sourceVlanId,
            @JsonProperty("source") NetworkEndpoint source,
            @JsonProperty("dest") NetworkEndpoint dest) {

        this.pingId = pingId;

        if (sourceVlanId != null && sourceVlanId < 1) {
            this.sourceVlanId = null;
        } else {
            this.sourceVlanId = sourceVlanId;
        }

        this.source = source;
        this.dest = dest;
    }

    public Ping(Short sourceVlanId, NetworkEndpoint source, NetworkEndpoint dest) {
        this(UUID.randomUUID(), sourceVlanId, source, dest);
    }

    public Ping(FlowDto flow) {
        this(UUID.randomUUID(), (short) flow.getSourceVlan(),
                new NetworkEndpoint(flow.getSourceSwitch(), flow.getSourcePort()),
                new NetworkEndpoint(flow.getDestinationSwitch(), flow.getDestinationPort()));
    }

    @Override
    public String toString() {
        String sourceEndpoint = source.getDatapath().toString();
        if (sourceVlanId != null) {
            sourceEndpoint += String.format("-%d", sourceVlanId);
        }

        return String.format("%s ===( ping{%s} )===> %s", sourceEndpoint, pingId, dest.getDatapath());
    }
}
