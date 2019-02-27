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
    public List<BaseInstallFlow> buildCommandsToSyncRules(SwitchId switchId, List<Long> switchRules) {
        List<BaseInstallFlow> commands = new ArrayList<>();

        flowPathRepository.findPathSegmentsByDestSwitchId(switchId).forEach(segment -> {
            FlowPath flowPath = flowPathRepository.findById(segment.getPathId())
                    .orElseThrow(() -> new IllegalStateException(format("Abandon PathSegment found: %s", segment)));
            if (switchRules.contains(flowPath.getCookie().getValue())) {
                log.info("Rule {} is to be (re)installed on switch {}", flowPath.getCookie(), switchId);
                commands.addAll(buildInstallCommandFromSegment(flowPath, segment));
            }
        });

        flowPathRepository.findBySrcSwitchId(switchId)
                .forEach(flowPath -> {
                    if (switchRules.contains(flowPath.getCookie().getValue())) {
                        Flow flow = flowRepository.findById(flowPath.getFlowId())
                                .orElseThrow(() ->
                                        new IllegalStateException(format("Abandon FlowPath found: %s", flowPath)));
                        OutputVlanType outputVlanType = getOutputVlanType(flow);

                        if (flowPath.isOneSwitchFlow()) {
                            log.info("One-switch flow {} is to be (re)installed on switch {}",
                                    flowPath.getCookie(), switchId);
                            commands.add(buildOneSwitchRuleCommand(flow, flowPath, outputVlanType));
                        } else {
                            log.info("Ingress flow {} is to be (re)installed on switch {}",
                                    flowPath.getCookie(), switchId);
                            if (flowPath.getSegments().isEmpty()) {
                                log.warn("Output port was not found for ingress flow rule");
                            } else {
                                PathSegment foundIngressSegment = flowPath.getSegments().get(0);
                                int transitVlan =
                                        transitVlanRepository.findByPathId(flowPath.getPathId())
                                                .map(TransitVlan::getVlan).orElse(0);
                                commands.add(buildInstallIngressRuleCommand(flow, flowPath,
                                        foundIngressSegment.getSrcPort(),
                                        outputVlanType, transitVlan));
                            }
                        }
                    }

                });

        return commands;
    }

    private List<BaseInstallFlow> buildInstallCommandFromSegment(FlowPath flowPath, PathSegment segment) {
        if (segment.getSrcSwitch().getSwitchId().equals(segment.getDestSwitch().getSwitchId())) {
            log.warn("One-switch flow segment {} is provided", flowPath.getCookie());
            return new ArrayList<>();
        }

        Optional<Flow> foundFlow = flowRepository.findById(flowPath.getFlowId());
        if (!foundFlow.isPresent()) {
            log.warn("Flow with id {} was not found", flowPath.getFlowId());
            return new ArrayList<>();
        }
        Flow flow = foundFlow.get();

        int transitVlan =
                transitVlanRepository.findByPathId(flowPath.getPathId())
                        .map(TransitVlan::getVlan).orElse(0);

        OutputVlanType outputVlanType = getOutputVlanType(flow);

        if (segment.getDestSwitch().getSwitchId().equals(flow.getDestSwitch().getSwitchId())) {
            return Collections.singletonList(buildInstallEgressRuleCommand(flow, flowPath, segment.getDestPort(),
                    transitVlan, outputVlanType));
        } else {
            int segmentIdx = flowPath.getSegments().indexOf(segment);
            if (segmentIdx < 0 || segmentIdx + 1 == flowPath.getSegments().size()) {
                log.warn("Paired segment for switch {} and cookie {} has not been found",
                        segment.getDestSwitch().getSwitchId(), flowPath.getCookie());
                return new ArrayList<>();
            }

            PathSegment foundPairedFlowSegment = flowPath.getSegments().get(segmentIdx + 1);

            return Collections.singletonList(buildInstallTransitRuleCommand(flow, flowPath,
                    segment.getDestSwitch().getSwitchId(),
                    segment.getDestPort(), foundPairedFlowSegment.getSrcPort(), transitVlan));
        }
    }

    private InstallIngressFlow buildInstallIngressRuleCommand(Flow flow, FlowPath flowPath, int outputPortNo,
                                                              OutputVlanType outputVlanType, int transitVlan) {
        return new InstallIngressFlow(transactionIdGenerator.generate(), flow.getFlowId(),
                flowPath.getCookie().getValue(), flow.getSrcSwitch().getSwitchId(), flow.getSrcPort(),
                outputPortNo, flow.getSrcVlan(), transitVlan, outputVlanType,
                flow.getBandwidth(), Optional.ofNullable(flowPath.getMeterId()).map(MeterId::getValue).orElse(null));
    }

    private InstallTransitFlow buildInstallTransitRuleCommand(Flow flow, FlowPath flowPath, SwitchId switchId,
                                                              int inputPortNo,
                                                              int outputPortNo, int transitVlan) {
        return new InstallTransitFlow(transactionIdGenerator.generate(), flow.getFlowId(),
                flowPath.getCookie().getValue(),
                switchId, inputPortNo, outputPortNo, transitVlan);
    }

    private InstallEgressFlow buildInstallEgressRuleCommand(Flow flow, FlowPath flowPath, int inputPortNo,
                                                            int transitVlan, OutputVlanType outputVlanType) {
        return new InstallEgressFlow(transactionIdGenerator.generate(), flow.getFlowId(),
                flowPath.getCookie().getValue(), flow.getDestSwitch().getSwitchId(), inputPortNo, flow.getDestPort(),
                transitVlan, flow.getDestVlan(), outputVlanType);
    }

    private InstallOneSwitchFlow buildOneSwitchRuleCommand(Flow flow, FlowPath flowPath,
                                                           OutputVlanType outputVlanType) {
        return new InstallOneSwitchFlow(transactionIdGenerator.generate(),
                flow.getFlowId(), flowPath.getCookie().getValue(), flow.getSrcSwitch().getSwitchId(), flow.getSrcPort(),
                flow.getDestPort(), flow.getSrcVlan(), flow.getDestVlan(),
                outputVlanType, flow.getBandwidth(),
                Optional.ofNullable(flowPath.getMeterId()).map(MeterId::getValue).orElse(null));
    }

    private OutputVlanType getOutputVlanType(Flow flow) {
        int sourceVlanId = flow.getSrcVlan();
        int destinationVlanId = flow.getDestVlan();

        if (sourceVlanId == 0) {
            return destinationVlanId == 0 ? OutputVlanType.NONE : OutputVlanType.PUSH;
        }
        return destinationVlanId == 0 ? OutputVlanType.POP : OutputVlanType.REPLACE;
    }
}
