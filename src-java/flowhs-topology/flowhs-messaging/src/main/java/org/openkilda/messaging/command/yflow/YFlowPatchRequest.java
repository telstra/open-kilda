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

import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.payload.flow.FlowEncapsulationType;
import org.openkilda.model.FlowEndpoint;

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Represents a patch request for y-flow.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@JsonNaming(value = SnakeCaseStrategy.class)
public class YFlowPatchRequest extends CommandData {
    private static final long serialVersionUID = 1L;

    String flowId;
    FlowEndpoint sharedEndpoint;
    Long maximumBandwidth;
    String pathComputationStrategy;
    FlowEncapsulationType encapsulationType;
    Long maxLatency;
    Long maxLatencyTier2;
    Boolean ignoreBandwidth;
    Boolean periodicPings;
    Boolean pinned;
    Integer priority;
    Boolean strictBandwidth;
    String description;
    List<SubFlowDto> subFlows;
}
