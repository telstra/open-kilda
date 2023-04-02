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

package org.openkilda.messaging.command.haflow;

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathComputationStrategy;

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Data
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class HaFlowDto implements Serializable {
    private static final long serialVersionUID = 1L;

    String haFlowId;
    FlowStatus status;
    FlowEndpoint sharedEndpoint;
    long maximumBandwidth;
    PathComputationStrategy pathComputationStrategy;
    FlowEncapsulationType encapsulationType;
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
    Set<String> diverseWithYFlows;
    Set<String> diverseWithHaFlows;
    List<HaSubFlowDto> subFlows;
    Instant timeCreate;
    Instant timeUpdate;
}
