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
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.topology.reroute.bolts.MessageSender;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;

@Slf4j
public class RerouteService {

    private FlowRepository flowRepository;

    public RerouteService(RepositoryFactory repositoryFactory) {
        this.flowRepository = repositoryFactory.createFlowRepository();
    }

    /**
     * Handles reroute for failed isl case.
     * @param sender transport carrier
     * @param correlationId original correlation id
     * @param rerouteAffectedFlows command payload
     */
    public void rerouteAffectedFlows(MessageSender sender, String correlationId,
                                     RerouteAffectedFlows rerouteAffectedFlows) {
        PathNode pathNode = rerouteAffectedFlows.getPathNode();
        SwitchId switchId = pathNode.getSwitchId();
        int port = pathNode.getPortNo();
        Collection<Flow> affectedUnpinnedFlows
                = getAffectedUnpinnedFlows(switchId, port);
        for (Flow flow : affectedUnpinnedFlows) {
            sender.sendRerouteCommand(correlationId, flow, rerouteAffectedFlows.getReason());
        }
        Collection<Flow> affectedPinnedFlows = getAffectedPinnedFlows(switchId, port);
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
     * Handles reroute for up isl case.
     * @param sender transport carrier
     * @param correlationId original correlation id
     * @param rerouteInactiveFlows command payload
     */
    public void rerouteInactiveFlows(MessageSender sender, String correlationId,
                                     RerouteInactiveFlows rerouteInactiveFlows) {
        Collection<Flow> inactiveFlows = getInactiveFlows();
        PathNode pathNode = rerouteInactiveFlows.getPathNode();
        SwitchId switchId = pathNode.getSwitchId();
        int port = pathNode.getPortNo();
        for (Flow flow : inactiveFlows) {
            if (flow.isPinned()) {
                int failedFlowPathsCount = 0;
                for (FlowPath flowPath: flow.getPaths()) {
                    int failedSegmentsCount = 0;
                    for (PathSegment pathSegment: flowPath.getSegments()) {
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
                sender.sendRerouteCommand(correlationId, flow, rerouteInactiveFlows.getReason());
            }
        }
    }

    /**
     * Get set of active flows with affected path.
     *
     * @param switchId switch id.
     * @param port     port.
     * @return set of active flows with affected path.
     */
    public Collection<Flow> getAffectedUnpinnedFlows(SwitchId switchId, int port) {
        log.info("Get affected unpinned flows by node {}_{}", switchId, port);
        return flowRepository.findActiveUnpinnedFlowsWithPortInPath(switchId, port);
    }

    /**
     * Get set of active flows with affected path.
     *
     * @param switchId switch id.
     * @param port     port.
     * @return set of active flows with affected path.
     */
    public Collection<Flow> getAffectedPinnedFlows(SwitchId switchId, int port) {
        log.info("Get affected pinned flows by node {}_{}", switchId, port);
        return flowRepository.findActivePinnedFlowsWithPortInPath(switchId, port);
    }

    /**
     * Get set of inactive flows.
     */
    public Collection<Flow> getInactiveFlows() {
        log.info("Get inactive flows");
        return flowRepository.findDownFlows();
    }
}
