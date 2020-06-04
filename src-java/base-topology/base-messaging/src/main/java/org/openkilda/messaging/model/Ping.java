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

import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.SwitchId;

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
        DEST_NOT_AVAILABLE,
        INCORRECT_REQUEST
    }

    @JsonProperty(value = "ping_id", required = true)
    UUID pingId;

    @JsonProperty(value = "source", required = true)
    NetworkEndpoint source;

    @JsonProperty(value = "dest", required = true)
    NetworkEndpoint dest;

    @JsonProperty(value = "transit_encapsulation", required = true)
    FlowTransitEncapsulation transitEncapsulation;

    @JsonProperty(value = "isl_port", required = true)
    int islPort;

    @JsonCreator
    public Ping(
            @JsonProperty("ping_id") UUID pingId,
            @JsonProperty("source") NetworkEndpoint source,
            @JsonProperty("dest") NetworkEndpoint dest,
            @JsonProperty("transit_encapsulation") FlowTransitEncapsulation transitEncapsulation,
            @JsonProperty("isl_port") int islPort) {

        this.pingId = pingId;

        this.source = source;
        this.dest = dest;

        this.transitEncapsulation = transitEncapsulation;
        this.islPort = islPort;
    }

    public Ping(NetworkEndpoint source, NetworkEndpoint dest,
                FlowTransitEncapsulation transitEncapsulation, int islPort) {
        this(UUID.randomUUID(), source, dest, transitEncapsulation, islPort);
    }

    @Override
    public String toString() {
        String sourceEndpoint = formatEndpoint(source.getDatapath(), source.getPortNumber());
        return String.format("%s ===( ping{%s} )===> %s", sourceEndpoint, pingId, dest.getDatapath());
    }

    /**
     * Make string representation of ping endpoint(ingress). Used mostly for logging.
     */
    public static String formatEndpoint(SwitchId swId, int portNumber) {
        return String.format("%s-%d", swId, portNumber);
    }
}
