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

import org.openkilda.messaging.command.CommandData;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.PathComputationStrategy;

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class HaFlowRequest extends CommandData {
    private static final long serialVersionUID = 1L;

    String haFlowId;
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
    String diverseFlowId;
    List<HaSubFlowDto> subFlows;
    Type type;

    public enum Type {
        CREATE,
        UPDATE
    }

    /**
     * Gets HA-sub flow by its id.
     */
    public HaSubFlowDto getHaSubFlow(String haSubFlowId) {
        for (HaSubFlowDto subFlow : subFlows) {
            if (haSubFlowId.equals(subFlow.getFlowId())) {
                return subFlow;
            }
        }
        throw new IllegalArgumentException(String.format("HA-sub flow %s not found. Valid ha-sub flows are: %s",
                haSubFlowId, subFlows.stream().map(HaSubFlowDto::getFlowId).collect(Collectors.toList())));
    }

}
