/* Copyright 2023 Telstra Open Source
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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import javax.validation.constraints.PositiveOrZero;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PathValidationPayload {
    @JsonProperty("bandwidth")
    Long bandwidth;

    @JsonProperty("max_latency")
    @PositiveOrZero(message = "max_latency cannot be negative")
    Long latencyMs;

    @JsonProperty("max_latency_tier2")
    @PositiveOrZero(message = "max_latency_tier2 cannot be negative")
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
}
