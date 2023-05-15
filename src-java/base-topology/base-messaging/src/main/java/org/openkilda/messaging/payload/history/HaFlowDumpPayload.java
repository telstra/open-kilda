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

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.model.history.DumpType;

import com.fasterxml.jackson.databind.PropertyNamingStrategy.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@AllArgsConstructor
@Builder
@JsonNaming(SnakeCaseStrategy.class)
public class HaFlowDumpPayload {
    String taskId;
    DumpType dumpType;
    String haFlowId;
    SwitchId sharedSwitchId;
    int sharedPort;
    int sharedOuterVlan;
    int sharedInnerVlan;
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
    String diverseGroupId;
    String affinityGroupId;
    PathId forwardPathId;
    PathId reversePathId;
    PathId protectedForwardPathId;
    PathId protectedReversePathId;
    String paths;
    String haSubFlows;
    FlowStatus status;
    Instant flowTimeCreate;
    Instant flowTimeModify;
}
