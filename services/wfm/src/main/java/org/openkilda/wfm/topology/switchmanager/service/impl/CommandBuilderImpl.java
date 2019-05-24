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

package org.openkilda.wfm.topology.switchmanager.service.impl;

import static java.lang.String.format;

import org.openkilda.messaging.command.flow.BaseInstallFlow;
import org.openkilda.messaging.command.flow.InstallEgressFlow;
import org.openkilda.messaging.command.flow.InstallIngressFlow;
import org.openkilda.messaging.command.flow.InstallOneSwitchFlow;
import org.openkilda.messaging.command.flow.InstallTransitFlow;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.messaging.info.rule.FlowApplyActions;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.FlowInstructions;
import org.openkilda.messaging.info.rule.FlowMatchField;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.MeterId;
import org.openkilda.model.OutputVlanType;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.model.TransitVlan;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.TransitVlanRepository;
import org.openkilda.wfm.topology.switchmanager.service.CommandBuilder;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class CommandBuilderImpl implements CommandBuilder {
    private final NoArgGenerator transactionIdGenerator = Generators.timeBasedGenerator();

    private FlowRepository flowRepository;
    private FlowPathRepository flowPathRepository;
    private TransitVlanRepository transitVlanRepository;

    public CommandBuilderImpl(PersistenceManager persistenceManager) {
        this.flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        this.flowPathRepository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
        this.transitVlanRepository = persistenceManager.getRepositoryFactory().createTransitVlanRepository();
    }

    @Override
    public List<BaseInstallFlow> buildCommandsToSyncMissingRules(SwitchId switchId, List<Long> switchRules) {
        List<BaseInstallFlow> commands = new ArrayList<>();

        flowPathRepository.findBySegmentDestSwitch(switchId)
                .forEach(flowPath -> {
                    if (switchRules.contains(flowPath.getCookie().getValue())) {
                        PathSegment segment = flowPath.getSegments().stream()
                                .filter(pathSegment -> pathSegment.getDestSwitch().getSwitchId().equals(switchId))
                                .findAny()
                                .orElseThrow(() -> new IllegalStateException(
                                        format("PathSegment not found, path %s, switch %s", flowPath, switchId)));
                        log.info("Rule {} is to be (re)installed on switch {}", flowPath.getCookie(), switchId);
                        commands.addAll(buildInstallCommandFromSegment(flowPath, segment));
                    }
                });

        flowPathRepository.findByEndpointSwitch(switchId)
                .forEach(flowPath -> {
                    if (switchRules.contains(flowPath.getCookie().getValue())) {
                        Flow flow = flowRepository.findById(flowPath.getFlow().getFlowId())
                                .orElseThrow(() ->
                                        new IllegalStateException(format("Abandon FlowPath found: %s", flowPath)));
                        if (flowPath.isOneSwitchFlow()) {
                            log.info("One-switch flow {} is to be (re)installed on switch {}",
                                    flowPath.getCookie(), switchId);
                            commands.add(buildOneSwitchRuleCommand(flow, flowPath));
                        } else if (flowPath.getSrcSwitch().getSwitchId().equals(switchId)) {
                            log.info("Ingress flow {} is to be (re)installed on switch {}",
                                    flowPath.getCookie(), switchId);
                            if (flowPath.getSegments().isEmpty()) {
                                log.warn("Output port was not found for ingress flow rule");
                            } else {
                                PathSegment foundIngressSegment = flowPath.getSegments().get(0);
                                int transitVlan =
                                        transitVlanRepository.findByPathId(
                                                flowPath.getPathId(), flow.getOppositePathId(flowPath.getPathId()))
                                                .stream()
                                                .findAny()
                                                .map(TransitVlan::getVlan).orElse(0);
                                commands.add(buildInstallIngressRuleCommand(flow, flowPath,
                                        transitVlan, foundIngressSegment.getSrcPort()));
                            }
                        }
                    }
                });

        return commands;
    }

    @Override
    public List<RemoveFlow> buildCommandsToRemoveExcessRules(SwitchId switchId,
                                                             List<FlowEntry> flows,
                                                             List<Long> excessRulesCookies) {
        return flows.stream()
                .filter(flow -> excessRulesCookies.contains(flow.getCookie()))
                .map(entry -> buildRemoveFlowFromFlowEntry(switchId, entry))
                .collect(Collectors.toList());
    }

    private RemoveFlow buildRemoveFlowFromFlowEntry(SwitchId switchId, FlowEntry entry) {
        Optional<FlowMatchField> entryMatch = Optional.ofNullable(entry.getMatch());

        Integer inPort = entryMatch.map(FlowMatchField::getInPort).map(Integer::valueOf).orElse(null);
        Integer vlan = entryMatch.map(FlowMatchField::getVlanVid).map(Integer::valueOf).orElse(null);

        Optional<FlowApplyActions> actions = Optional.ofNullable(entry.getInstructions())
                .map(FlowInstructions::getApplyActions);

        Integer outPort = actions.map(FlowApplyActions::getFlowOutput).map(Integer::valueOf).orElse(null);
        Long meterId = actions.map(FlowApplyActions::getMeter).map(Long::valueOf).orElse(null);

        DeleteRulesCriteria criteria = new DeleteRulesCriteria(entry.getCookie(), inPort, vlan,
                0, outPort);

        return new RemoveFlow(transactionIdGenerator.generate(), "SWMANAGER_BATCH_REMOVE", entry.getCookie(),
                switchId, meterId, criteria);
    }

    private List<BaseInstallFlow> buildInstallCommandFromSegment(FlowPath flowPath, PathSegment segment) {
        if (segment.getSrcSwitch().getSwitchId().equals(segment.getDestSwitch().getSwitchId())) {
            log.warn("One-switch flow segment {} is provided", flowPath.getCookie());
            return new ArrayList<>();
        }

        Optional<Flow> foundFlow = flowRepository.findById(flowPath.getFlow().getFlowId());
        if (!foundFlow.isPresent()) {
            log.warn("Flow with id {} was not found", flowPath.getFlow().getFlowId());
            return new ArrayList<>();
        }
        Flow flow = foundFlow.get();

        int transitVlan =
                transitVlanRepository.findByPathId(flowPath.getPathId(),
                                                   flow.getOppositePathId(flowPath.getPathId())).stream()
                        .findAny()
                        .map(TransitVlan::getVlan).orElse(0);

        if (segment.getDestSwitch().getSwitchId().equals(flowPath.getDestSwitch().getSwitchId())) {
            return Collections.singletonList(buildInstallEgressRuleCommand(flow, flowPath, segment.getDestPort(),
                    transitVlan));
        } else {
            int segmentIdx = flowPath.getSegments().indexOf(segment);
            if (segmentIdx < 0 || segmentIdx + 1 == flowPath.getSegments().size()) {
                log.warn("Paired segment for switch {} and cookie {} has not been found",
                        segment.getDestSwitch().getSwitchId(), flowPath.getCookie());
                return new ArrayList<>();
            }

            PathSegment foundPairedFlowSegment = flowPath.getSegments().get(segmentIdx + 1);

            return Collections.singletonList(buildInstallTransitRuleCommand(flowPath,
                    segment.getDestSwitch().getSwitchId(),
                    segment.getDestPort(), foundPairedFlowSegment.getSrcPort(), transitVlan));
        }
    }

    private InstallIngressFlow buildInstallIngressRuleCommand(Flow flow, FlowPath flowPath,
                                                              int transitVlan, int outputPortNo) {
        boolean forward = flow.isForward(flowPath);
        int inPort = forward ? flow.getSrcPort() : flow.getDestPort();
        int inVlan = forward ? flow.getSrcVlan() : flow.getDestVlan();
        int outVlan = forward ? flow.getDestVlan() : flow.getSrcVlan();
        OutputVlanType outputVlanType = getOutputVlanType(inVlan, outVlan);

        return new InstallIngressFlow(transactionIdGenerator.generate(), flow.getFlowId(),
                flowPath.getCookie().getValue(), flowPath.getSrcSwitch().getSwitchId(), inPort,
                outputPortNo, inVlan, transitVlan, outputVlanType,
                flowPath.getBandwidth(),
                Optional.ofNullable(flowPath.getMeterId()).map(MeterId::getValue).orElse(null));
    }

    private InstallTransitFlow buildInstallTransitRuleCommand(FlowPath flowPath, SwitchId switchId,
                                                              int inputPortNo, int outputPortNo, int transitVlan) {
        return new InstallTransitFlow(transactionIdGenerator.generate(), flowPath.getFlow().getFlowId(),
                flowPath.getCookie().getValue(),
                switchId, inputPortNo, outputPortNo, transitVlan);
    }

    private InstallEgressFlow buildInstallEgressRuleCommand(Flow flow, FlowPath flowPath, int inputPortNo,
                                                            int transitVlan) {
        boolean forward = flow.isForward(flowPath);
        int inVlan = forward ? flow.getSrcVlan() : flow.getDestVlan();
        int outPort = forward ? flow.getDestPort() : flow.getSrcPort();
        int outVlan = forward ? flow.getDestVlan() : flow.getSrcVlan();
        OutputVlanType outputVlanType = getOutputVlanType(inVlan, outVlan);

        return new InstallEgressFlow(transactionIdGenerator.generate(), flowPath.getFlow().getFlowId(),
                flowPath.getCookie().getValue(), flowPath.getDestSwitch().getSwitchId(), inputPortNo, outPort,
                transitVlan, outVlan, outputVlanType);
    }

    private InstallOneSwitchFlow buildOneSwitchRuleCommand(Flow flow, FlowPath flowPath) {
        boolean forward = flow.isForward(flowPath);
        int inPort = forward ? flow.getSrcPort() : flow.getDestPort();
        int inVlan = forward ? flow.getSrcVlan() : flow.getDestVlan();
        int outPort = forward ? flow.getDestPort() : flow.getSrcPort();
        int outVlan = forward ? flow.getDestVlan() : flow.getSrcVlan();
        OutputVlanType outputVlanType = getOutputVlanType(inVlan, outVlan);

        return new InstallOneSwitchFlow(transactionIdGenerator.generate(),
                flow.getFlowId(), flowPath.getCookie().getValue(), flowPath.getSrcSwitch().getSwitchId(), inPort,
                outPort, inVlan, outVlan,
                outputVlanType, flowPath.getBandwidth(),
                Optional.ofNullable(flowPath.getMeterId()).map(MeterId::getValue).orElse(null));
    }

    private OutputVlanType getOutputVlanType(int sourceVlanId, int destinationVlanId) {
        if (sourceVlanId == 0) {
            return destinationVlanId == 0 ? OutputVlanType.NONE : OutputVlanType.PUSH;
        }
        return destinationVlanId == 0 ? OutputVlanType.POP : OutputVlanType.REPLACE;
    }
}
