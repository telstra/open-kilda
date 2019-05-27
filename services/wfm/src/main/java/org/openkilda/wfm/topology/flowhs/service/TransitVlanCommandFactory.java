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
import org.openkilda.floodlight.flow.request.InstallMultiSwitchIngressRule;
import org.openkilda.floodlight.flow.request.InstallSingleSwitchIngressRule;
import org.openkilda.floodlight.flow.request.InstallTransitRule;
import org.openkilda.floodlight.flow.request.RemoveRule;
import org.openkilda.messaging.MessageContext;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.OutputVlanType;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.model.TransitVlan;
import org.openkilda.persistence.repositories.TransitVlanRepository;
import org.openkilda.wfm.CommandContext;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import com.google.common.collect.ImmutableList;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
public class TransitVlanCommandFactory implements FlowCommandFactory {
    private final NoArgGenerator commandIdGenerator = Generators.timeBasedGenerator();
    private final TransitVlanRepository transitVlanRepository;

    public TransitVlanCommandFactory(TransitVlanRepository transitVlanRepository) {
        this.transitVlanRepository = transitVlanRepository;
    }

    @Override
    public List<InstallTransitRule> createInstallNonIngressRules(CommandContext context, Flow flow) {
        return createInstallNonIngressRules(context, flow, flow.getForwardPath(), flow.getReversePath());
    }

    @Override
    public List<InstallTransitRule> createInstallNonIngressRules(CommandContext context, Flow flow,
                                                                 FlowPath forwardPath, FlowPath reversePath) {
        if (flow.isOneSwitchFlow()) {
            return Collections.emptyList();
        }

        List<InstallTransitRule> forwardRules = collectNonIngressRules(
                context, forwardPath, flow.getDestPort(),
                flow.getSrcVlan(), getTransitVlan(forwardPath, reversePath), flow.getDestVlan());
        List<InstallTransitRule> reverseRules = collectNonIngressRules(
                context, reversePath, flow.getSrcPort(),
                flow.getDestVlan(), getTransitVlan(reversePath, forwardPath), flow.getSrcVlan());
        return ListUtils.union(forwardRules, reverseRules);
    }

    @Override
    public List<InstallIngressRule> createInstallIngressRules(CommandContext context, Flow flow) {
        return createInstallIngressRules(context, flow, flow.getForwardPath(), flow.getReversePath());
    }

    @Override
    public List<InstallIngressRule> createInstallIngressRules(CommandContext context, Flow flow,
                                                              FlowPath forwardPath, FlowPath reversePath) {
        InstallIngressRule forwardRule;
        InstallIngressRule reverseRule;
        if (flow.isOneSwitchFlow()) {
            forwardRule = buildInstallOneSwitchRule(context, forwardPath,
                    flow.getSrcPort(), flow.getDestPort(), flow.getSrcVlan(), flow.getDestVlan());
            reverseRule = buildInstallOneSwitchRule(context, reversePath,
                    flow.getDestPort(), flow.getSrcPort(), flow.getDestVlan(), flow.getSrcVlan());
        } else {
            forwardRule = buildInstallIngressRule(context, forwardPath,
                                                  flow.getSrcPort(), flow.getSrcVlan(),
                                                  getTransitVlan(forwardPath, reversePath), flow.getDestVlan());
            reverseRule = buildInstallIngressRule(context, reversePath,
                                                  flow.getDestPort(), flow.getDestVlan(),
                                                  getTransitVlan(reversePath, forwardPath), flow.getSrcVlan());
        }

        return ImmutableList.of(forwardRule, reverseRule);
    }

    @Override
    public List<RemoveRule> createRemoveNonIngressRules(CommandContext context, Flow flow) {
        return createRemoveNonIngressRules(context, flow, flow.getForwardPath(), flow.getReversePath());
    }

    @Override
    public List<RemoveRule> createRemoveNonIngressRules(CommandContext context, Flow flow,
                                                        FlowPath forwardPath, FlowPath reversePath) {
        if (flow.isOneSwitchFlow()) {
            // Removing of single switch rules is done with no output port in criteria.
            return Collections.emptyList();
        }

        List<RemoveRule> commands = new ArrayList<>();
        commands.addAll(collectRemoveNonIngressRules(context, forwardPath, getTransitVlan(forwardPath, reversePath),
                                                     flow.getDestPort()));
        commands.addAll(collectRemoveNonIngressRules(context, reversePath, getTransitVlan(reversePath, forwardPath),
                                                     flow.getSrcPort()));
        return commands;
    }

    @Override
    public List<RemoveRule> createRemoveIngressRules(CommandContext context, Flow flow) {
        return createRemoveIngressRules(context, flow, flow.getForwardPath(), flow.getReversePath());
    }

    @Override
    public List<RemoveRule> createRemoveIngressRules(CommandContext context, Flow flow,
                                                     FlowPath forwardPath, FlowPath reversePath) {
        RemoveRule removeForwardIngress =
                buildRemoveIngressRule(context, forwardPath, flow.getSrcPort(), flow.getSrcVlan());
        RemoveRule removeReverseIngress =
                buildRemoveIngressRule(context, reversePath, flow.getDestPort(), flow.getDestVlan());

        return ImmutableList.of(removeForwardIngress, removeReverseIngress);
    }

    private InstallMultiSwitchIngressRule buildInstallIngressRule(CommandContext context, FlowPath flowPath,
                                                                  int inputPort,
                                                                  int inputVlanId, int transitVlan, int outputVlanId) {
        PathSegment ingressSegment = flowPath.getSegments().stream()
                .filter(segment -> segment.getSrcSwitch().equals(flowPath.getSrcSwitch()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        format("PathSegment was not found for ingress flow rule, flowId: %s",
                                flowPath.getFlow().getFlowId())));

        String commandId = commandIdGenerator.generate().toString();
        return InstallMultiSwitchIngressRule.builder()
                .messageContext(new MessageContext(commandId, context.getCorrelationId()))
                .commandId(commandIdGenerator.generate())
                .flowId(flowPath.getFlow().getFlowId())
                .switchId(flowPath.getSrcSwitch().getSwitchId())
                .cookie(flowPath.getCookie())
                .bandwidth(flowPath.getBandwidth())
                .meterId(flowPath.getMeterId())
                .inputPort(inputPort)
                .outputPort(ingressSegment.getSrcPort())
                .outputVlanType(getOutputVlanType(inputVlanId, outputVlanId))
                .inputVlanId(inputVlanId)
                .transitVlanId(transitVlan)
                .build();
    }

    private InstallSingleSwitchIngressRule buildInstallOneSwitchRule(CommandContext context, FlowPath flowPath,
                                                                     int inputPort, int outputPort,
                                                                     int inputVlanId, int outputVlanId) {
        String commandId = commandIdGenerator.generate().toString();
        return InstallSingleSwitchIngressRule.builder()
                .messageContext(new MessageContext(commandId, context.getCorrelationId()))
                .commandId(commandIdGenerator.generate())
                .flowId(flowPath.getFlow().getFlowId())
                .switchId(flowPath.getSrcSwitch().getSwitchId())
                .cookie(flowPath.getCookie())
                .bandwidth(flowPath.getBandwidth())
                .meterId(flowPath.getMeterId())
                .inputPort(inputPort)
                .outputPort(outputPort)
                .outputVlanType(getOutputVlanType(inputVlanId, outputVlanId))
                .inputVlanId(inputVlanId)
                .outputVlanId(outputVlanId)
                .build();
    }

    private List<InstallTransitRule> collectNonIngressRules(CommandContext context, FlowPath flowPath, int outputPort,
                                                            int srcVlan, int transitVlan, int destVlan) {
        if (flowPath == null || CollectionUtils.isEmpty(flowPath.getSegments())) {
            throw new IllegalArgumentException("Flow path with segments is required");
        }

        List<PathSegment> segments = flowPath.getSegments();
        List<InstallTransitRule> commands = new ArrayList<>(segments.size());

        for (int i = 1; i < segments.size(); i++) {
            PathSegment income = segments.get(i - 1);
            PathSegment outcome = segments.get(i);

            InstallTransitRule transitRule = buildInstallTransitRule(context, flowPath,
                    income.getDestSwitch().getSwitchId(), income.getDestPort(), outcome.getSrcPort(), transitVlan);
            commands.add(transitRule);
        }

        PathSegment egressSegment = segments.get(segments.size() - 1);
        if (!egressSegment.getDestSwitch().getSwitchId().equals(flowPath.getDestSwitch().getSwitchId())) {
            throw new IllegalStateException(format("PathSegment was not found for egress flow rule pathId: %s",
                    flowPath.getPathId()));
        }

        InstallEgressRule egressRule = buildInstallEgressRule(context, flowPath, egressSegment.getDestPort(),
                outputPort, srcVlan, transitVlan, destVlan);
        commands.add(egressRule);

        return commands;
    }

    private InstallTransitRule buildInstallTransitRule(CommandContext context, FlowPath flowPath, SwitchId switchId,
                                                       int inputPort, int outputPort, int transitVlan) {
        UUID commandId = commandIdGenerator.generate();
        MessageContext messageContext = new MessageContext(commandId.toString(), context.getCorrelationId());

        return new InstallTransitRule(messageContext, commandId, flowPath.getFlow().getFlowId(), flowPath.getCookie(),
                switchId, inputPort, outputPort, transitVlan);
    }

    private InstallEgressRule buildInstallEgressRule(CommandContext context, FlowPath flowPath, int inputPort,
                                                     int outputPort, int srcVlan, int transitVlan, int destVlan) {
        UUID commandId = commandIdGenerator.generate();
        return InstallEgressRule.builder()
                .messageContext(new MessageContext(commandId.toString(), context.getCorrelationId()))
                .commandId(commandId)
                .flowId(flowPath.getFlow().getFlowId())
                .switchId(flowPath.getDestSwitch().getSwitchId())
                .cookie(flowPath.getCookie())
                .inputPort(inputPort)
                .transitVlanId(transitVlan)
                .outputPort(outputPort)
                .outputVlanId(destVlan)
                .outputVlanType(getOutputVlanType(srcVlan, destVlan))
                .build();
    }

    private RemoveRule buildRemoveIngressRule(CommandContext context, FlowPath flowPath, int inputPort,
                                              int inputVlanId) {
        PathSegment ingressSegment = flowPath.getSegments().stream()
                .filter(segment -> segment.getSrcSwitch().equals(flowPath.getSrcSwitch()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        format("PathSegment was not found for ingress flow rule, flowId: %s",
                                flowPath.getFlow().getFlowId())));

        DeleteRulesCriteria ingressCriteria = new DeleteRulesCriteria(flowPath.getCookie().getValue(), inputPort,
                inputVlanId, 0, ingressSegment.getSrcPort());
        UUID commandId = commandIdGenerator.generate();
        return RemoveRule.builder()
                .messageContext(new MessageContext(commandId.toString(), context.getCorrelationId()))
                .commandId(commandId)
                .flowId(flowPath.getFlow().getFlowId())
                .switchId(flowPath.getSrcSwitch().getSwitchId())
                .cookie(flowPath.getCookie())
                .meterId(flowPath.getMeterId())
                .criteria(ingressCriteria)
                .build();
    }

    private List<RemoveRule> collectRemoveNonIngressRules(CommandContext context, FlowPath flowPath, int transitVlan,
                                                          int outputPort) {
        if (flowPath == null || CollectionUtils.isEmpty(flowPath.getSegments())) {
            throw new IllegalArgumentException("Flow path with segments is required");
        }

        List<PathSegment> segments = flowPath.getSegments();
        List<RemoveRule> commands = new ArrayList<>(segments.size());

        for (int i = 1; i < segments.size(); i++) {
            PathSegment income = segments.get(i - 1);
            PathSegment outcome = segments.get(i);

            RemoveRule transitRule = buildRemoveTransitRule(context, flowPath, income.getDestSwitch().getSwitchId(),
                    income.getDestPort(), outcome.getSrcPort(), transitVlan);
            commands.add(transitRule);
        }

        PathSegment egressSegment = segments.get(segments.size() - 1);
        if (!egressSegment.getDestSwitch().getSwitchId().equals(flowPath.getDestSwitch().getSwitchId())) {
            throw new IllegalStateException(
                    format("PathSegment was not found for egress flow rule, flowId: %s",
                            flowPath.getFlow().getFlowId()));
        }

        RemoveRule egressRule =
                buildRemoveEgressRule(context, flowPath, egressSegment.getDestPort(), outputPort, transitVlan);
        commands.add(egressRule);

        return commands;
    }

    private RemoveRule buildRemoveTransitRule(CommandContext context, FlowPath flowPath, SwitchId switchId,
                                              int inputPort, int outputPort, int transitVlan) {
        DeleteRulesCriteria criteria = new DeleteRulesCriteria(flowPath.getCookie().getValue(), inputPort, transitVlan,
                0, outputPort);
        UUID commandId = commandIdGenerator.generate();
        return RemoveRule.builder()
                .messageContext(new MessageContext(commandId.toString(), context.getCorrelationId()))
                .commandId(commandId)
                .flowId(flowPath.getFlow().getFlowId())
                .cookie(flowPath.getCookie())
                .switchId(switchId)
                .criteria(criteria)
                .build();
    }

    private RemoveRule buildRemoveEgressRule(CommandContext context, FlowPath flowPath, int inputPort, int outputPort,
                                             int transitVlan) {
        DeleteRulesCriteria criteria = new DeleteRulesCriteria(flowPath.getCookie().getValue(), inputPort, transitVlan,
                0, outputPort);
        UUID commandId = commandIdGenerator.generate();
        return RemoveRule.builder()
                .messageContext(new MessageContext(commandId.toString(), context.getCorrelationId()))
                .commandId(commandId)
                .flowId(flowPath.getFlow().getFlowId())
                .cookie(flowPath.getCookie())
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

    private int getTransitVlan(FlowPath path, FlowPath oppositePath) {
        return transitVlanRepository.findByPathId(path.getPathId(), oppositePath.getPathId()).stream()
                .findAny()
                .map(TransitVlan::getVlan)
                .orElseThrow(() ->
                        new IllegalStateException(format(
                                "No transit VLAN found for flow path %s (opposite: %s)",
                                path.getPathId(), oppositePath.getPathId())));
    }
}
