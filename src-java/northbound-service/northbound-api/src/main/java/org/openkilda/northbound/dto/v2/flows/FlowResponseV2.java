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

package org.openkilda.northbound.dto.v2.flows;

import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.util.Set;

@Data
@Builder
@AllArgsConstructor
@JsonNaming(value = SnakeCaseStrategy.class)
public class FlowResponseV2 {
    @NonNull
    private String flowId;
    @NonNull
    private FlowEndpointV2 source;
    @NonNull
    private FlowEndpointV2 destination;
    @NonNull
    private String status;
    private PathStatus statusDetails;
    private String statusInfo;

    private long maximumBandwidth;
    private boolean ignoreBandwidth;
    private boolean periodicPings;
    private String description;
    private Long maxLatency;
    private Long maxLatencyTier2;
    private Integer priority;

    private Set<String> diverseWith;
    private boolean pinned;
    private boolean allocateProtectedPath;
    private String encapsulationType;
    private String pathComputationStrategy;
    private String targetPathComputationStrategy;
    private SwitchId loopSwitchId;

    private String created;
    private String lastUpdated;
}
