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
import org.openkilda.wfm.share.model.SpeakerRequestBuildContext;
import org.openkilda.wfm.share.model.SpeakerRequestBuildContext.PathContext;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowProcessingWithHistorySupportFsm;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;
import org.openkilda.wfm.topology.flowhs.service.FlowCommandBuilderFactory;

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
            FlowEndpoint oldEndpoint, FlowEndpoint newEndpoint, boolean server42Rtt) {
        return server42Rtt && !oldEndpoint.isSwitchPortEquals(newEndpoint)
                && findFlowIdsForMultiSwitchFlowsByEndpointWithMultiTableSupport(
                oldEndpoint.getSwitchId(), oldEndpoint.getPortNumber()).isEmpty();
    }

    protected boolean removeSharedLldpRule(String flowId, FlowEndpoint oldEndpoint, FlowEndpoint newEndpoint) {
        boolean lldpWasSwitchedOff = oldEndpoint.isTrackLldpConnectedDevices()
                && !newEndpoint.isTrackLldpConnectedDevices();
        boolean lldpPortWasChanged = !oldEndpoint.isSwitchPortEquals(newEndpoint)
                && oldEndpoint.isTrackLldpConnectedDevices();

        return (lldpWasSwitchedOff || lldpPortWasChanged) && isFlowTheLastUserOfSharedLldpPortRule(
                flowId, oldEndpoint.getSwitchId(), oldEndpoint.getPortNumber());
    }

    protected boolean removeSharedArpRule(String flowId, FlowEndpoint oldEndpoint, FlowEndpoint newEndpoint) {
        boolean arpWasSwitchedOff = oldEndpoint.isTrackArpConnectedDevices()
                && !newEndpoint.isTrackArpConnectedDevices();
        boolean arpPortWasChanged = !oldEndpoint.isSwitchPortEquals(newEndpoint)
                && oldEndpoint.isTrackArpConnectedDevices();

        return (arpWasSwitchedOff || arpPortWasChanged) && isFlowTheLastUserOfSharedArpPortRule(
                flowId, oldEndpoint.getSwitchId(), oldEndpoint.getPortNumber());
    }

    protected boolean removeOuterVlanMatchSharedRule(String flowId, FlowEndpoint current, FlowEndpoint goal) {
        if (current.isSwitchPortEquals(goal)
                && current.getOuterVlanId() == goal.getOuterVlanId()) {
            return false;
        }
        return findOuterVlanMatchSharedRuleUsage(current).stream()
                .allMatch(entry -> flowId.equals(entry.getFlowId()));
    }

    protected boolean removeServer42OuterVlanMatchSharedRule(
            RequestedFlow currentFlow, FlowEndpoint current, FlowEndpoint goal) {
        if (current.getSwitchId().equals(goal.getSwitchId()) && current.getOuterVlanId() == goal.getOuterVlanId()
                || currentFlow.isOneSwitchFlow()) {
            return false;
        }
        return findServer42OuterVlanMatchSharedRuleUsage(current).stream()
                .allMatch(currentFlow.getFlowId()::equals);
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

        PathContext forwardPathContext = PathContext.builder()
                .removeCustomerPortRule(removeForwardCustomerPortSharedCatchRule(oldFlow, newFlow))
                .removeCustomerPortLldpRule(removeSharedLldpRule(oldFlow.getFlowId(), oldIngress, newIngress))
                .removeCustomerPortArpRule(removeSharedArpRule(oldFlow.getFlowId(), oldIngress, newIngress))
                .removeOuterVlanMatchSharedRule(
                        removeOuterVlanMatchSharedRule(oldFlow.getFlowId(), oldIngress, newIngress))
                .removeServer42InputRule(removeSharedServer42InputRule(
                        oldIngress, newIngress, srcServer42FlowRtt))
                .removeServer42IngressRule(srcServer42FlowRtt)
                .updateMeter(removeMeters)
                .removeServer42OuterVlanMatchSharedRule(srcServer42FlowRtt
                        && removeServer42OuterVlanMatchSharedRule(oldFlow, oldIngress, newIngress))
                .server42Port(oldSrcSwitchProperties.getServer42Port())
                .server42MacAddress(oldSrcSwitchProperties.getServer42MacAddress())
                .build();

        boolean dstServer42FlowRtt = oldDstSwitchProperties.isServer42FlowRtt() && server42FlowRttToggle;

        PathContext reversePathContext = PathContext.builder()
                .removeCustomerPortRule(removeReverseCustomerPortSharedCatchRule(oldFlow, newFlow))
                .removeCustomerPortLldpRule(removeSharedLldpRule(oldFlow.getFlowId(), oldEgress, newEgress))
                .removeCustomerPortArpRule(removeSharedArpRule(oldFlow.getFlowId(), oldEgress, newEgress))
                .removeOuterVlanMatchSharedRule(
                        removeOuterVlanMatchSharedRule(oldFlow.getFlowId(), oldEgress, newEgress))
                .removeServer42InputRule(removeSharedServer42InputRule(
                        oldEgress, newEgress, dstServer42FlowRtt))
                .removeServer42IngressRule(dstServer42FlowRtt)
                .updateMeter(removeMeters)
                .removeServer42OuterVlanMatchSharedRule(dstServer42FlowRtt
                        && removeServer42OuterVlanMatchSharedRule(oldFlow, oldEgress, newEgress))
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
