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

package org.openkilda.messaging.payload.flow;

import org.openkilda.messaging.Utils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.io.Serializable;

// TODO(surabujin) move into api module
// TODO(surabujin) split into input/output
// FIXME(surabujin): reconsider usage of "equals" methods and set of properties in it
@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = {"status"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty(Utils.FLOW_ID)
    private String id;

    @NonNull
    @JsonProperty("source")
    private FlowEndpointPayload source;

    @NonNull
    @JsonProperty("destination")
    private FlowEndpointPayload destination;

    @JsonProperty("maximum-bandwidth")
    private long maximumBandwidth;

    @JsonProperty("ignore_bandwidth")
    private boolean ignoreBandwidth = false;

    @JsonProperty("periodic-pings")
    private boolean periodicPings = false;

    @JsonProperty("description")
    private String description;

    @JsonProperty("created")
    private String created;

    @JsonProperty("last-updated")
    private String lastUpdated;

    @JsonProperty("status")
    private String status;

    @JsonProperty("max-latency")
    private Integer maxLatency;

    @JsonProperty("priority")
    private Integer priority;

    /**
     * Instance constructor.
     *
     * @param id               flow id
     * @param source           flow source
     * @param destination      flow destination
     * @param maximumBandwidth flow maximum bandwidth
     * @param ignoreBandwidth  should ignore bandwidth in path computation
     * @param periodicPings    enable periodic flow pings
     * @param description      flow description
     * @param created          flow created timestamp
     * @param lastUpdated      flow last updated timestamp
     * @param status           flow status
     * @param maxLatency       max latency
     * @param priority         flow priority
     */
    @Builder
    @JsonCreator
    public FlowPayload(@JsonProperty(Utils.FLOW_ID) String id,
                       @JsonProperty("source") FlowEndpointPayload source,
                       @JsonProperty("destination") FlowEndpointPayload destination,
                       @JsonProperty("maximum-bandwidth") long maximumBandwidth,
                       @JsonProperty("ignore_bandwidth") Boolean ignoreBandwidth,
                       @JsonProperty("periodic-pings") Boolean periodicPings,
                       @JsonProperty("description") String description,
                       @JsonProperty("created") String created,
                       @JsonProperty("last-updated") String lastUpdated,
                       @JsonProperty("status") String status,
                       @JsonProperty("max-latency") Integer maxLatency,
                       @JsonProperty("priority") Integer priority) {
        setId(id);
        setSource(source);
        setDestination(destination);
        setMaximumBandwidth(maximumBandwidth);

        if (ignoreBandwidth != null) {
            this.ignoreBandwidth = ignoreBandwidth;
        }
        if (periodicPings != null) {
            this.periodicPings = periodicPings;
        }

        this.description = description;
        this.created = created;
        this.lastUpdated = lastUpdated;
        this.status = status;
        this.maxLatency = maxLatency;
        this.priority = priority;
    }

    /**
     * Sets flow id.
     */
    public void setId(String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("need to set id");
        }
        this.id = id;
    }

    /**
     * Sets maximum bandwidth.
     */
    public void setMaximumBandwidth(long maximumBandwidth) {
        if (maximumBandwidth < 0L) {
            throw new IllegalArgumentException("need to set non negative bandwidth");
        }
        this.maximumBandwidth = maximumBandwidth;
    }
}
