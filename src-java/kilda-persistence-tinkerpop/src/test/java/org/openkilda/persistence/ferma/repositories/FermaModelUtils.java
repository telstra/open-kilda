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
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.GroupId;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlow.HaSharedEndpoint;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.HaSubFlowEdge;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;

import com.google.common.collect.Sets;

import java.util.Set;

public final class FermaModelUtils {
    private FermaModelUtils() {
    }

    /**
     * Builds HaFlowPath object.
     */
    public static HaFlowPath buildHaFlowPath(
            PathId pathId, long bandwidth, FlowSegmentCookie cookie, String sharedGroupId, MeterId sharedMeterId,
            MeterId yPointMeterId, Switch sharedSwitch, SwitchId yPointSwitchId, GroupId yPointGroupId) {
        return HaFlowPath.builder()
                .haPathId(pathId)
                .bandwidth(bandwidth)
                .ignoreBandwidth(true)
                .cookie(cookie)
                .sharedBandwidthGroupId(sharedGroupId)
                .sharedPointMeterId(sharedMeterId)
                .yPointMeterId(yPointMeterId)
                .sharedSwitch(sharedSwitch)
                .yPointSwitchId(yPointSwitchId)
                .status(FlowPathStatus.ACTIVE)
                .yPointGroupId(yPointGroupId)
                .build();
    }

    /**
     * Builds HaSubFlowEdge object.
     */
    public static HaSubFlowEdge buildHaSubFlowEdge(String haFlowId, HaSubFlow haSubFlow, MeterId meterId) {
        return HaSubFlowEdge.builder()
                .haFlowId(haFlowId)
                .haSubFlow(haSubFlow)
                .meterId(meterId)
                .build();
    }

    /**
     * Builds HaSubFlowEdge objects.
     */
    public static Set<HaSubFlowEdge> buildHaSubFlowEdges(
            String haFLowId, HaSubFlow subFlow1, HaSubFlow subFlow2, MeterId meterId1, MeterId meterId2) {
        HaSubFlowEdge edge1 = buildHaSubFlowEdge(haFLowId, subFlow1, meterId1);
        HaSubFlowEdge edge2 = buildHaSubFlowEdge(haFLowId, subFlow2, meterId2);
        return Sets.newHashSet(edge1, edge2);
    }

    /**
     * Builds HaSubFlow object.
     */
    public static HaSubFlow buildHaSubFlow(
            String subFlowId, SwitchId switchId, int port, int vlan, int innerVlan, String description) {
        return HaSubFlow.builder()
                .subFlowId(subFlowId)
                .endpointSwitchId(switchId)
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
    public static HaFlow buildHaFlow(
            String flowId, SwitchId switchId, int port, int vlan, int innerVlan, long latency, long latencyTier2,
            long bandwidth, FlowEncapsulationType encapsulationType, int priority, String description,
            PathComputationStrategy strategy, FlowStatus status, boolean protectedPath, boolean pinned, boolean pings,
            boolean ignoreBandwidth, boolean strictBandwidth) {
        return HaFlow.builder()
                .haFlowId(flowId)
                .sharedEndpoint(new HaSharedEndpoint(switchId, port, vlan, innerVlan))
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
}
