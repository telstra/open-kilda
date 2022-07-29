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

package org.openkilda.wfm.share.history.model;

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.MeterId;
import org.openkilda.model.MirrorPointStatus;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
public class FlowDumpData implements Serializable {
    private DumpType dumpType;
    private String flowId;
    private long bandwidth;
    private boolean ignoreBandwidth;
    private boolean strictBandwidth;
    private SwitchId sourceSwitch;
    private SwitchId destinationSwitch;
    private int sourcePort;
    private int destinationPort;
    private int sourceVlan;
    private int destinationVlan;
    private int sourceInnerVlan;
    private int destinationInnerVlan;
    private FlowSegmentCookie forwardCookie;
    private FlowSegmentCookie reverseCookie;
    private MeterId forwardMeterId;
    private MeterId reverseMeterId;
    private String forwardPath;
    private String reversePath;
    private FlowPathStatus forwardStatus;
    private FlowPathStatus reverseStatus;
    private String diverseGroupId;
    private String affinityGroupId;
    private boolean allocateProtectedPath;
    private boolean pinned;
    private boolean periodicPings;
    private FlowEncapsulationType encapsulationType;
    private PathComputationStrategy pathComputationStrategy;
    private long maxLatency;
    private long maxLatencyTier2;
    private List<MirrorPointStatus> mirrorPointStatuses;
    private Integer priority;
    private SwitchId loopSwitchId;

    @AllArgsConstructor
    @Getter
    public enum DumpType {
        STATE_BEFORE("stateBefore"),
        STATE_AFTER("stateAfter");

        private String type;
    }
}
