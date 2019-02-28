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

package org.openkilda.wfm.topology.flowhs.service;

import static java.lang.String.format;

import org.openkilda.floodlight.flow.request.InstallEgressRule;
import org.openkilda.floodlight.flow.request.InstallIngressRule;
import org.openkilda.floodlight.flow.request.InstallTransitRule;
import org.openkilda.floodlight.flow.request.RemoveRule;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.MeterId;
import org.openkilda.model.OutputVlanType;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.model.TransitVlan;
import org.openkilda.persistence.repositories.TransitVlanRepository;

import com.google.common.collect.ImmutableList;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
public class FlowCommandFactory {
    private final TransitVlanRepository transitVlanRepository;

    public FlowCommandFactory(TransitVlanRepository transitVlanRepository) {
        this.transitVlanRepository = transitVlanRepository;
    }

    public List<InstallTransitRule> createInstallNonIngressRules(Flow flow) {
        if (flow.isOneSwitchFlow()) {
            return Collections.emptyList();
        }

        FlowPath forwardPath = flow.getForwardPath();
        FlowPath reversePath = flow.getReversePath();
        List<InstallTransitRule> commands = new ArrayList<>();
        commands.addAll(collectNonIngressRules(forwardPath, flow.getDestPort(), flow.getSrcVlan(), flow.getDestVlan()));
        commands.addAll(collectNonIngressRules(reversePath, flow.getSrcPort(), flow.getDestVlan(), flow.getSrcVlan()));
        return commands;
    }

    public List<InstallIngressRule> createInstallIngressRules(Flow flow) {
        InstallIngressRule forwardIngressRule = buildInstallIngressRule(flow.getForwardPath(), flow.getSrcPort(),
                flow.getSrcVlan(), flow.getDestVlan());
        InstallIngressRule reverseIngressRule = buildInstallIngressRule(flow.getReversePath(), flow.getDestPort(),
                flow.getDestVlan(), flow.getSrcVlan());

        return ImmutableList.of(forwardIngressRule, reverseIngressRule);
    }

    public List<RemoveRule> createRemoveNonIngressRules(Flow flow) {
        if (flow.isOneSwitchFlow()) {
            // Removing of single switch rules is done with no output port in criteria.
            return Collections.emptyList();
        }

        List<RemoveRule> commands = new ArrayList<>();
        commands.addAll(collectRemoveNonIngressRules(flow.getForwardPath(), flow.getDestPort()));
        commands.addAll(collectRemoveNonIngressRules(flow.getReversePath(), flow.getSrcPort()));
        return commands;
    }

    public List<RemoveRule> createRemoveIngressRules(Flow flow) {
        RemoveRule removeForwardIngress =
                buildRemoveIngressRule(flow.getForwardPath(), flow.getSrcPort(), flow.getSrcVlan());
        RemoveRule removeReverseIngress =
                buildRemoveIngressRule(flow.getReversePath(), flow.getDestPort(), flow.getDestVlan());

        return ImmutableList.of(removeForwardIngress, removeReverseIngress);
    }

    private InstallIngressRule buildInstallIngressRule(FlowPath flowPath, int inputPort,
                                                       int inputVlanId, int outputVlanId) {
        TransitVlan transitVlan = transitVlanRepository.findByPathId(flowPath.getPathId())
                .orElseThrow(() ->
                        new IllegalStateException(format("Transit vlan should be present for path %s for flow %s",
                                flowPath.getPathId(), flowPath.getFlowId())));

        PathSegment ingressSegment = flowPath.getSegments().stream()
                .filter(segment -> segment.getSrcSwitch().equals(flowPath.getSrcSwitch()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        format("PathSegment was not found for ingress flow rule, flowId: %s", flowPath.getFlowId())));

        return InstallIngressRule.builder()
                .id(flowPath.getFlowId())
                .switchId(flowPath.getSrcSwitch().getSwitchId())
                .cookie(flowPath.getCookie().getValue())
                .bandwidth(flowPath.getBandwidth())
                .meterId(Optional.ofNullable(flowPath.getMeterId())
                        .map(MeterId::getValue)
                        .orElse(null))
                .inputPort(inputPort)
                .outputPort(ingressSegment.getSrcPort())
                .outputVlanType(getOutputVlanType(inputVlanId, outputVlanId))
                .inputVlanId(inputVlanId)
                .transitVlanId(transitVlan.getVlan())
                .build();
    }

    private List<InstallTransitRule> collectNonIngressRules(FlowPath flowPath, int outputPort, int srcVlan,
                                                            int destVlan) {
        if (flowPath == null || CollectionUtils.isEmpty(flowPath.getSegments())) {
            throw new IllegalArgumentException("Flow path with segments is required");
        }

        List<PathSegment> segments = flowPath.getSegments();
        List<InstallTransitRule> commands = new ArrayList<>(segments.size());

        int transitVlan = getTransitVlan(flowPath.getPathId());
        for (int i = 1; i < segments.size(); i++) {
            PathSegment income = segments.get(i - 1);
            PathSegment outcome = segments.get(i);

            InstallTransitRule transitRule = buildInstallTransitRule(flowPath, income.getDestSwitch().getSwitchId(),
                    income.getDestPort(), outcome.getSrcPort(), transitVlan);
            commands.add(transitRule);
        }

        PathSegment egressSegment = segments.get(segments.size() - 1);
        if (!egressSegment.getDestSwitch().getSwitchId().equals(flowPath.getDestSwitch().getSwitchId())) {
            throw new IllegalStateException(format("PathSegment was not found for egress flow rule pathId: %s",
                    flowPath.getPathId()));
        }

        InstallEgressRule egressRule = buildInstallEgressRule(flowPath, egressSegment.getDestPort(), outputPort,
                srcVlan, transitVlan, destVlan);
        commands.add(egressRule);

        return commands;
    }

    private InstallTransitRule buildInstallTransitRule(FlowPath flowPath, SwitchId switchId, int inputPort,
                                                       int outputPort, int transitVlan) {
        return new InstallTransitRule(null, flowPath.getFlowId(), flowPath.getCookie().getValue(), switchId,
                inputPort, outputPort, transitVlan);
    }

    private InstallEgressRule buildInstallEgressRule(FlowPath flowPath, int inputPort, int outputPort,
                                                     int srcVlan, int transitVlan, int destVlan) {
        return InstallEgressRule.builder()
                .id(flowPath.getFlowId())
                .switchId(flowPath.getDestSwitch().getSwitchId())
                .cookie(flowPath.getCookie().getValue())
                .inputPort(inputPort)
                .transitVlanId(transitVlan)
                .outputPort(outputPort)
                .outputVlanId(destVlan)
                .outputVlanType(getOutputVlanType(srcVlan, destVlan))
                .build();
    }

    private RemoveRule buildRemoveIngressRule(FlowPath flowPath, int inputPort, int inputVlanId) {
        PathSegment ingressSegment = flowPath.getSegments().stream()
                .filter(segment -> segment.getSrcSwitch().equals(flowPath.getSrcSwitch()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        format("PathSegment was not found for ingress flow rule, flowId: %s", flowPath.getFlowId())));

        DeleteRulesCriteria ingressCriteria = new DeleteRulesCriteria(flowPath.getCookie().getValue(), inputPort,
                inputVlanId, 0, ingressSegment.getSrcPort());
        return RemoveRule.builder()
                .flowId(flowPath.getFlowId())
                .switchId(flowPath.getSrcSwitch().getSwitchId())
                .cookie(flowPath.getCookie().getValue())
                .meterId(Optional.ofNullable(flowPath.getMeterId())
                        .map(MeterId::getValue)
                        .orElse(null))
                .criteria(ingressCriteria)
                .build();
    }

    private List<RemoveRule> collectRemoveNonIngressRules(FlowPath flowPath, int outputPort) {
        if (flowPath == null || CollectionUtils.isEmpty(flowPath.getSegments())) {
            throw new IllegalArgumentException("Flow path with segments is required");
        }

        List<PathSegment> segments = flowPath.getSegments();
        List<RemoveRule> commands = new ArrayList<>(segments.size());

        int transitVlan = getTransitVlan(flowPath.getPathId());
        for (int i = 1; i < segments.size(); i++) {
            PathSegment income = segments.get(i - 1);
            PathSegment outcome = segments.get(i);

            RemoveRule transitRule = buildRemoveTransitRule(flowPath, income.getDestSwitch().getSwitchId(),
                    income.getDestPort(), outcome.getSrcPort(), transitVlan);
            commands.add(transitRule);
        }

        PathSegment egressSegment = segments.get(segments.size() - 1);
        if (!egressSegment.getDestSwitch().getSwitchId().equals(flowPath.getDestSwitch().getSwitchId())) {
            throw new IllegalStateException(
                    format("PathSegment was not found for egress flow rule, flowId: %s", flowPath.getFlowId()));
        }

        RemoveRule egressRule = buildRemoveEgressRule(flowPath, egressSegment.getDestPort(), outputPort, transitVlan);
        commands.add(egressRule);

        return commands;
    }

    private RemoveRule buildRemoveTransitRule(FlowPath flowPath, SwitchId switchId, int inputPort, int outputPort,
                                              int transitVlan) {
        DeleteRulesCriteria criteria = new DeleteRulesCriteria(flowPath.getCookie().getValue(), inputPort, transitVlan,
                0, outputPort);
        return RemoveRule.builder()
                .flowId(flowPath.getFlowId())
                .cookie(flowPath.getCookie().getValue())
                .switchId(switchId)
                .criteria(criteria)
                .build();
    }

    private RemoveRule buildRemoveEgressRule(FlowPath flowPath, int inputPort, int outputPort, int transitVlan) {
        DeleteRulesCriteria criteria = new DeleteRulesCriteria(flowPath.getCookie().getValue(), inputPort, transitVlan,
                0, outputPort);

        return RemoveRule.builder()
                .flowId(flowPath.getFlowId())
                .cookie(flowPath.getCookie().getValue())
                .criteria(criteria)
                .switchId(flowPath.getDestSwitch().getSwitchId())
                .build();
    }

    private OutputVlanType getOutputVlanType(int srcVlan, int destVlan) {
        if (srcVlan == 0) {
            return destVlan == 0 ? OutputVlanType.NONE : OutputVlanType.PUSH;
        }
        return destVlan == 0 ? OutputVlanType.POP : OutputVlanType.REPLACE;
    }

    private int getTransitVlan(PathId pathId) {
        return transitVlanRepository.findByPathId(pathId)
                .map(TransitVlan::getVlan)
                .orElseThrow(() ->
                        new IllegalStateException(format("No flow path found for flow %s", pathId)));
    }

}
