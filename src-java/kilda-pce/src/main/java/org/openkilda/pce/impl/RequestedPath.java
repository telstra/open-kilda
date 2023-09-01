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

package org.openkilda.pce.impl;

import org.openkilda.model.Flow;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RequestedPath {
    SwitchId srcSwitchId;
    SwitchId dstSwitchId;
    long bandwidth;
    boolean ignoreBandwidth;
    PathComputationStrategy strategy;
    Long maxLatency;
    Long maxLatencyTier2;
    String name;

    public RequestedPath(Flow flow) {
        this(flow.getSrcSwitchId(), flow.getDestSwitchId(), flow.getBandwidth(), flow.isIgnoreBandwidth(),
                flow.getPathComputationStrategy(), flow.getMaxLatency(), flow.getMaxLatencyTier2(),
                flow.getFlowId());
    }

    public RequestedPath(HaFlow haFlow, HaSubFlow subFlow) {
        this(haFlow.getSharedSwitchId(), subFlow.getEndpointSwitchId(), haFlow.getMaximumBandwidth(),
                haFlow.isIgnoreBandwidth(), haFlow.getPathComputationStrategy(), haFlow.getMaxLatency(),
                haFlow.getMaxLatencyTier2(), haFlow.getHaFlowId());
    }

    public boolean isOneSwitch() {
        return srcSwitchId.equals(dstSwitchId);
    }
}
