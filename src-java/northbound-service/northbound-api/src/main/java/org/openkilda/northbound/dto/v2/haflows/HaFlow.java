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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Set;

@Data
@Builder
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(Include.NON_NULL)
public class HaFlow {
    String haFlowId;
    String status;

    HaFlowSharedEndpoint sharedEndpoint;

    long maximumBandwidth;
    String pathComputationStrategy;
    String encapsulationType;
    Long maxLatency;
    Long maxLatencyTier2;
    boolean ignoreBandwidth;
    boolean periodicPings;
    boolean pinned;
    Integer priority;
    boolean strictBandwidth;
    String description;
    boolean allocateProtectedPath;

    Set<String> diverseWithFlows;
    @JsonProperty("diverse_with_y_flows")
    Set<String> diverseWithYFlows;
    Set<String> diverseWithHaFlows;

    List<HaSubFlow> subFlows;

    String timeCreate;
    String timeUpdate;
}
