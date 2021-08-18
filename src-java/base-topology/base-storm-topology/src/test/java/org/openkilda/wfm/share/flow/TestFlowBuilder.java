/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.share.flow;

import static com.google.common.base.Preconditions.checkArgument;

import org.openkilda.model.DetectConnectedDevices;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.TransitVlan;
import org.openkilda.model.Vxlan;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.wfm.share.flow.resources.EncapsulationResources;
import org.openkilda.wfm.share.flow.resources.transitvlan.TransitVlanEncapsulation;
import org.openkilda.wfm.share.flow.resources.vxlan.VxlanEncapsulation;
import org.openkilda.wfm.topology.flow.model.FlowPathsWithEncapsulation;
import org.openkilda.wfm.topology.flow.model.FlowPathsWithEncapsulation.FlowPathsWithEncapsulationBuilder;

import com.google.common.collect.Lists;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Setter
@Accessors(fluent = true)
public class TestFlowBuilder {

    private String flowId = UUID.randomUUID().toString();
    @Setter(AccessLevel.NONE)
    private final Endpoint source = new Endpoint();
    private int srcVlan;
    @Setter(AccessLevel.NONE)
    private final Endpoint destination = new Endpoint();
    private int destVlan;
    private final List<Endpoint> transit = new ArrayList<>();
    private final List<Endpoint> protectedTransit = new ArrayList<>();
    private long unmaskedCookie = 1;
    private long protectedUnmaskedCookie = 2;
    private long bandwidth;
    private boolean ignoreBandwidth = false;
    private int forwardTransitEncapsulationId = 101;
    private int reverseTransitEncapsulationId = 102;
    private int protectedForwardTransitEncapsulationId = 103;
    private int protectedReverseTransitEncapsulationId = 104;
    private long forwardMeterId = 1001;
    private long reverseMeterId = 1002;
    private long protectedForwardMeterId = 1003;
    private long protectedReverseMeterId = 1004;
    private FlowStatus status = FlowStatus.UP;
    private Long maxLatency = null;
    private Integer priority = null;
    private DetectConnectedDevices detectConnectedDevices = DetectConnectedDevices.builder().build();
    private FlowEncapsulationType encapsulationType;
    private PathComputationStrategy pathComputationStrategy;
    private String description;

    public TestFlowBuilder() {
    }

    public TestFlowBuilder(String flowId) {
        this.flowId = flowId;
    }

    public TestFlowBuilder srcSwitch(Switch srcSwitch) {
        source.sw = srcSwitch;
        return this;
    }

    public TestFlowBuilder srcSwitch(String srcSwitchId) {
        source.sw = Switch.builder().switchId(new SwitchId(srcSwitchId)).build();
        return this;
    }

    public TestFlowBuilder srcPort(int srcPort) {
        source.port = srcPort;
        return this;
    }

    public TestFlowBuilder destSwitch(Switch dstSwitch) {
        destination.sw = dstSwitch;
        return this;
    }

    public TestFlowBuilder destSwitch(String dstSwitchId) {
        destination.sw = Switch.builder().switchId(new SwitchId(dstSwitchId)).build();
        return this;
    }

    public TestFlowBuilder destPort(int dstPort) {
        destination.port = dstPort;
        return this;
    }

    public TestFlowBuilder addTransitionEndpoint(Switch tranSwitch, int tranPort) {
        transit.add(new Endpoint(tranSwitch, tranPort));
        return this;
    }

    public TestFlowBuilder addProtectedTransitionEndpoint(Switch tranSwitch, int tranPort) {
        protectedTransit.add(new Endpoint(tranSwitch, tranPort));
        return this;
    }

    /**
     * Build a Flow with set properties.
     */
    public Flow build() {
        Switch srcSwitch = source.sw;
        Switch destSwitch = destination.sw;
        if (srcSwitch.getSwitchId().equals(destSwitch.getSwitchId())) {
            checkArgument(transit.isEmpty(), "Transit endpoints were provided for a one-switch flow");
            checkArgument(protectedTransit.isEmpty(),
                    "ProtectedTransit endpoints were provided for a one-switch flow");
        } else {
            checkArgument(transit.isEmpty() || transit.size() % 2 == 0, "The number of transit endpoints is wrong");
        }
        checkArgument(protectedTransit.isEmpty() || protectedUnmaskedCookie != unmaskedCookie,
                "Same unmasked cookies provided with enabled ProtectedTransit");

        Flow flow = Flow.builder()
                .flowId(flowId)
                .srcSwitch(srcSwitch)
                .srcPort(source.port)
                .srcVlan(srcVlan)
                .destSwitch(destSwitch)
                .destPort(destination.port)
                .destVlan(destVlan)
                .bandwidth(bandwidth)
                .ignoreBandwidth(ignoreBandwidth)
                .encapsulationType(encapsulationType)
                .maxLatency(maxLatency)
                .priority(priority)
                .detectConnectedDevices(detectConnectedDevices)
                .pathComputationStrategy(pathComputationStrategy)
                .description(description)
                .build();
        flow.setStatus(status);

        FlowPath forwardPath =
                buildFlowPath(flow, srcSwitch, destSwitch, transit,
                        new FlowSegmentCookie(FlowPathDirection.FORWARD, unmaskedCookie),
                        new MeterId(forwardMeterId));
        flow.setForwardPath(forwardPath);
        FlowPath reversePath =
                buildFlowPath(flow, destSwitch, srcSwitch, Lists.reverse(transit),
                        new FlowSegmentCookie(FlowPathDirection.REVERSE, unmaskedCookie),
                        new MeterId(reverseMeterId));
        flow.setReversePath(reversePath);

        if (!protectedTransit.isEmpty()) {
            FlowPath protectedForwardPath =
                    buildFlowPath(flow, srcSwitch, destSwitch, protectedTransit,
                            new FlowSegmentCookie(FlowPathDirection.FORWARD, protectedUnmaskedCookie),
                            new MeterId(protectedForwardMeterId));
            flow.setProtectedForwardPath(protectedForwardPath);
            FlowPath protectedReversePath =
                    buildFlowPath(flow, destSwitch, srcSwitch, Lists.reverse(protectedTransit),
                            new FlowSegmentCookie(FlowPathDirection.REVERSE, protectedUnmaskedCookie),
                            new MeterId(protectedReverseMeterId));
            flow.setProtectedReversePath(protectedReversePath);
        }

        return flow;
    }

    private FlowPath buildFlowPath(
            Flow flow, Switch srcSwitch, Switch destSwitch, List<Endpoint> transitEndpoints,
            FlowSegmentCookie cookie, MeterId meterId) {
        PathId pathId = new PathId(UUID.randomUUID().toString());
        List<PathSegment> pathSegments = new ArrayList<>();
        if (!srcSwitch.getSwitchId().equals(destSwitch.getSwitchId())) {
            for (int i = 0; i < transitEndpoints.size() - 1; i += 2) {
                Endpoint first = transitEndpoints.get(i);
                Endpoint second = transitEndpoints.get(i + 1);
                pathSegments.add(PathSegment.builder()
                        .pathId(pathId)
                        .srcSwitch(first.sw)
                        .srcPort(first.port)
                        .destSwitch(second.sw)
                        .destPort(second.port)
                        .bandwidth(flow.getBandwidth())
                        .ignoreBandwidth(flow.isIgnoreBandwidth())
                        .build());
            }
        }
        return FlowPath.builder()
                .pathId(pathId)
                .srcSwitch(srcSwitch)
                .destSwitch(destSwitch)
                .cookie(cookie)
                .meterId(meterId)
                .bandwidth(flow.getBandwidth())
                .ignoreBandwidth(flow.isIgnoreBandwidth())
                .segments(pathSegments)
                .build();
    }

    /**
     * Build a UnidirectionalFlow with set properties.
     */
    public FlowPathsWithEncapsulation buildFlowPathsWithEncapsulation() {
        Flow flow = build();
        FlowPathsWithEncapsulationBuilder encapsulationBuilder = FlowPathsWithEncapsulation.builder()
                .flow(flow)
                .forwardPath(flow.getForwardPath())
                .forwardEncapsulation(
                        buildEncapsulationResources(flow.getForwardPathId(), forwardTransitEncapsulationId))
                .reversePath(flow.getReversePath())
                .reverseEncapsulation(
                        buildEncapsulationResources(flow.getReversePathId(), reverseTransitEncapsulationId));
        if (flow.getProtectedForwardPathId() != null) {
            encapsulationBuilder.protectedForwardEncapsulation(
                    buildEncapsulationResources(flow.getProtectedForwardPathId(),
                            protectedForwardTransitEncapsulationId));
        }
        if (flow.getProtectedReversePathId() != null) {
            encapsulationBuilder.protectedReverseEncapsulation(
                    buildEncapsulationResources(flow.getProtectedReversePathId(),
                            protectedReverseTransitEncapsulationId));
        }
        return encapsulationBuilder.build();
    }

    private EncapsulationResources buildEncapsulationResources(PathId pathId, int encapsulationId) {
        if (FlowEncapsulationType.TRANSIT_VLAN.equals(encapsulationType)) {
            TransitVlan transitVlan = TransitVlan.builder()
                    .flowId(flowId)
                    .pathId(pathId)
                    .vlan(encapsulationId > 0 ? encapsulationId : srcVlan + destVlan + 1)
                    .build();
            return TransitVlanEncapsulation.builder().transitVlan(transitVlan).build();
        } else if (FlowEncapsulationType.VXLAN.equals(encapsulationType)) {
            Vxlan vxlan = Vxlan.builder()
                    .flowId(flowId)
                    .pathId(pathId)
                    .vni(encapsulationId > 0 ? encapsulationId : srcVlan + destVlan + 1)
                    .build();
            return VxlanEncapsulation.builder().vxlan(vxlan).build();
        }
        throw new IllegalStateException("Unsupported encapsulation type: " + encapsulationType);
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private class Endpoint {
        Switch sw;
        int port;
    }
}
