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
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.wfm.share.flow.resources.EncapsulationResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.service.FlowCommandFactory;
import org.openkilda.wfm.topology.switchmanager.service.CommandBuilder;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
public class CommandBuilderImpl implements CommandBuilder {
    private FlowRepository flowRepository;
    private FlowPathRepository flowPathRepository;
    private FlowCommandFactory flowCommandFactory = new FlowCommandFactory();
    private FlowResourcesManager flowResourcesManager;

    public CommandBuilderImpl(PersistenceManager persistenceManager, FlowResourcesConfig flowResourcesConfig) {
        this.flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        this.flowPathRepository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
        this.flowResourcesManager = new FlowResourcesManager(persistenceManager, flowResourcesConfig);
    }

    @Override
    public List<BaseInstallFlow> buildCommandsToSyncRules(SwitchId switchId, List<Long> switchRules) {
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
                            commands.add(flowCommandFactory.makeOneSwitchRule(flow, flowPath));
                        } else if (flowPath.getSrcSwitch().getSwitchId().equals(switchId)) {
                            log.info("Ingress flow {} is to be (re)installed on switch {}",
                                    flowPath.getCookie(), switchId);
                            if (flowPath.getSegments().isEmpty()) {
                                log.warn("Output port was not found for ingress flow rule");
                            } else {
                                PathSegment foundIngressSegment = flowPath.getSegments().get(0);
                                EncapsulationResources encapsulationResources =
                                        flowResourcesManager.getEncapsulationResources(flowPath.getPathId(),
                                                flow.getOppositePathId(flowPath.getPathId()),
                                                flow.getEncapsulationType())
                                                .orElseThrow(() -> new IllegalStateException(
                                        format("Encapsulation resources are not found for path %s", flowPath)));
                                commands.add(flowCommandFactory.buildInstallIngressFlow(flow, flowPath,
                                        foundIngressSegment.getSrcPort(), encapsulationResources));
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

        Optional<Flow> foundFlow = flowRepository.findById(flowPath.getFlow().getFlowId());
        if (!foundFlow.isPresent()) {
            log.warn("Flow with id {} was not found", flowPath.getFlow().getFlowId());
            return new ArrayList<>();
        }
        Flow flow = foundFlow.get();

        EncapsulationResources encapsulationResources =
                flowResourcesManager.getEncapsulationResources(flowPath.getPathId(),
                        flow.getOppositePathId(flowPath.getPathId()), flow.getEncapsulationType())
                        .orElseThrow(() -> new IllegalStateException(
                                        format("Encapsulation resources are not found for path %s", flowPath)));

        if (segment.getDestSwitch().getSwitchId().equals(flowPath.getDestSwitch().getSwitchId())) {
            return Collections.singletonList(
                    flowCommandFactory.buildInstallEgressFlow(flowPath, segment.getDestPort(), encapsulationResources));
        } else {
            int segmentIdx = flowPath.getSegments().indexOf(segment);
            if (segmentIdx < 0 || segmentIdx + 1 == flowPath.getSegments().size()) {
                log.warn("Paired segment for switch {} and cookie {} has not been found",
                        segment.getDestSwitch().getSwitchId(), flowPath.getCookie());
                return new ArrayList<>();
            }

            PathSegment foundPairedFlowSegment = flowPath.getSegments().get(segmentIdx + 1);

            return Collections.singletonList(flowCommandFactory.buildInstallTransitFlow(
                    flowPath, segment.getDestSwitch().getSwitchId(), segment.getDestPort(),
                    foundPairedFlowSegment.getSrcPort(), encapsulationResources));
        }
    }
}
