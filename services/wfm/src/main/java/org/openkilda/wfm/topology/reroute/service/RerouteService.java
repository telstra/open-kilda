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

package org.openkilda.wfm.topology.reroute.service;

import org.openkilda.messaging.command.reroute.RerouteAffectedFlows;
import org.openkilda.messaging.command.reroute.RerouteInactiveFlows;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.topology.reroute.bolts.MessageSender;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class RerouteService {

    private FlowRepository flowRepository;
    private FlowPathRepository pathRepository;

    public RerouteService(RepositoryFactory repositoryFactory) {
        this.flowRepository = repositoryFactory.createFlowRepository();
        this.pathRepository = repositoryFactory.createFlowPathRepository();
    }

    /**
     * Handles reroute on ISL down events.
     * @param sender transport sender
     * @param correlationId correlation id to pass through
     * @param command origin command
     */
    public void rerouteAffectedFlows(MessageSender sender, String correlationId, RerouteAffectedFlows command) {
        PathNode pathNode = command.getPathNode();
        int port = pathNode.getPortNo();
        SwitchId switchId = pathNode.getSwitchId();
        Collection<FlowPath> affectedFlowPaths
                = getAffectedFlowPaths(pathNode.getSwitchId(), pathNode.getPortNo());

        // swapping affected primary paths with available protected
        List<FlowPath> pathsForSwapping = getPathsForSwapping(affectedFlowPaths);
        for (FlowPath path : pathsForSwapping) {
            sender.emitPathSwapCommand(correlationId, path, command.getReason());
        }
        Map<Flow, Set<PathId>> flowsForRerouting = groupFlowsForRerouting(affectedFlowPaths);
        for (Entry<Flow, Set<PathId>> entry : flowsForRerouting.entrySet()) {
            sender.emitRerouteCommand(correlationId, entry.getKey(), entry.getValue(),
                    command.getReason());
        }
        Set<Flow> affectedPinnedFlows = groupAffectedPinnedFlows(affectedFlowPaths);
        for (Flow flow : affectedPinnedFlows) {
            for (FlowPath fp : flow.getPaths()) {
                boolean failedFlowPath = false;
                for (PathSegment pathSegment : fp.getSegments()) {
                    if (pathSegment.getSrcPort() == port
                            && switchId.equals(pathSegment.getSrcSwitch().getSwitchId())
                            || (pathSegment.getDestPort() == port
                            && switchId.equals(pathSegment.getDestSwitch().getSwitchId()))) {
                        pathSegment.setFailed(true);
                        failedFlowPath = true;
                        break;
                    }
                }
                if (failedFlowPath) {
                    fp.setStatus(FlowPathStatus.INACTIVE);
                }
            }
            flow.setStatus(FlowStatus.DOWN);
            flowRepository.createOrUpdate(flow);
        }
    }

    /**
     * Handles reroute on ISL up events.
     * @param sender transport sender
     * @param correlationId correlation id to pass through
     * @param command origin command
     */
    public void rerouteInactiveFlows(MessageSender sender, String correlationId, RerouteInactiveFlows command) {
        PathNode pathNode = command.getPathNode();
        int port = pathNode.getPortNo();
        SwitchId switchId = pathNode.getSwitchId();
        Map<Flow, Set<PathId>> flowsForRerouting = getInactiveFlowsForRerouting();

        for (Entry<Flow, Set<PathId>> entry : flowsForRerouting.entrySet()) {
            Flow flow = entry.getKey();
            if (flow.isPinned()) {
                int failedFlowPathsCount = 0;
                for (FlowPath flowPath : flow.getPaths()) {
                    int failedSegmentsCount = 0;
                    for (PathSegment pathSegment : flowPath.getSegments()) {
                        if (pathSegment.isFailed()) {
                            if (pathSegment.containsNode(switchId, port)) {
                                pathSegment.setFailed(false);
                            } else {
                                failedSegmentsCount++;
                            }
                        }
                    }
                    if (flowPath.getStatus().equals(FlowPathStatus.INACTIVE)) {
                        if (failedSegmentsCount == 0) {
                            flowPath.setStatus(FlowPathStatus.ACTIVE);
                        } else {
                            failedFlowPathsCount++;
                        }
                    }

                }
                if (failedFlowPathsCount == 0) {
                    flow.setStatus(FlowStatus.UP);
                }
                flowRepository.createOrUpdate(flow);
                log.info("Skipping reroute command for pinned flow {}", flow.getFlowId());
            } else {
                sender.emitRerouteCommand(correlationId, entry.getKey(), entry.getValue(),
                        command.getReason());
            }
        }
    }

    /**
     * Get list of active affected flow paths with flows.
     *
     * @param switchId switch id.
     * @param port     port.
     * @return list affected flows and flow paths.
     */
    public Collection<FlowPath> getAffectedFlowPaths(SwitchId switchId, int port) {
        log.info("Get affected flow paths by node {}_{}", switchId, port);
        return pathRepository.findBySegmentEndpoint(switchId, port);
    }


    /**
     * Get flow paths list to swap.
     *
     * @return list of flows paths.
     */
    public List<FlowPath> getPathsForSwapping(Collection<FlowPath> paths) {
        return paths.stream()
                .filter(path -> path.getStatus() == null || FlowPathStatus.ACTIVE.equals(path.getStatus()))
                .filter(path -> path.getFlow().isAllocateProtectedPath())
                .filter(this::filterForwardPrimaryPath)
                .collect(Collectors.toList());
    }

    private boolean filterForwardPrimaryPath(FlowPath path) {
        return path.getPathId().equals(path.getFlow().getForwardPathId());
    }

    /**
     * Returns map with flow for reroute and set of reroute pathId.
     *
     * @return map with flow for reroute and set of reroute pathId.
     */
    public Map<Flow, Set<PathId>> groupFlowsForRerouting(Collection<FlowPath> paths) {
        return paths.stream()
                .filter(path -> !path.getFlow().isPinned())
                .collect(Collectors.groupingBy(FlowPath::getFlow,
                        Collectors.mapping(FlowPath::getPathId, Collectors.toSet())));
    }

    /**
     * Filters out unique pinned flow from paths.
     *
     * @param paths affected paths
     * @return
     */
    public Set<Flow> groupAffectedPinnedFlows(Collection<FlowPath> paths) {
        return paths.stream()
                .filter(path -> path.getFlow().isPinned())
                .map(FlowPath::getFlow)
                .collect(Collectors.toSet());
    }

    /**
     * Returns map with inactive flow and flow pathId set for rerouting.
     */
    public Map<Flow, Set<PathId>> getInactiveFlowsForRerouting() {
        log.info("Get inactive flows");
        return flowRepository.findDownFlows().stream()
                .collect(Collectors.toMap(Function.identity(),
                        flow -> flow.getPaths().stream()
                        .filter(path -> FlowPathStatus.INACTIVE.equals(path.getStatus()))
                        .map(FlowPath::getPathId)
                        .collect(Collectors.toSet()))
                );
    }
}
