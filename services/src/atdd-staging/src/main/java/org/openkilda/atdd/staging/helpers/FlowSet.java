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

package org.openkilda.atdd.staging.helpers;

import static java.lang.String.format;
import static java.util.Collections.unmodifiableSet;

import org.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowState;
import org.openkilda.testing.model.topology.TopologyDefinition.OutPort;
import org.openkilda.testing.model.topology.TopologyDefinition.Switch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A builder for flows ({@link FlowPayload}) in nonoverlapped VLANs.
 */
public class FlowSet {

    private Set<FlowPayload> flows = new HashSet<>();
    private List<Integer> allocatedVlans = new ArrayList<>();

    public Set<FlowPayload> getFlows() {
        return unmodifiableSet(flows);
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
            List<Integer> srcVlans = new ArrayList<>();
            srcSwitch.getOutPorts().forEach(port -> srcVlans.addAll(port.getVlanRange()));
            List<Integer> dstVlans = new ArrayList<>();
            destSwitch.getOutPorts().forEach(port -> dstVlans.addAll(port.getVlanRange()));
            // Exclude already allocated vlans
            srcVlans.removeAll(allocatedVlans);
            dstVlans.removeAll(allocatedVlans);

            int srcVlan = chooseSrcVlan(srcVlans, dstVlans);
            int destVlan = chooseDestVlan(srcVlans, dstVlans);

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
            allocatedVlans.add(srcVlan);
            allocatedVlans.add(destVlan);

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
            List<Integer> srcVlans = new ArrayList<>(srcSwitch.getOutPorts().stream()
                    .filter(port -> port.getPort() == srcPort)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            format("Unable to define a flow for %d port on %s switch.", srcPort, srcSwitch))
                    ).getVlanRange());

            List<Integer> dstVlans = new ArrayList<>(destSwitch.getOutPorts().stream()
                    .filter(port -> port.getPort() == destPort)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            format("Unable to define a flow for %d port on %s switch.", destPort, destSwitch))
                    ).getVlanRange());
            // Exclude already allocated vlans
            srcVlans.removeAll(allocatedVlans);
            dstVlans.removeAll(allocatedVlans);

            int srcVlan = chooseSrcVlan(srcVlans, dstVlans);
            int destVlan = chooseDestVlan(srcVlans, dstVlans);

            // Record used vlan to archive uniqueness
            allocatedVlans.add(srcVlan);
            allocatedVlans.add(destVlan);

            return buildFlowPayload(srcPort, srcVlan, destPort, destVlan);
        }

        private int chooseSrcVlan(List<Integer> srcVlans, List<Integer> dstVlans) {
            if (srcVlans.isEmpty() || dstVlans.isEmpty()) {
                throw new IllegalStateException(
                        format("Unable to define a flow between %s and %s as no vlan available.", srcSwitch,
                                destSwitch));
            }

            // Calculate intersection of the ranges
            List<Integer> intersection = srcVlans.stream().filter(dstVlans::contains)
                    .collect(Collectors.toList());

            if (intersection.size() > 0) {
                return intersection.get(0);
            } else {
                // Cross vlan flow
                if (srcVlans.size() < 1) {
                    throw new IllegalStateException(
                            format("Unable to allocate a vlan for the switch %s.", srcSwitch));

                }
                return srcVlans.get(0);
            }
        }

        private int chooseDestVlan(List<Integer> srcVlans, List<Integer> dstVlans) {
            if (srcVlans.isEmpty() || dstVlans.isEmpty()) {
                throw new IllegalStateException(
                        format("Unable to define a flow between %s and %s as no vlan available.", srcSwitch,
                                destSwitch));
            }

            // Calculate intersection of the ranges
            List<Integer> intersection = srcVlans.stream().filter(dstVlans::contains)
                    .collect(Collectors.toList());

            if (intersection.size() > 0) {
                return intersection.get(0);
            } else {
                // Cross vlan flow
                if (dstVlans.size() < 1) {
                    throw new IllegalStateException(
                            format("Unable to allocate a vlan for the switch %s.", destSwitch));
                }
                return dstVlans.get(0);
            }
        }

        private FlowPayload buildFlowPayload(int srcPort, int srcVlan, int destPort, int destVlan) {
            FlowEndpointPayload srcEndpoint = new FlowEndpointPayload(srcSwitch.getDpId(), srcPort, srcVlan);
            FlowEndpointPayload destEndpoint = new FlowEndpointPayload(destSwitch.getDpId(), destPort, destVlan);

            return new FlowPayload(flowId, srcEndpoint, destEndpoint,
                    1, false, flowId, null, FlowState.UP.getState());
        }
    }
}

