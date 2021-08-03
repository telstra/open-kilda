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

package org.openkilda.northbound.dto.v2.flows;

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(value = SnakeCaseStrategy.class)
public class FlowPatchV2 {
    private FlowPatchEndpoint source;
    private FlowPatchEndpoint destination;

    private Long maximumBandwidth;
    private Boolean ignoreBandwidth;
    private Boolean strictBandwidth;
    private Boolean periodicPings;
    private String description;
    private Long maxLatency;
    private Long maxLatencyTier2;
    private Integer priority;

    private String diverseFlowId;
    private String affinityFlowId;
    private Boolean pinned;
    private Boolean allocateProtectedPath;
    private String encapsulationType;
    private String pathComputationStrategy;
    private String targetPathComputationStrategy;
}
