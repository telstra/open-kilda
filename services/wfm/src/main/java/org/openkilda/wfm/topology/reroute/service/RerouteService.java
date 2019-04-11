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

import org.openkilda.model.Flow;
import org.openkilda.model.FlowSegment;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.FlowSegmentRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;

import lombok.extern.slf4j.Slf4j;
import org.openkilda.wfm.topology.flow.service.BaseFlowService;
import org.openkilda.wfm.topology.reroute.bolts.SendRerouteRequestCarrier;
import org.openkilda.wfm.topology.reroute.model.FlowThrottlingData;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class RerouteService extends BaseFlowService {

    private SendRerouteRequestCarrier carrier;
    protected FlowSegmentRepository flowSegmentRepository;

    public RerouteService(PersistenceManager persistenceManager, SendRerouteRequestCarrier carrier) {
        super(persistenceManager);
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        flowSegmentRepository = repositoryFactory.createFlowSegmentRepository();
        this.carrier = carrier;
    }


    public void processRerouteOnIslDown(SwitchId switchId, int port, String reason, String correlationId) {
        Set<Flow> affectedFlows = getAffectedFlows(switchId, port);
        for (Flow flow : affectedFlows) {
            if (flow.getManual() != true) {
                carrier.sendRerouteRequest(flow.getFlowId(),
                        new FlowThrottlingData(correlationId, flow.getPriority(), flow.getTimeCreate()),
                        reason);
            } else {
                Collection<FlowSegment> flowSegments = flowSegmentRepository.findFlowSegmentsByEndpoint(flow.getFlowId(),
                        switchId, port);
                for (FlowSegment fs : flowSegments) {
                    fs.setFailed(true);
                    flowSegmentRepository.createOrUpdate(fs);
                }
                updateFlowStatus(flow.getFlowId(), FlowStatus.DOWN);
            }
        }
    }

    public void processRerouteOnIslUp(SwitchId switchId, int port, String reason, String correlationId) {
        Set<Flow> inactiveFlows = getInactiveFlows();
        for (Flow flow : inactiveFlows) {
            if (flow.getManual() != true) {
                carrier.sendRerouteRequest(flow.getFlowId(),
                        new FlowThrottlingData(correlationId, flow.getPriority(), flow.getTimeCreate()),
                        reason);
            } else {
                Collection<FlowSegment> flowSegments = flowSegmentRepository.findFlowSegmentsByEndpoint(
                        flow.getFlowId(), switchId, port);
                for (FlowSegment fs : flowSegments) {
                    fs.setFailed(false);
                    flowSegmentRepository.createOrUpdate(fs);
                }
                Collection<FlowSegment> failedSegments = flowSegmentRepository.findFailedSegmnetsForFlow(
                        flow.getFlowId());
                if (failedSegments.isEmpty()) {
                    updateFlowStatus(flow.getFlowId(), FlowStatus.UP);
                }
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
    private Set<Flow> getAffectedFlows(SwitchId switchId, int port) {
        log.info("Get affected flows by node {}_{}", switchId, port);
        return flowRepository.findActiveFlowIdsWithPortInPathOverSegments(switchId, port).stream()
                .filter(Flow::isForward)
                .collect(Collectors.toSet());
    }

    /**
     * Get set of inactive flows.
     */
    private Set<Flow> getInactiveFlows() {
        log.info("Get inactive flows");
        return flowRepository.findDownFlows().stream()
                .filter(Flow::isForward)
                .collect(Collectors.toSet());
    }
}
