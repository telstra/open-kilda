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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Singular;

import java.util.List;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class YFlowCreatePayload extends YFlowCreateUpdatePayloadBase {
    @JsonProperty("y_flow_id")
    String yFlowId;

    @Builder
    public YFlowCreatePayload(
            String yFlowId,
            YFlowSharedEndpoint sharedEndpoint, long maximumBandwidth, String pathComputationStrategy,
            String encapsulationType, Long maxLatency, Long maxLatencyTier2, boolean ignoreBandwidth,
            boolean periodicPings, boolean pinned, Integer priority, boolean strictBandwidth, String description,
            boolean allocateProtectedPath, String diverseFlowId,
            @Singular List<SubFlowUpdatePayload> subFlows) {
        super(
                sharedEndpoint, maximumBandwidth, pathComputationStrategy, encapsulationType, maxLatency,
                maxLatencyTier2, ignoreBandwidth, periodicPings, pinned, priority, strictBandwidth, description,
                allocateProtectedPath, diverseFlowId, subFlows);
        this.yFlowId = yFlowId;
    }
}
