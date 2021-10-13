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

package org.openkilda.northbound.dto.v2.yflows;

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import javax.validation.constraints.PositiveOrZero;

@Data
@Builder
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class YFlowPatchPayload {
    YFlowPatchSharedEndpoint sharedEndpoint;

    @PositiveOrZero(message = "maximumBandwidth can't be negative")
    Long maximumBandwidth;
    String pathComputationStrategy;
    String encapsulationType;
    @PositiveOrZero(message = "maxLatency can't be negative")
    Long maxLatency;
    @PositiveOrZero(message = "maxLatencyTier2 can't be negative")
    Long maxLatencyTier2;
    Boolean ignoreBandwidth;
    Boolean periodicPings;
    Boolean pinned;
    Integer priority;
    Boolean strictBandwidth;
    String description;
    Boolean allocateProtectedPath;

    List<SubFlowPatchPayload> subFlows;
}
