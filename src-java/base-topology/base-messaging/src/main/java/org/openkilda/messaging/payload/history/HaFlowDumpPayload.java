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

package org.openkilda.messaging.payload.history;

import org.openkilda.messaging.validation.JsonIncludeObjectHavingNonNullFieldOrNonEmptyList;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.history.DumpType;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.io.Serializable;
import java.util.List;

@Value
@AllArgsConstructor
@Builder
@JsonNaming(SnakeCaseStrategy.class)
public class HaFlowDumpPayload implements Serializable {
    String taskId;
    DumpType dumpType;
    String haFlowId;
    String sharedSwitchId;
    Integer sharedPort;
    Integer sharedOuterVlan;
    Integer sharedInnerVlan;
    Long maximumBandwidth;
    PathComputationStrategy pathComputationStrategy;
    FlowEncapsulationType encapsulationType;
    Long maxLatency;
    Long maxLatencyTier2;
    Boolean ignoreBandwidth;
    Boolean periodicPings;
    Boolean pinned;
    Integer priority;
    Boolean strictBandwidth;
    String description;
    Boolean allocateProtectedPath;
    String diverseGroupId;
    String affinityGroupId;
    FlowStatus status;
    String statusInfo;
    String flowTimeCreate;
    String flowTimeModify;

    List<HaSubFlowPayload> haSubFlows;
    HaFlowPathPayload forwardPath;
    HaFlowPathPayload reversePath;
    @JsonInclude(value = JsonInclude.Include.CUSTOM,
            valueFilter = JsonIncludeObjectHavingNonNullFieldOrNonEmptyList.class)
    HaFlowPathPayload protectedForwardPath;
    @JsonInclude(value = JsonInclude.Include.CUSTOM,
            valueFilter = JsonIncludeObjectHavingNonNullFieldOrNonEmptyList.class)
    HaFlowPathPayload protectedReversePath;
}
