/* Copyright 2021 Telstra Open Source
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

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.PathComputationStrategy;

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
@JsonNaming(value = SnakeCaseStrategy.class)
public class FlowPatch {
    private String flowId;
    private PatchEndpoint source;
    private PatchEndpoint destination;
    private Long bandwidth;
    private Boolean allocateProtectedPath;
    private Long maxLatency;
    private Long maxLatencyTier2;
    private Integer priority;
    private Boolean periodicPings;
    private PathComputationStrategy pathComputationStrategy;
    private PathComputationStrategy targetPathComputationStrategy;
    private String diverseFlowId;
    private String affinityFlowId;
    private Boolean pinned;
    private Boolean ignoreBandwidth;
    private Boolean strictBandwidth;
    private String description;
    private FlowEncapsulationType encapsulationType;
    private Set<Integer> vlanStatistics;
}
