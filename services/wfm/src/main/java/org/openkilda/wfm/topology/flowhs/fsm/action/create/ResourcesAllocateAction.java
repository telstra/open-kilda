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

package org.openkilda.wfm.topology.flowhs.fsm.action.create;

import org.openkilda.messaging.Message;
import org.openkilda.model.Cookie;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.pce.Path;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathPair;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResources.PathResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.ResourceAllocationException;
import org.openkilda.wfm.topology.flowhs.fsm.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.FlowCreateFsm.State;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.AnonymousAction;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class ResourcesAllocateAction extends AnonymousAction<FlowCreateFsm, State, Event, FlowCreateContext> {

    private PathComputer pathComputer;
    private FlowResourcesManager resourcesManager;
    private FlowRepository flowRepository;
    private SwitchRepository switchRepository;

    public ResourcesAllocateAction(PersistenceManager persistenceManager, PathComputer pathComputer,
                                   FlowResourcesConfig resourcesConfig) {
        this.resourcesManager = new FlowResourcesManager(persistenceManager, resourcesConfig);
        this.pathComputer = pathComputer;
        this.flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        this.switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
    }

    @Override
    public void execute(State from, State to, Event event, FlowCreateContext context, FlowCreateFsm stateMachine) {
        Optional<Message> response = Optional.empty();

        log.debug("Allocation resources has been started");
        Flow flow = stateMachine.getFlow();
        flow.setSrcSwitch(switchRepository.reload(flow.getSrcSwitch()));
        flow.setDestSwitch(switchRepository.reload(flow.getDestSwitch()));
        PathPair pathPair;

        try {
            pathPair = pathComputer.getPath(flow);
        } catch (UnroutableFlowException | RecoverableException e) {
            stateMachine.fire(Event.Error);
            return;
        }

        flow.setEncapsulationType(FlowEncapsulationType.TRANSIT_VLAN);

        FlowResources flowResources;
        try {
            flowResources = resourcesManager.allocateFlowResources(flow);
        } catch (ResourceAllocationException e) {
            log.error("Failed to allocate resources", e);
            stateMachine.fire(Event.Error);
            return;
        }

        long cookie = flowResources.getUnmaskedCookie();
        flow.setForwardPath(buildFlowPath(flow.getFlowId(), flowResources.getForward(), pathPair.getForward(),
                Cookie.buildForwardCookie(cookie)));
        flow.setReversePath(buildFlowPath(flow.getFlowId(), flowResources.getReverse(), pathPair.getReverse(),
                Cookie.buildReverseCookie(cookie)));

        flow.setStatus(FlowStatus.IN_PROGRESS);
        flowRepository.createOrUpdate(flow);
        log.debug("Resources allocated successfully for the flow {}", flow.getFlowId());

        stateMachine.setFlow(flow);
        stateMachine.fire(Event.Next);
    }

    private FlowPath buildFlowPath(String flowId, PathResources pathResources, Path path, Cookie cookie) {
        List<PathSegment> segments = path.getSegments().stream()
                .map(segment -> PathSegment.builder()
                        .pathId(pathResources.getPathId())
                        .srcSwitch(switchRepository.reload(Switch.builder()
                                .switchId(segment.getSrcSwitchId()).build()))
                        .srcPort(segment.getSrcPort())
                        .destSwitch(switchRepository.reload(Switch.builder()
                                .switchId(segment.getDestSwitchId()).build()))
                        .destPort(segment.getDestPort())
                        .latency(segment.getLatency())
                        .build())
                .collect(Collectors.toList());
        return FlowPath.builder()
                .flowId(flowId)
                .pathId(pathResources.getPathId())
                .srcSwitch(switchRepository.reload(Switch.builder()
                        .switchId(path.getSrcSwitchId()).build()))
                .destSwitch(switchRepository.reload(Switch.builder()
                        .switchId(path.getDestSwitchId()).build()))
                .meterId(pathResources.getMeterId())
                .cookie(cookie)
                .segments(segments)
                .build();
    }

}
