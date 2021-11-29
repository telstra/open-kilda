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

package org.openkilda.messaging.command.yflow;

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

@Data
@JsonNaming(value = SnakeCaseStrategy.class)
public class YFlowDto implements Serializable {
    private static final long serialVersionUID = 1L;

    String yFlowId;
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
    SwitchId yPoint;
    SwitchId protectedPathYPoint;
    List<SubFlowDto> subFlows;
    Instant timeCreate;
    Instant timeUpdate;
}
