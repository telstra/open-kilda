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

package org.openkilda.messaging.payload.flow;

import org.openkilda.messaging.Utils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowUpdatePayload extends FlowPayload {

    @JsonProperty("diverse-flowid")
    private String diverseFlowId;

    /**
     * Instance constructor.
     *
     * @param id                        flow id
     * @param source                    flow source
     * @param destination               flow destination
     * @param maximumBandwidth          flow maximum bandwidth
     * @param ignoreBandwidth           should ignore bandwidth in path computation
     * @param periodicPings             enable periodic flow pings
     * @param allocateProtectedPath     allocate flow protected path
     * @param description               flow description
     * @param created                   flow created timestamp
     * @param lastUpdated               flow last updated timestamp
     * @param diverseFlowId             make new flow diverse with FlowId
     * @param status                    flow status
     * @param maxLatency                max latency
     * @param priority                  flow priority
     * @param pinned                    pinned flag
     * @param encapsulationType         flow encapsulation type
     * @param pathComputationStrategy   path computation strategy
     */
    @JsonCreator
    public FlowUpdatePayload(@JsonProperty(Utils.FLOW_ID) String id,
                             @JsonProperty("source") FlowEndpointPayload source,
                             @JsonProperty("destination") FlowEndpointPayload destination,
                             @JsonProperty("maximum-bandwidth") long maximumBandwidth,
                             @JsonProperty("ignore_bandwidth") Boolean ignoreBandwidth,
                             @JsonProperty("periodic-pings") Boolean periodicPings,
                             @JsonProperty("allocate_protected_path") Boolean allocateProtectedPath,
                             @JsonProperty("description") String description,
                             @JsonProperty("created") String created,
                             @JsonProperty("last-updated") String lastUpdated,
                             @JsonProperty("diverse-flowid") String diverseFlowId,
                             @JsonProperty("status") String status,
                             @JsonProperty("max-latency") Long maxLatency,
                             @JsonProperty("priority") Integer priority,
                             @JsonProperty("pinned") Boolean pinned,
                             @JsonProperty("encapsulation-type") String encapsulationType,
                             @JsonProperty("path-computation-strategy") String pathComputationStrategy) {
        super(id, source, destination, maximumBandwidth, ignoreBandwidth, periodicPings, allocateProtectedPath,
                description, created, lastUpdated, status, maxLatency, priority, pinned, encapsulationType,
                pathComputationStrategy);
        this.diverseFlowId = diverseFlowId;
    }
}
