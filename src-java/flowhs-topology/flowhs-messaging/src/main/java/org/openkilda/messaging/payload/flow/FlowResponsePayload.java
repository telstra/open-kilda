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
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonInclude(Include.NON_NULL)
public class FlowResponsePayload extends FlowPayload {

    @JsonProperty("diverse_with")
    private List<String> diverseWith;

    @JsonProperty("status-details")
    private FlowStatusDetailsPayload flowStatusDetails;

    @JsonProperty("status_info")
    private String statusInfo;

    @JsonProperty("target-path-computation-strategy")
    private String targetPathComputationStrategy;

    @JsonProperty("loop-switch-id")
    private SwitchId loopSwitchId;

    @JsonProperty("forward-latency")
    private long forwardLatency;

    @JsonProperty("reverse-latency")
    private long reverseLatency;

    @JsonProperty("latency-last-modified-time")
    private String latencyLastModifiedTime;

    @JsonProperty("y_flow_id")
    @JsonInclude(Include.NON_NULL)
    private String yFlowId;

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
     * @param status                    flow status
     * @param flowStatusDetails         flow status details
     * @param statusInfo                flow status info
     * @param maxLatency                max latency
     * @param priority                  flow priority
     * @param diverseWith               diverse with flows id
     * @param pinned                    pinned flag
     * @param encapsulationType         flow encapsulation type
     * @param pathComputationStrategy   path computation strategy
     * @param targetPathComputationStrategy     target path computation strategy
     * @param loopSwitchId              looped switch id
     * @param forwardLatency            forward path latency nanoseconds
     * @param reverseLatency            reverse path latency nanoseconds
     * @param latencyLastModifiedTime   latency last modified time
     * @param yFlowId                   the y-flow ID in the case of sub-flow
     */
    @Builder(builderMethodName = "flowResponsePayloadBuilder")
    @JsonCreator
    public FlowResponsePayload(@JsonProperty(Utils.FLOW_ID) String id,
                               @JsonProperty("source") FlowEndpointPayload source,
                               @JsonProperty("destination") FlowEndpointPayload destination,
                               @JsonProperty("maximum-bandwidth") long maximumBandwidth,
                               @JsonProperty("ignore_bandwidth") Boolean ignoreBandwidth,
                               @JsonProperty("periodic-pings") Boolean periodicPings,
                               @JsonProperty("allocate_protected_path") Boolean allocateProtectedPath,
                               @JsonProperty("description") String description,
                               @JsonProperty("created") String created,
                               @JsonProperty("last-updated") String lastUpdated,
                               @JsonProperty("status") String status,
                               @JsonProperty("status-details") FlowStatusDetailsPayload flowStatusDetails,
                               @JsonProperty("status_info") String statusInfo,
                               @JsonProperty("max-latency") Long maxLatency,
                               @JsonProperty("priority") Integer priority,
                               @JsonProperty("diverse_with") List<String> diverseWith,
                               @JsonProperty("pinned") Boolean pinned,
                               @JsonProperty("encapsulation-type") String encapsulationType,
                               @JsonProperty("path-computation-strategy") String pathComputationStrategy,
                               @JsonProperty("target-path-computation-strategy") String targetPathComputationStrategy,
                               @JsonProperty("loop-switch-id") SwitchId loopSwitchId,
                               @JsonProperty("forward-latency") long forwardLatency,
                               @JsonProperty("reverse-latency") long reverseLatency,
                               @JsonProperty("latency-last-modified-time") String latencyLastModifiedTime,
                               @JsonProperty("y_flow_id") String yFlowId) {
        super(id, source, destination, maximumBandwidth, ignoreBandwidth, periodicPings, allocateProtectedPath,
                description, created, lastUpdated, status, maxLatency, priority, pinned, encapsulationType,
                pathComputationStrategy);
        this.diverseWith = diverseWith;
        this.flowStatusDetails = flowStatusDetails;
        this.statusInfo = statusInfo;
        this.targetPathComputationStrategy = targetPathComputationStrategy;
        this.loopSwitchId = loopSwitchId;
        this.forwardLatency = forwardLatency;
        this.reverseLatency = reverseLatency;
        this.latencyLastModifiedTime = latencyLastModifiedTime;
        this.yFlowId = yFlowId;
    }
}
