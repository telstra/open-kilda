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

package org.openkilda.wfm.share.history.model;

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

@Value
@Builder
@AllArgsConstructor
@JsonNaming(SnakeCaseStrategy.class)
public class HaFlowDumpData implements Serializable {
    DumpType dumpType;
    String taskId;

    String haFlowId;

    String affinityGroupId;
    Boolean allocateProtectedPath;
    String description;
    String diverseGroupId;
    FlowEncapsulationType encapsulationType;
    Instant flowTimeCreate;
    Instant flowTimeModify;
    List<HaSubFlowDump> haSubFlows;
    Boolean ignoreBandwidth;
    Long maxLatency;
    Long maxLatencyTier2;
    Long maximumBandwidth;
    PathComputationStrategy pathComputationStrategy;
    Boolean periodicPings;
    Boolean pinned;
    Integer priority;
    Integer sharedInnerVlan;
    Integer sharedOuterVlan;
    Integer sharedPort;
    SwitchId sharedSwitchId;
    FlowStatus status;
    String statusInfo;
    Boolean strictBandwidth;

    HaFlowPathDump forwardPath;
    HaFlowPathDump reversePath;
    HaFlowPathDump protectedForwardPath;
    HaFlowPathDump protectedReversePath;

    public static HaFlowDumpData empty() {
        return HaFlowDumpData.builder().build();
    }
}
