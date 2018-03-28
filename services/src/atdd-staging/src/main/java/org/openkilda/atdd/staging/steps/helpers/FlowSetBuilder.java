/* Copyright 2018 Telstra Open Source
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
package org.openkilda.atdd.staging.steps.helpers;

import static java.lang.String.format;
import static java.util.Collections.unmodifiableSet;

import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition.OutPort;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition.Switch;
import org.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * A builder for flows ({@link FlowPayload}) in nonoverlapped VLANs.
 */
public class FlowSetBuilder {

    private Set<FlowPayload> flows = new HashSet<>();
    private RangeSet<Integer> allocatedVlans = TreeRangeSet.create();

    public Set<FlowPayload> getFlows() {
        return unmodifiableSet(flows);
    }

    public void addFlowInUniqueVlan(String flowId, Switch srcSwitch, Switch destSwitch) {
        // Take the switch vlan ranges as the base
        RangeSet<Integer> srcRangeSet = TreeRangeSet.create();
        srcSwitch.getOutPorts().forEach(port -> srcRangeSet.addAll(port.getVlanRange()));
        RangeSet<Integer> destRangeSet = TreeRangeSet.create();
        destSwitch.getOutPorts().forEach(port -> destRangeSet.addAll(port.getVlanRange()));
        // Exclude already allocated vlans
        srcRangeSet.removeAll(allocatedVlans);
        destRangeSet.removeAll(allocatedVlans);

        if (srcRangeSet.isEmpty() || destRangeSet.isEmpty()) {
            throw new IllegalStateException(
                    format("Unable to define a flow between %s and %s as no vlan available.", srcSwitch, destSwitch));
        }

        // Calculate intersection of the ranges
        RangeSet<Integer> interRangeSet = TreeRangeSet.create(srcRangeSet);
        interRangeSet.removeAll(destRangeSet.complement());
        // Same vlan for source and destination
        final Optional<Integer> sameVlan = interRangeSet.asRanges().stream()
                .flatMap(range -> ContiguousSet.create(range, DiscreteDomain.integers()).stream())
                .findFirst();

        int srcVlan;
        int destVlan;
        if (sameVlan.isPresent()) {
            srcVlan = sameVlan.get();
            destVlan = sameVlan.get();
        } else {
            // Cross vlan flow
            Optional<Integer> srcVlanOpt = srcRangeSet.asRanges().stream()
                    .flatMap(range -> ContiguousSet.create(range, DiscreteDomain.integers()).stream())
                    .findFirst();
            if (!srcVlanOpt.isPresent()) {
                throw new IllegalStateException(
                        format("Unable to allocate a vlan for the switch %s.", srcSwitch));

            }
            srcVlan = srcVlanOpt.get();

            Optional<Integer> destVlanOpt = destRangeSet.asRanges().stream()
                    .flatMap(range -> ContiguousSet.create(range, DiscreteDomain.integers()).stream())
                    .findFirst();
            if (!destVlanOpt.isPresent()) {
                throw new IllegalStateException(
                        format("Unable to allocate a vlan for the switch %s.", destSwitch));
            }
            destVlan = destVlanOpt.get();
        }

        boolean sameSwitchFlow = srcSwitch.getDpId().equals(destSwitch.getDpId());

        Optional<OutPort> srcPort = srcSwitch.getOutPorts().stream()
                .filter(p -> p.getVlanRange().contains(srcVlan))
                .findFirst();
        int srcPortId = srcPort
                .orElseThrow(() -> new IllegalStateException("Unable to allocate a port in found vlan."))
                .getPort();

        Optional<OutPort> destPort = destSwitch.getOutPorts().stream()
                .filter(p -> p.getVlanRange().contains(destVlan))
                .filter(p -> !sameSwitchFlow || p.getPort() != srcPortId)
                .findFirst();
        if (!destPort.isPresent()) {
            if (sameSwitchFlow) {
                throw new IllegalStateException(
                        format("Unable to define a same switch flow for %s as no ports available.", srcSwitch));
            } else {
                throw new IllegalStateException("Unable to allocate a port in found vlan.");
            }
        }

        // Record used vlan to archive uniqueness
        allocatedVlans.add(Range.singleton(srcVlan));
        allocatedVlans.add(Range.singleton(destVlan));

        FlowEndpointPayload srcEndpoint = new FlowEndpointPayload(srcSwitch.getDpId(), srcPortId, srcVlan);
        FlowEndpointPayload destEndpoint = new FlowEndpointPayload(destSwitch.getDpId(), destPort.get().getPort(),
                destVlan);

        flows.add(new FlowPayload(flowId, srcEndpoint, destEndpoint,
                1, false, flowId, null));
    }
}
