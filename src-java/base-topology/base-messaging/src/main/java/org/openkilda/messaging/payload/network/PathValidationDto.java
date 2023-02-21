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

package org.openkilda.messaging.payload.network;

import org.openkilda.messaging.payload.flow.FlowEncapsulationType;
import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.model.PathComputationStrategy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PathValidationDto {
    @JsonProperty("bandwidth")
    Long bandwidth;

    @JsonProperty("max_latency")
    Long latencyMs;

    @JsonProperty("max_latency_tier2")
    Long latencyTier2ms;

    @JsonProperty("nodes")
    List<PathNodePayload> nodes;

    @JsonProperty("diverse_with_flow")
    String diverseWithFlow;

    @JsonProperty("reuse_flow_resources")
    String reuseFlowResources;

    @JsonProperty("flow_encapsulation_type")
    FlowEncapsulationType flowEncapsulationType;

    @JsonProperty("path_computation_strategy")
    PathComputationStrategy pathComputationStrategy;

    @JsonCreator
    public PathValidationDto(@JsonProperty("bandwidth") Long bandwidth,
                             @JsonProperty("latency_ms") Long latencyMs,
                             @JsonProperty("max_latency_tier2") Long latencyTier2ms,
                             @JsonProperty("nodes") List<PathNodePayload> nodes,
                             @JsonProperty("diverse_with_flow") String diverseWithFlow,
                             @JsonProperty("reuse_flow_resources") String reuseFlowResources,
                             @JsonProperty("flow_encapsulation_type") FlowEncapsulationType flowEncapsulationType,
                             @JsonProperty("path_computation_strategy") PathComputationStrategy computationStrategy) {
        this.bandwidth = bandwidth;
        this.latencyMs = latencyMs;
        this.latencyTier2ms = latencyTier2ms;
        this.nodes = nodes;
        this.diverseWithFlow = diverseWithFlow;
        this.reuseFlowResources = reuseFlowResources;
        this.flowEncapsulationType = flowEncapsulationType;
        this.pathComputationStrategy = computationStrategy;
    }
}
