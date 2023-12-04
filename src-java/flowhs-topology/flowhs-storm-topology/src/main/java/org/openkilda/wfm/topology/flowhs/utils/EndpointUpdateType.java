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

package org.openkilda.wfm.topology.flowhs.utils;

import org.openkilda.messaging.command.haflow.HaFlowRequest;
import org.openkilda.messaging.command.haflow.HaSubFlowDto;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;

import org.apache.storm.shade.com.google.common.base.Objects;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum EndpointUpdateType {
    NONE,
    SOURCE,
    DESTINATION,
    SHARED,
    SUBFLOWS,
    ALL;

    public boolean isPartialUpdate() {
        return !this.equals(NONE);
    }

    /**
     * Determines whether an update to the target HA-flow requires only endpoints update or a full update.
     * @param originalHaFlow an original HA-flow which is to be updated
     * @param targetHaFlow a target HA-flow from the update request
     * @return a type of the update required
     */
    public static EndpointUpdateType determineUpdateType(HaFlow originalHaFlow, HaFlowRequest targetHaFlow) {
        if (originalHaFlow == null || targetHaFlow == null) {
            throw new IllegalArgumentException("Original and target flow object cannot be null");
        }

        boolean updateEndpointOnly = originalHaFlow.getEndpointSwitchIds().equals(getEndpointSwitchIds(targetHaFlow))
                && Objects.equal(originalHaFlow.isAllocateProtectedPath(), targetHaFlow.isAllocateProtectedPath())
                && originalHaFlow.getMaximumBandwidth() == targetHaFlow.getMaximumBandwidth()
                && originalHaFlow.isIgnoreBandwidth() == targetHaFlow.isIgnoreBandwidth()
                && originalHaFlow.isStrictBandwidth() == targetHaFlow.isStrictBandwidth()
                && Objects.equal(originalHaFlow.getMaxLatency(), targetHaFlow.getMaxLatency())
                && Objects.equal(originalHaFlow.getMaxLatencyTier2(), targetHaFlow.getMaxLatencyTier2())
                && Objects.equal(originalHaFlow.getEncapsulationType(), targetHaFlow.getEncapsulationType())
                && Objects.equal(originalHaFlow.getPathComputationStrategy(), targetHaFlow.getPathComputationStrategy())
                && Objects.equal(originalHaFlow.getDiverseGroupId(), targetHaFlow.getDiverseFlowId());

        boolean sharedEndpointChanged =
                !originalHaFlow.getSharedEndpoint().isSwitchPortVlanEquals(targetHaFlow.getSharedEndpoint());

        boolean subflowEndpointChanged = isSubflowEndpointChanged(
                originalHaFlow.getHaSubFlows(), targetHaFlow.getSubFlows());

        if (updateEndpointOnly && sharedEndpointChanged && subflowEndpointChanged) {
            return ALL;
        } else if (updateEndpointOnly && sharedEndpointChanged) {
            return SHARED;
        } else if (updateEndpointOnly && subflowEndpointChanged) {
            return SUBFLOWS;
        } else {
            return NONE;
        }
    }

    /**
     * Determines whether an update to the target flow requires only endpoints update or a full update.
     * @param originalFlow an original flow to be updated
     * @param targetFlow a target from the update request
     * @param originalDiverseGroupId a diverse group ID for the original flow
     * @param targetDiverseGroupId a diverse group ID for the target flow
     * @param originalAffinityGroupId an affinity group ID for the original flow
     * @param targetAffinityGroupId an affinity group ID for the target flow
     * @return a type of the update required
     */
    public static EndpointUpdateType determineUpdateType(RequestedFlow originalFlow, RequestedFlow targetFlow,
                                                         String originalDiverseGroupId,
                                                         String targetDiverseGroupId,
                                                         String originalAffinityGroupId,
                                                         String targetAffinityGroupId) {
        boolean updateEndpointOnly = originalFlow.getSrcSwitch().equals(targetFlow.getSrcSwitch());
        updateEndpointOnly &= originalFlow.getDestSwitch().equals(targetFlow.getDestSwitch());

        updateEndpointOnly &= originalFlow.isAllocateProtectedPath() == targetFlow.isAllocateProtectedPath();
        updateEndpointOnly &= originalFlow.getBandwidth() == targetFlow.getBandwidth();
        updateEndpointOnly &= originalFlow.isIgnoreBandwidth() == targetFlow.isIgnoreBandwidth();
        updateEndpointOnly &= originalFlow.isStrictBandwidth() == targetFlow.isStrictBandwidth();

        updateEndpointOnly &= Objects.equal(originalFlow.getMaxLatency(), targetFlow.getMaxLatency());
        updateEndpointOnly &= Objects.equal(originalFlow.getMaxLatencyTier2(), targetFlow.getMaxLatencyTier2());
        updateEndpointOnly &= Objects.equal(originalFlow.getFlowEncapsulationType(),
                targetFlow.getFlowEncapsulationType());
        updateEndpointOnly &= Objects.equal(originalFlow.getPathComputationStrategy(),
                targetFlow.getPathComputationStrategy());

        updateEndpointOnly &= Objects.equal(originalDiverseGroupId, targetDiverseGroupId);
        updateEndpointOnly &= Objects.equal(originalAffinityGroupId, targetAffinityGroupId);

        // TODO(tdurakov): check connected devices as well
        boolean srcEndpointChanged = originalFlow.getSrcPort() != targetFlow.getSrcPort();
        srcEndpointChanged |= originalFlow.getSrcVlan() != targetFlow.getSrcVlan();
        srcEndpointChanged |= originalFlow.getSrcInnerVlan() != targetFlow.getSrcInnerVlan();

        // TODO(tdurakov): check connected devices as well
        boolean dstEndpointChanged = originalFlow.getDestPort() != targetFlow.getDestPort();
        dstEndpointChanged |= originalFlow.getDestVlan() != targetFlow.getDestVlan();
        dstEndpointChanged |= originalFlow.getDestInnerVlan() != targetFlow.getDestInnerVlan();

        if (originalFlow.getLoopSwitchId() != targetFlow.getLoopSwitchId()) {
            srcEndpointChanged |= originalFlow.getSrcSwitch().equals(originalFlow.getLoopSwitchId());
            srcEndpointChanged |= originalFlow.getSrcSwitch().equals(targetFlow.getLoopSwitchId());
            dstEndpointChanged |= originalFlow.getDestSwitch().equals(originalFlow.getLoopSwitchId());
            dstEndpointChanged |= originalFlow.getDestSwitch().equals(targetFlow.getLoopSwitchId());
        }

        if (updateEndpointOnly) {
            if (srcEndpointChanged && dstEndpointChanged) {
                return EndpointUpdateType.ALL;
            } else if (srcEndpointChanged) {
                return EndpointUpdateType.SOURCE;
            } else if (dstEndpointChanged) {
                return EndpointUpdateType.DESTINATION;
            } else {
                return EndpointUpdateType.NONE;
            }
        }
        return EndpointUpdateType.NONE;
    }

    /**
     * Compares the corresponding subflows endpoints using their IDs.
     * @param original HA subflows from the original HA-flow
     * @param target HA subflows from the target HA-flow
     * @return true if one of the endpoints has changed port or VLAN IDs.
     */
    private static boolean isSubflowEndpointChanged(Collection<HaSubFlow> original, Collection<HaSubFlowDto> target) {
        Map<String, FlowEndpoint> originalEndpoints = original.stream().collect(
                Collectors.toMap(HaSubFlow::getHaSubFlowId, HaSubFlow::getEndpoint,
                        EndpointUpdateType::throwOnDuplicate));

        Map<String, FlowEndpoint> targetEndpoints = target.stream().collect(
                Collectors.toMap(HaSubFlowDto::getFlowId, HaSubFlowDto::getEndpoint,
                        EndpointUpdateType::throwOnDuplicate));

        return originalEndpoints.entrySet().stream()
                .map(e -> e.getValue().isSwitchPortVlanEquals(targetEndpoints.get(e.getKey())))
                .anyMatch(b -> b == Boolean.FALSE);
    }

    private static <T> T throwOnDuplicate(T left, T right) {
        throw new IllegalArgumentException("HA-flow has HA sub flows with the duplicated ID. "
                + "Could not distinguish endpoints");
    }

    private static Set<SwitchId> getEndpointSwitchIds(HaFlowRequest haFlowRequest) {
        return Stream.concat(Stream.of(haFlowRequest.getSharedEndpoint()),
                        haFlowRequest.getSubFlows().stream().map(HaSubFlowDto::getEndpoint))
                .map(FlowEndpoint::getSwitchId).collect(Collectors.toSet());
    }
}
