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
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.PositiveOrZero;

@Data
@Builder
@AllArgsConstructor
@JsonNaming(value = SnakeCaseStrategy.class)
public class FlowRequestV2 {
    @NotBlank(message = "flowId should be provided")
    @NonNull
    private String flowId;
    @Valid
    @NonNull
    private FlowEndpointV2 source;
    @Valid
    @NonNull
    private FlowEndpointV2 destination;
    @PositiveOrZero(message = "maximumBandwidth can't be negative")
    private long maximumBandwidth;
    private boolean ignoreBandwidth;
    private boolean strictBandwidth;
    private boolean periodicPings;
    private String description;
    @PositiveOrZero(message = "maxLatency can't be negative")
    private Long maxLatency;
    @PositiveOrZero(message = "maxLatencyTier2 can't be negative")
    private Long maxLatencyTier2;
    private Integer priority;

    private String diverseFlowId;
    private String affinityFlowId;
    private boolean pinned;
    private boolean allocateProtectedPath;
    private String encapsulationType;
    private String pathComputationStrategy;
    private FlowStatistics statistics;
}
