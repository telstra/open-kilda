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
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;

import lombok.Builder;
import lombok.Value;

import java.io.Serializable;
import java.time.Instant;

@Value
@Builder
public class HaFlowDumpData implements Serializable {
    DumpType dumpType;
    String taskId;

    String haFlowId;

    String affinityGroupId;
    boolean allocateProtectedPath;
    String description;
    String diverseGroupId;
    FlowEncapsulationType encapsulationType;
    Instant flowTimeCreate;
    Instant flowTimeModify;
    PathId forwardPathId;
    String haSubFlows;
    boolean ignoreBandwidth;
    Long maxLatency;
    Long maxLatencyTier2;
    long maximumBandwidth;
    PathComputationStrategy pathComputationStrategy;
    String paths;
    boolean periodicPings;
    boolean pinned;
    Integer priority;
    PathId protectedForwardPathId;
    PathId protectedReversePathId;
    PathId reversePathId;
    int sharedInnerVlan;
    int sharedOuterVlan;
    int sharedPort;
    SwitchId sharedSwitchId;
    FlowStatus status;
    boolean strictBandwidth;
}
