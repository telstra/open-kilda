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

import org.openkilda.messaging.command.flow.BaseInstallFlow;
import org.openkilda.messaging.command.flow.InstallEgressFlow;
import org.openkilda.messaging.command.flow.InstallIngressFlow;
import org.openkilda.messaging.command.flow.InstallOneSwitchFlow;
import org.openkilda.messaging.command.flow.InstallTransitFlow;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowSegment;
import org.openkilda.model.OutputVlanType;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.FlowSegmentRepository;
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
    private FlowSegmentRepository flowSegmentRepository;

    public CommandBuilderImpl(PersistenceManager persistenceManager) {
        this.flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        this.flowSegmentRepository = persistenceManager.getRepositoryFactory().createFlowSegmentRepository();
    }

    @Override
    public List<BaseInstallFlow> buildCommandsToSyncRules(SwitchId switchId, List<Long> switchRules) {
        List<BaseInstallFlow> commands = new ArrayList<>();

        flowSegmentRepository.findByDestSwitchId(switchId).forEach(segment -> {
            if (switchRules.contains(segment.getCookie())) {
                log.info("Rule {} is to be (re)installed on switch {}", segment.getCookie(), switchId);
                commands.addAll(buildInstallCommandFromSegment(segment));
            }
        });

        flowRepository.findBySrcSwitchId(switchId).forEach(flow -> {
            if (switchRules.contains(flow.getCookie())) {
                OutputVlanType outputVlanType = getOutputVlanType(flow);

                if (flow.isOneSwitchFlow()) {
                    log.info("One-switch flow {} is to be (re)installed on switch {}", flow.getCookie(), switchId);
                    commands.add(buildOneSwitchRuleCommand(flow, outputVlanType));
                } else {
                    log.info("Ingress flow {} is to be (re)installed on switch {}", flow.getCookie(), switchId);
                    Optional<FlowSegment> foundIngressSegment = flowSegmentRepository.findBySrcSwitchIdAndCookie(
                            flow.getSrcSwitch().getSwitchId(), flow.getCookie());
                    if (!foundIngressSegment.isPresent()) {
                        log.warn("Output port was not found for ingress flow rule");
                    } else {
                        commands.add(buildInstallIngressRuleCommand(flow, foundIngressSegment.get().getSrcPort(),
                                flow.getCookie(), outputVlanType));
                    }
                }
            }
        });

        return commands;
    }

    private List<BaseInstallFlow> buildInstallCommandFromSegment(FlowSegment segment) {
        if (segment.getSrcSwitch().getSwitchId().equals(segment.getDestSwitch().getSwitchId())) {
            log.warn("One-switch flow segment {} is provided", segment.getCookie());
            return new ArrayList<>();
        }

        Optional<Flow> foundFlow = flowRepository.findByIdAndCookie(segment.getFlowId(), segment.getCookie());
        if (!foundFlow.isPresent()) {
            log.warn("Flow with id {} was not found, cookie {}", segment.getFlowId(), segment.getCookie());
            return new ArrayList<>();
        }
        Flow flow = foundFlow.get();

        OutputVlanType outputVlanType = getOutputVlanType(flow);

        if (segment.getDestSwitch().getSwitchId().equals(flow.getDestSwitch().getSwitchId())) {
            return Collections.singletonList(buildInstallEgressRuleCommand(flow, segment.getDestPort(),
                    segment.getCookie(), outputVlanType));
        } else {
            Optional<FlowSegment> foundPairedFlowSegment = flowSegmentRepository.findBySrcSwitchIdAndCookie(
                    segment.getDestSwitch().getSwitchId(), segment.getCookie());
            if (!foundPairedFlowSegment.isPresent()) {
                log.warn("Paired segment for switch {} and cookie {} has not been found",
                        segment.getDestSwitch().getSwitchId(), segment.getCookie());
                return new ArrayList<>();
            }

            return Collections.singletonList(buildInstallTransitRuleCommand(flow, segment.getDestSwitch().getSwitchId(),
                    segment.getDestPort(), foundPairedFlowSegment.get().getSrcPort(), segment.getCookie()));
        }
    }

    private InstallIngressFlow buildInstallIngressRuleCommand(Flow flow, int outputPortNo, long segmentCookie,
                                                              OutputVlanType outputVlanType) {
        if (segmentCookie == 0) {
            segmentCookie = flow.getCookie();
        }
        return new InstallIngressFlow(transactionIdGenerator.generate(), flow.getFlowId(),
                segmentCookie, flow.getSrcSwitch().getSwitchId(), flow.getSrcPort(),
                outputPortNo, flow.getSrcVlan(), flow.getTransitVlan(), outputVlanType,
                flow.getBandwidth(), flow.getMeterLongValue());
    }

    private InstallTransitFlow buildInstallTransitRuleCommand(Flow flow, SwitchId switchId, int inputPortNo,
                                                              int outputPortNo, long segmentCookie) {
        if (segmentCookie == 0) {
            segmentCookie = flow.getCookie();
        }
        return new InstallTransitFlow(transactionIdGenerator.generate(), flow.getFlowId(), segmentCookie,
                switchId, inputPortNo, outputPortNo, flow.getTransitVlan());
    }

    private InstallEgressFlow buildInstallEgressRuleCommand(Flow flow, int inputPortNo, long segmentCookie,
                                                            OutputVlanType outputVlanType) {
        if (segmentCookie == 0) {
            segmentCookie = flow.getCookie();
        }
        return new InstallEgressFlow(transactionIdGenerator.generate(), flow.getFlowId(),
                segmentCookie, flow.getDestSwitch().getSwitchId(), inputPortNo, flow.getDestPort(),
                flow.getTransitVlan(), flow.getDestVlan(), outputVlanType);
    }

    private InstallOneSwitchFlow buildOneSwitchRuleCommand(Flow flow, OutputVlanType outputVlanType) {
        return new InstallOneSwitchFlow(transactionIdGenerator.generate(),
                flow.getFlowId(), flow.getCookie(), flow.getSrcSwitch().getSwitchId(), flow.getSrcPort(),
                flow.getDestPort(), flow.getSrcVlan(), flow.getDestVlan(),
                outputVlanType, flow.getBandwidth(), flow.getMeterLongValue());
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
