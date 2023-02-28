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

package org.openkilda.northbound.dto.v2.haflows;

import org.openkilda.northbound.dto.utils.Constraints;

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.PositiveOrZero;

@Data
@Builder
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HaFlowUpdatePayload {
    HaFlowSharedEndpoint sharedEndpoint;

    @PositiveOrZero(message = Constraints.NEGATIVE_MAXIMUM_BANDWIDTH_MESSAGE)
    long maximumBandwidth;
    @NotBlank(message = Constraints.BLANK_PATH_COMPUTATION_STRATEGY_MESSAGE)
    String pathComputationStrategy;
    @NotBlank(message = Constraints.BLANK_ENCAPSULATION_TYPE_MESSAGE)
    String encapsulationType;
    @PositiveOrZero(message = Constraints.NEGATIVE_MAX_LATENCY_MESSAGE)
    Long maxLatency;
    @PositiveOrZero(message = Constraints.NEGATIVE_MAX_LATENCY_TIER_2_MESSAGE)
    Long maxLatencyTier2;
    boolean ignoreBandwidth;
    boolean periodicPings;
    boolean pinned;
    Integer priority;
    boolean strictBandwidth;
    String description;
    boolean allocateProtectedPath;
    String diverseFlowId;

    List<HaSubFlowUpdatePayload> subFlows;
}
