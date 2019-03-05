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

package org.openkilda.atdd.staging.helpers;

import static java.lang.String.format;
import static java.util.Collections.unmodifiableSet;

import org.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.testing.model.topology.TopologyDefinition.OutPort;
import org.openkilda.testing.model.topology.TopologyDefinition.Switch;

import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * A builder for flows ({@link FlowPayload}) in nonoverlapped VLANs.
 */
public class FlowSet {

    private Set<FlowPayload> flows = new HashSet<>();
    private RangeSet<Integer> allocatedVlans = TreeRangeSet.create();

    public Set<FlowPayload> getFlows() {
        return unmodifiableSet(flows);
    }

    /**
     * Returns an unallocated vlan. The returned vlan is immediately added to the list of allocated vlans
     */
    public int allocateVlan() {
        RangeSet<Integer> availableVlansRange = TreeRangeSet.create();
        availableVlansRange.removeAll(allocatedVlans);
        Integer vlan = availableVlansRange.asRanges().stream()
                .flatMap(range -> ContiguousSet.create(range, DiscreteDomain.integers()).stream())
                .findFirst().get();
        allocatedVlans.add(Range.singleton(vlan));
        return vlan;
    }

    public void addFlow(String flowId, Switch srcSwitch, Switch destSwitch) {
        FlowBuilder flow = new FlowBuilder(flowId, srcSwitch, destSwitch);
        flows.add(flow.buildWithAnyPortsInUniqueVlan());
    }

    public void addFlow(String flowId, Switch srcSwitch, int srcPort, Switch destSwitch, int destPort) {
        FlowBuilder flow = new FlowBuilder(flowId, srcSwitch, destSwitch);
        flows.add(flow.buildInUniqueVlan(srcPort, destPort));
    }

    public FlowBuilder getFlowBuilder(String flowId, Switch srcSwitch, Switch destSwitch) {
        return new FlowBuilder(flowId, srcSwitch, destSwitch);
    }

    /**
     * Creates a FlowPayload instance. Properly handles unique vlans for each new flow
     * in current FlowSetBuilder. Uses available ports specified in config.
     */
    public FlowPayload buildWithAnyPortsInUniqueVlan(String flowId, Switch srcSwitch, Switch destSwitch,
                                                     int bandwidth) {
        FlowBuilder flowBuilder = new FlowBuilder(flowId, srcSwitch, destSwitch);
        FlowPayload flow = flowBuilder.buildWithAnyPortsInUniqueVlan();
        flow.setMaximumBandwidth(bandwidth);
        return flow;
    }

    public class FlowBuilder {

        private String flowId;
        private Switch srcSwitch;
        private Switch destSwitch;

        /**
         * FlowBuilder instance for given parameters.
         */
        public FlowBuilder(String flowId, Switch srcSwitch, Switch destSwitch) {
            this.flowId = flowId;
            this.srcSwitch = srcSwitch;
            this.destSwitch = destSwitch;
        }

        /**
         * Creates a FlowPayload instance. Properly handles unique vlans for each new flow
         * in current FlowSetBuilder. Uses available ports specified in config.
         */
        public FlowPayload buildWithAnyPortsInUniqueVlan() {
            // Take the switch vlan ranges as the base
            RangeSet<Integer> srcRangeSet = TreeRangeSet.create();
            srcSwitch.getOutPorts().forEach(port -> srcRangeSet.addAll(port.getVlanRange()));
            RangeSet<Integer> destRangeSet = TreeRangeSet.create();
            destSwitch.getOutPorts().forEach(port -> destRangeSet.addAll(port.getVlanRange()));
            // Exclude already allocated vlans
            srcRangeSet.removeAll(allocatedVlans);
            destRangeSet.removeAll(allocatedVlans);

            int srcVlan = chooseSrcVlan(srcRangeSet, destRangeSet);
            int destVlan = chooseDestVlan(srcRangeSet, destRangeSet);

            boolean sameSwitchFlow = srcSwitch.getDpId().equals(destSwitch.getDpId());

            Optional<OutPort> srcOutPort = srcSwitch.getOutPorts().stream()
                    .filter(p -> p.getVlanRange().contains(srcVlan))
                    .findFirst();
            int srcPort = srcOutPort
                    .orElseThrow(() -> new IllegalStateException("Unable to allocate a port in found vlan."))
                    .getPort();

            Optional<OutPort> destOutPort = destSwitch.getOutPorts().stream()
                    .filter(p -> p.getVlanRange().contains(destVlan))
                    .filter(p -> !sameSwitchFlow || p.getPort() != srcPort)
                    .findFirst();
            int destPort = destOutPort
                    .orElseThrow(() -> {
                        if (sameSwitchFlow) {
                            return new IllegalStateException(
                                    format("Unable to define a same switch flow for %s as no ports available.",
                                            srcSwitch));
                        } else {
                            return new IllegalStateException("Unable to allocate a port in found vlan.");
                        }
                    })
                    .getPort();

            // Record used vlan to archive uniqueness
            allocatedVlans.add(Range.singleton(srcVlan));
            allocatedVlans.add(Range.singleton(destVlan));

            return buildFlowPayload(srcPort, srcVlan, destPort, destVlan);
        }

        /**
         * Creates a new FlowPayload instance. Properly handles unique vlans for each new flow
         * in current FlowSetBuilder.
         *
         * @param srcPort port number on source switch
         * @param destPort port number on destination switch
         */
        public FlowPayload buildInUniqueVlan(int srcPort, int destPort) {
            RangeSet<Integer> srcRangeSet = TreeRangeSet.create();
            srcRangeSet.addAll(srcSwitch.getOutPorts().stream()
                    .filter(port -> port.getPort() == srcPort)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            format("Unable to define a flow for %d port on %s switch.", srcPort, srcSwitch))
                    ).getVlanRange());

            RangeSet<Integer> destRangeSet = TreeRangeSet.create();
            destRangeSet.addAll(destSwitch.getOutPorts().stream()
                    .filter(port -> port.getPort() == destPort)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            format("Unable to define a flow for %d port on %s switch.", destPort, destSwitch))
                    ).getVlanRange());
            // Exclude already allocated vlans
            srcRangeSet.removeAll(allocatedVlans);
            destRangeSet.removeAll(allocatedVlans);

            int srcVlan = chooseSrcVlan(srcRangeSet, destRangeSet);
            int destVlan = chooseDestVlan(srcRangeSet, destRangeSet);

            // Record used vlan to archive uniqueness
            allocatedVlans.add(Range.singleton(srcVlan));
            allocatedVlans.add(Range.singleton(destVlan));

            return buildFlowPayload(srcPort, srcVlan, destPort, destVlan);
        }

        private int chooseSrcVlan(RangeSet<Integer> srcRangeSet, RangeSet<Integer> destRangeSet) {
            if (srcRangeSet.isEmpty() || destRangeSet.isEmpty()) {
                throw new IllegalStateException(
                        format("Unable to define a flow between %s and %s as no vlan available.", srcSwitch,
                                destSwitch));
            }

            // Calculate intersection of the ranges
            RangeSet<Integer> interRangeSet = TreeRangeSet.create(srcRangeSet);
            interRangeSet.removeAll(destRangeSet.complement());
            // Same vlan for source and destination
            final Optional<Integer> sameVlan = interRangeSet.asRanges().stream()
                    .flatMap(range -> ContiguousSet.create(range, DiscreteDomain.integers()).stream())
                    .findFirst();

            if (sameVlan.isPresent()) {
                return sameVlan.get();
            } else {
                // Cross vlan flow
                Optional<Integer> srcVlanOpt = srcRangeSet.asRanges().stream()
                        .flatMap(range -> ContiguousSet.create(range, DiscreteDomain.integers()).stream())
                        .findFirst();
                if (!srcVlanOpt.isPresent()) {
                    throw new IllegalStateException(
                            format("Unable to allocate a vlan for the switch %s.", srcSwitch));

                }
                return srcVlanOpt.get();
            }
        }

        private int chooseDestVlan(RangeSet<Integer> srcRangeSet, RangeSet<Integer> destRangeSet) {
            if (srcRangeSet.isEmpty() || destRangeSet.isEmpty()) {
                throw new IllegalStateException(
                        format("Unable to define a flow between %s and %s as no vlan available.", srcSwitch,
                                destSwitch));
            }

            // Calculate intersection of the ranges
            RangeSet<Integer> interRangeSet = TreeRangeSet.create(srcRangeSet);
            interRangeSet.removeAll(destRangeSet.complement());
            // Same vlan for source and destination
            final Optional<Integer> sameVlan = interRangeSet.asRanges().stream()
                    .flatMap(range -> ContiguousSet.create(range, DiscreteDomain.integers()).stream())
                    .findFirst();

            if (sameVlan.isPresent()) {
                return sameVlan.get();
            } else {
                // Cross vlan flow
                Optional<Integer> destVlanOpt = destRangeSet.asRanges().stream()
                        .flatMap(range -> ContiguousSet.create(range, DiscreteDomain.integers()).stream())
                        .findFirst();
                if (!destVlanOpt.isPresent()) {
                    throw new IllegalStateException(
                            format("Unable to allocate a vlan for the switch %s.", destSwitch));
                }
                return destVlanOpt.get();
            }
        }

        private FlowPayload buildFlowPayload(int srcPort, int srcVlan, int destPort, int destVlan) {
            FlowEndpointPayload srcEndpoint = new FlowEndpointPayload(srcSwitch.getDpId(), srcPort, srcVlan);
            FlowEndpointPayload destEndpoint = new FlowEndpointPayload(destSwitch.getDpId(), destPort, destVlan);

            return FlowPayload.builder()
                    .id(flowId)
                    .source(srcEndpoint)
                    .destination(destEndpoint)
                    .maximumBandwidth(1)
                    .description(flowId)
                    .status(FlowState.UP.getState())
                    .build();
        }
    }
}

