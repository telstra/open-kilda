/* Copyright 2020 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowProcessingWithHistorySupportFsm;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;
import org.openkilda.wfm.topology.flowhs.service.speaker.FlowCommandBuilderFactory;
import org.openkilda.wfm.topology.flowhs.service.speaker.SpeakerRequestBuildContext;
import org.openkilda.wfm.topology.flowhs.service.speaker.SpeakerRequestBuildContext.PathContext;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A base for action classes that remove or revert flow rules.
 */
@Slf4j
public abstract class BaseFlowRuleRemovalAction<T extends FlowProcessingWithHistorySupportFsm<T, S, E, C, ?, ?>, S, E,
        C> extends FlowProcessingWithHistorySupportAction<T, S, E, C> {
    protected final FlowCommandBuilderFactory commandBuilderFactory;

    protected BaseFlowRuleRemovalAction(PersistenceManager persistenceManager, FlowResourcesManager resourcesManager) {
        super(persistenceManager);
        this.commandBuilderFactory = new FlowCommandBuilderFactory(resourcesManager);
    }

    protected boolean isRemoveCustomerPortSharedCatchRule(String flowId, SwitchId ingressSwitchId, int ingressPort) {
        Set<String> flowIds = findFlowsIdsByEndpointWithMultiTable(ingressSwitchId, ingressPort);
        return flowIds.size() == 1 && flowIds.iterator().next().equals(flowId);
    }

    protected boolean isFlowTheLastUserOfServer42InputSharedRule(String flowId, SwitchId switchId, int port) {
        Set<String> flowIds = findFlowIdsForMultiSwitchFlowsByEndpointWithMultiTableSupport(switchId, port);
        return flowIds.size() == 1 && flowIds.iterator().next().equals(flowId);
    }

    protected boolean isFlowTheLastUserOfSharedLldpPortRule(
            String flowId, SwitchId ingressSwitchId, int ingressPort) {
        List<Flow> flows = getFlowsOnSwitchEndpointExcludeCurrentFlow(flowId, ingressSwitchId, ingressPort);

        if (getSwitchProperties(ingressSwitchId).isSwitchLldp()) {
            return flows.isEmpty();
        }

        List<Flow> flowsWithLldp = flows.stream()
                .filter(f -> f.getSrcPort() == ingressPort && f.getDetectConnectedDevices().isSrcLldp()
                        || f.getDestPort() == ingressPort && f.getDetectConnectedDevices().isDstLldp())
                .collect(Collectors.toList());

        return flowsWithLldp.isEmpty();
    }

    protected boolean isFlowTheLastUserOfSharedArpPortRule(
            String flowId, SwitchId ingressSwitchId, int ingressPort) {
        List<Flow> flows = getFlowsOnSwitchEndpointExcludeCurrentFlow(flowId, ingressSwitchId, ingressPort);

        if (getSwitchProperties(ingressSwitchId).isSwitchArp()) {
            return flows.isEmpty();
        }

        return flows.stream()
                .noneMatch(f -> f.getSrcPort() == ingressPort && f.getDetectConnectedDevices().isSrcArp()
                        || f.getDestPort() == ingressPort && f.getDetectConnectedDevices().isDstArp());
    }

    private List<Flow> getFlowsOnSwitchEndpointExcludeCurrentFlow(
            String flowId, SwitchId ingressSwitchId, int ingressPort) {
        return flowRepository.findByEndpoint(ingressSwitchId, ingressPort).stream()
                .filter(f -> !f.getFlowId().equals(flowId))
                .collect(Collectors.toList());
    }

    protected boolean removeForwardCustomerPortSharedCatchRule(RequestedFlow oldFlow, RequestedFlow newFlow) {
        boolean srcPortChanged = oldFlow.getSrcPort() != newFlow.getSrcPort();
        boolean srcSwitchChanged = !oldFlow.getSrcSwitch().equals(newFlow.getSrcSwitch());

        return (srcPortChanged || srcSwitchChanged)
                && findFlowsIdsByEndpointWithMultiTable(oldFlow.getSrcSwitch(), oldFlow.getSrcPort()).isEmpty();
    }

    protected boolean removeReverseCustomerPortSharedCatchRule(RequestedFlow oldFlow, RequestedFlow newFlow) {
        boolean dstPortChanged = oldFlow.getDestPort() != newFlow.getDestPort();
        boolean dstSwitchChanged = !oldFlow.getDestSwitch().equals(newFlow.getDestSwitch());

        return (dstPortChanged || dstSwitchChanged)
                && findFlowsIdsByEndpointWithMultiTable(oldFlow.getDestSwitch(), oldFlow.getDestPort()).isEmpty();
    }

    protected boolean removeSharedServer42InputRule(
            FlowEndpoint oldEndpoint, FlowEndpoint newEndpoint,  FlowEndpoint oppositeNewEndpoint,
            boolean server42Rtt, boolean becameSingleSwitch) {
        if (!server42Rtt) {
            return false; // server42 is off so nothing to delete.
        }
        if (oldEndpoint.isSwitchPortEquals(newEndpoint) && !becameSingleSwitch) {
            return false; // new endpoint still need server42 input rule
        }

        if (oldEndpoint.isSwitchPortEquals(oppositeNewEndpoint) && !becameSingleSwitch) {
            return false; // opposite new endpoint will use existing server42 input rule
        }

        // Current flow doesn't use server42 input rule anymore, but maybe some other flow still need this rule.
        return findFlowIdsForMultiSwitchFlowsByEndpointWithMultiTableSupport(
                oldEndpoint.getSwitchId(), oldEndpoint.getPortNumber()).isEmpty();
    }

    protected boolean removeSharedLldpRule(
            String flowId, FlowEndpoint oldEndpoint, FlowEndpoint newEndpoint, FlowEndpoint oppositeNewEndpoint) {
        if (!oldEndpoint.isTrackLldpConnectedDevices()) {
            return false; // LLDP is off on endpoint so nothing to delete.
        }
        if (oldEndpoint.isSwitchPortEquals(newEndpoint) && newEndpoint.isTrackLldpConnectedDevices()) {
            return false; // LLDP still on and endpoint still same. We need LLDP rule.
        }
        if (oldEndpoint.isSwitchPortEquals(oppositeNewEndpoint) && oppositeNewEndpoint.isTrackLldpConnectedDevices()) {
            return false; // Opposite endpoint will use LLDP rule of oldEndpoint.
        }

        // Current flow doesn't use shared LLDP rule anymore, but maybe some other flow still need this rule.
        return isFlowTheLastUserOfSharedLldpPortRule(flowId, oldEndpoint.getSwitchId(), oldEndpoint.getPortNumber());
    }

    protected boolean removeSharedArpRule(
            String flowId, FlowEndpoint oldEndpoint, FlowEndpoint newEndpoint, FlowEndpoint oppositeNewEndpoint) {
        if (!oldEndpoint.isTrackArpConnectedDevices()) {
            return false; // ARP is off on endpoint so nothing to delete.
        }
        if (oldEndpoint.isSwitchPortEquals(newEndpoint) && newEndpoint.isTrackArpConnectedDevices()) {
            return false; // ARP still on and endpoint still same. We need LLDP rule.
        }
        if (oldEndpoint.isSwitchPortEquals(oppositeNewEndpoint) && oppositeNewEndpoint.isTrackArpConnectedDevices()) {
            return false; // Opposite endpoint will use ARP rule of oldEndpoint.
        }

        // Current flow doesn't use shared ARP rule anymore, but maybe some other flow still need this rule.
        return isFlowTheLastUserOfSharedArpPortRule(flowId, oldEndpoint.getSwitchId(), oldEndpoint.getPortNumber());
    }

    protected boolean removeOuterVlanMatchSharedRule(
            String flowId, FlowEndpoint oldEndpoint, FlowEndpoint newEndpoint, FlowEndpoint oppositeNewEndpoint) {
        if (oldEndpoint.isSwitchPortOuterVlanEquals(newEndpoint)) {
            return false; // new endpoint still need shared rule
        }

        if (oldEndpoint.isSwitchPortOuterVlanEquals(oppositeNewEndpoint)) {
            return false; // opposite new endpoint will use existing shared rule
        }

        // Current flow doesn't use shared rule anymore, but maybe some other flow still need this rule.
        return findOuterVlanMatchSharedRuleUsage(oldEndpoint).stream()
                .allMatch(entry -> flowId.equals(entry.getFlowId()));
    }

    protected boolean removeServer42OuterVlanMatchSharedRule(
            RequestedFlow oldFlow, FlowEndpoint oldEndpoint, FlowEndpoint newEndpoint, FlowEndpoint oppositeNewEndpoint,
            boolean server42FlowRtt, boolean flowBecameSingleSwitch) {
        if (oldFlow.isOneSwitchFlow() || !server42FlowRtt) {
            return false; // flow endpoint has no server42 rules so nothing to delete.
        }

        if (oldEndpoint.getSwitchId().equals(newEndpoint.getSwitchId())
                && oldEndpoint.getOuterVlanId() == newEndpoint.getOuterVlanId() && !flowBecameSingleSwitch) {
            return false; // new endpoint still need shared server42 rule
        }

        if (oldEndpoint.getSwitchId().equals(oppositeNewEndpoint.getSwitchId())
                && oldEndpoint.getOuterVlanId() == oppositeNewEndpoint.getOuterVlanId() && !flowBecameSingleSwitch) {
            return false; // opposite new endpoint will use existing shared server42 rule
        }

        // Current flow doesn't use shared server42 rule anymore, but maybe some other flow still need this rule.
        return findServer42OuterVlanMatchSharedRuleUsage(oldEndpoint).stream()
                .allMatch(oldFlow.getFlowId()::equals);
    }

    protected SpeakerRequestBuildContext buildSpeakerContextForRemovalIngressAndShared(
            RequestedFlow oldFlow, RequestedFlow newFlow, boolean removeMeters) {
        SwitchProperties oldSrcSwitchProperties = getSwitchProperties(oldFlow.getSrcSwitch());
        SwitchProperties oldDstSwitchProperties = getSwitchProperties(oldFlow.getDestSwitch());
        SwitchProperties newSrcSwitchProperties = getSwitchProperties(newFlow.getSrcSwitch());
        SwitchProperties newDstSwitchProperties = getSwitchProperties(newFlow.getDestSwitch());
        boolean server42FlowRttToggle = isServer42FlowRttFeatureToggle();
        boolean srcServer42FlowRtt = oldSrcSwitchProperties.isServer42FlowRtt() && server42FlowRttToggle;

        FlowEndpoint oldIngress = makeIngressEndpoint(oldFlow, oldSrcSwitchProperties);
        FlowEndpoint oldEgress = makeEgressEndpoint(oldFlow, oldDstSwitchProperties);

        FlowEndpoint newIngress = makeIngressEndpoint(newFlow, newSrcSwitchProperties);
        FlowEndpoint newEgress = makeEgressEndpoint(newFlow, newDstSwitchProperties);
        boolean becameSingleSwitch = !oldFlow.isOneSwitchFlow() && newFlow.isOneSwitchFlow();

        PathContext forwardPathContext = PathContext.builder()
                .removeCustomerPortRule(removeForwardCustomerPortSharedCatchRule(oldFlow, newFlow))
                .removeCustomerPortLldpRule(removeSharedLldpRule(
                        oldFlow.getFlowId(), oldIngress, newIngress, newEgress))
                .removeCustomerPortArpRule(removeSharedArpRule(oldFlow.getFlowId(), oldIngress, newIngress, newEgress))
                .removeOuterVlanMatchSharedRule(
                        removeOuterVlanMatchSharedRule(oldFlow.getFlowId(), oldIngress, newIngress, newEgress))
                .removeServer42InputRule(removeSharedServer42InputRule(
                        oldIngress, newIngress, newEgress, srcServer42FlowRtt, becameSingleSwitch))
                .removeServer42IngressRule(srcServer42FlowRtt)
                .updateMeter(removeMeters)
                .removeServer42OuterVlanMatchSharedRule(removeServer42OuterVlanMatchSharedRule(
                        oldFlow, oldIngress, newIngress, newEgress, srcServer42FlowRtt, becameSingleSwitch))
                .server42Port(oldSrcSwitchProperties.getServer42Port())
                .server42MacAddress(oldSrcSwitchProperties.getServer42MacAddress())
                .build();

        boolean dstServer42FlowRtt = oldDstSwitchProperties.isServer42FlowRtt() && server42FlowRttToggle;

        PathContext reversePathContext = PathContext.builder()
                .removeCustomerPortRule(removeReverseCustomerPortSharedCatchRule(oldFlow, newFlow))
                .removeCustomerPortLldpRule(removeSharedLldpRule(oldFlow.getFlowId(), oldEgress, newEgress, newIngress))
                .removeCustomerPortArpRule(removeSharedArpRule(oldFlow.getFlowId(), oldEgress, newEgress, newIngress))
                .removeOuterVlanMatchSharedRule(
                        removeOuterVlanMatchSharedRule(oldFlow.getFlowId(), oldEgress, newEgress, newIngress))
                .removeServer42InputRule(removeSharedServer42InputRule(
                        oldEgress, newEgress, newIngress, dstServer42FlowRtt, becameSingleSwitch))
                .removeServer42IngressRule(dstServer42FlowRtt)
                .updateMeter(removeMeters)
                .removeServer42OuterVlanMatchSharedRule(removeServer42OuterVlanMatchSharedRule(
                        oldFlow, oldEgress, newEgress, newIngress, dstServer42FlowRtt, becameSingleSwitch))
                .server42Port(oldDstSwitchProperties.getServer42Port())
                .server42MacAddress(oldDstSwitchProperties.getServer42MacAddress())
                .build();

        return SpeakerRequestBuildContext.builder()
                .forward(forwardPathContext)
                .reverse(reversePathContext)
                .build();
    }

    private FlowEndpoint makeIngressEndpoint(RequestedFlow endpoint, SwitchProperties oldSrcSwitchProperties) {
        return new FlowEndpoint(
                endpoint.getSrcSwitch(), endpoint.getSrcPort(), endpoint.getSrcVlan(), endpoint.getSrcInnerVlan(),
                endpoint.getDetectConnectedDevices().isSrcLldp() || oldSrcSwitchProperties.isSwitchLldp(),
                endpoint.getDetectConnectedDevices().isSrcArp() || oldSrcSwitchProperties.isSwitchArp());
    }

    private FlowEndpoint makeEgressEndpoint(RequestedFlow oldFlow, SwitchProperties oldDstSwitchProperties) {
        return new FlowEndpoint(
                oldFlow.getDestSwitch(), oldFlow.getDestPort(), oldFlow.getDestVlan(), oldFlow.getDestInnerVlan(),
                oldFlow.getDetectConnectedDevices().isDstLldp() || oldDstSwitchProperties.isSwitchLldp(),
                oldFlow.getDetectConnectedDevices().isDstArp() || oldDstSwitchProperties.isSwitchArp());
    }

    protected SpeakerRequestBuildContext buildSpeakerContextForRemovalIngressOnly(
            SwitchId srcSwitchId, SwitchId dstSwitchId) {
        return SpeakerRequestBuildContext.builder()
                .forward(buildPathContextForRemovalIngressOnly(srcSwitchId))
                .reverse(buildPathContextForRemovalIngressOnly(dstSwitchId))
                .build();
    }

    protected PathContext buildPathContextForRemovalIngressOnly(SwitchId switchId) {
        SwitchProperties switchProperties = getSwitchProperties(switchId);
        return PathContext.builder()
                .removeServer42IngressRule(switchProperties.isServer42FlowRtt() && isServer42FlowRttFeatureToggle())
                .server42Port(switchProperties.getServer42Port())
                .server42MacAddress(switchProperties.getServer42MacAddress())
                .build();
    }
}
