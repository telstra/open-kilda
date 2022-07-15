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

package org.openkilda.messaging.payload.history;

import org.openkilda.messaging.model.MirrorPointStatusDto;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlowDumpPayload {
    private String type;

    private long bandwidth;

    private boolean ignoreBandwidth;

    private boolean strictBandwidth;

    private String forwardCookie;

    private String reverseCookie;

    private String sourceSwitch;

    private String destinationSwitch;

    private int sourcePort;

    private int destinationPort;

    private int sourceVlan;

    private int destinationVlan;

    private int sourceInnerVlan;

    private int destinationInnerVlan;

    private Long forwardMeterId;

    private Long reverseMeterId;

    private String forwardPath;

    private String reversePath;

    private String forwardStatus;

    private String reverseStatus;

    private String diverseGroupId;

    private String affinityGroupId;

    private boolean allocateProtectedPath;

    private boolean pinned;

    private boolean periodicPings;

    private FlowEncapsulationType encapsulationType;

    private PathComputationStrategy pathComputationStrategy;

    private long maxLatency;

    private Long maxLatencyTier2;

    private Integer priority;

    private SwitchId loopSwitchId;

    private List<MirrorPointStatusDto> mirrorPointStatuses;

}
