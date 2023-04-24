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

package org.openkilda.persistence.ferma.repositories;

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.GroupId;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;

import com.google.common.collect.Lists;

import java.util.List;

public final class FermaModelUtils {
    private FermaModelUtils() {
    }

    /**
     * Builds HaFlowPath object.
     */
    public static HaFlowPath buildHaFlowPath(
            PathId pathId, long bandwidth, FlowSegmentCookie cookie, MeterId sharedMeterId,
            MeterId yPointMeterId, Switch sharedSwitch, SwitchId yPointSwitchId, GroupId yPointGroupId) {
        return HaFlowPath.builder()
                .haPathId(pathId)
                .bandwidth(bandwidth)
                .ignoreBandwidth(true)
                .cookie(cookie)
                .sharedPointMeterId(sharedMeterId)
                .yPointMeterId(yPointMeterId)
                .sharedSwitch(sharedSwitch)
                .yPointSwitchId(yPointSwitchId)
                .status(FlowPathStatus.ACTIVE)
                .yPointGroupId(yPointGroupId)
                .build();
    }

    /**
     * Builds HaSubFlow object.
     */
    public static HaSubFlow buildHaSubFlow(
            String subFlowId, Switch sw, int port, int vlan, int innerVlan, String description) {
        return HaSubFlow.builder()
                .haSubFlowId(subFlowId)
                .endpointSwitch(sw)
                .endpointPort(port)
                .endpointVlan(vlan)
                .endpointInnerVlan(innerVlan)
                .status(FlowStatus.UP)
                .description(description)
                .build();
    }

    /**
     * Builds HaFlow object.
     */
    public static HaFlow buildHaFlow(String flowId, Switch sharedSwitch) {
        return buildHaFlow(
                flowId, sharedSwitch, 0, 0, 0, 0, 0, 0, null, 0, null, null, null, true, true, true, true, true);
    }

    /**
     * Builds HaFlow object.
     */
    public static HaFlow buildHaFlow(
            String flowId, Switch sharedSwitch, int port, int vlan, int innerVlan, long latency, long latencyTier2,
            long bandwidth, FlowEncapsulationType encapsulationType, int priority, String description,
            PathComputationStrategy strategy, FlowStatus status, boolean protectedPath, boolean pinned, boolean pings,
            boolean ignoreBandwidth, boolean strictBandwidth) {
        return HaFlow.builder()
                .haFlowId(flowId)
                .sharedSwitch(sharedSwitch)
                .sharedPort(port)
                .sharedOuterVlan(vlan)
                .sharedInnerVlan(innerVlan)
                .maxLatency(latency)
                .maxLatencyTier2(latencyTier2)
                .maximumBandwidth(bandwidth)
                .encapsulationType(encapsulationType)
                .priority(priority)
                .description(description)
                .pathComputationStrategy(strategy)
                .status(status)
                .allocateProtectedPath(protectedPath)
                .pinned(pinned)
                .periodicPings(pings)
                .ignoreBandwidth(ignoreBandwidth)
                .strictBandwidth(strictBandwidth)
                .build();
    }

    /**
     * Builds FlowPath object.
     */
    public static FlowPath buildPath(
            PathId pathId, HaFlowPath haFlowPath, Switch srcSwitch, Switch dstSwitch) {
        return FlowPath.builder()
                .pathId(pathId)
                .srcSwitch(srcSwitch)
                .destSwitch(dstSwitch)
                .haFlowPath(haFlowPath)
                .build();
    }

    /**
     * Builds 2 PathSegment objects.
     */
    public static List<PathSegment> buildSegments(PathId pathId, Switch switch1, Switch switch2, Switch switch3) {
        PathSegment segment1 = PathSegment.builder()
                .pathId(pathId).srcSwitch(switch1).destSwitch(switch3).build();
        PathSegment segment2 = PathSegment.builder()
                .pathId(pathId).srcSwitch(switch3).destSwitch(switch2).build();
        return Lists.newArrayList(segment1, segment2);
    }
}
