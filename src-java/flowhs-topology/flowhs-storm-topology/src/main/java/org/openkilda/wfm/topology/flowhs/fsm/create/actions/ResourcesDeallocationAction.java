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

package org.openkilda.wfm.topology.flowhs.fsm.create.actions;

import static java.lang.String.format;

import org.openkilda.model.Flow;
import org.openkilda.model.PathSegment;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.exceptions.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
public class ResourcesDeallocationAction extends
        FlowProcessingWithHistorySupportAction<FlowCreateFsm, State, Event, FlowCreateContext> {
    private final FlowResourcesManager resourcesManager;
    private final IslRepository islRepository;

    public ResourcesDeallocationAction(FlowResourcesManager resourcesManager, PersistenceManager persistenceManager) {
        super(persistenceManager);

        this.resourcesManager = resourcesManager;
        this.islRepository = persistenceManager.getRepositoryFactory().createIslRepository();
    }

    @Override
    protected void perform(State from, State to, Event event, FlowCreateContext context, FlowCreateFsm stateMachine) {
        try {
            transactionManager.doInTransaction(() -> {
                Flow flow = getFlow(stateMachine.getFlowId());
                flow.resetPaths();
            });
        } catch (FlowProcessingException e) {
            stateMachine.saveActionToHistory("Skip resources deallocation",
                    format("Skip resources deallocation. Flow %s has already been deleted: %s",
                            stateMachine.getFlowId(), e.getMessage()));
            return;
        }

        Collection<FlowResources> flowResources = stateMachine.getFlowResources();
        for (FlowResources resources : flowResources) {
            List<PathSegment> removedSegments = new ArrayList<>();

            Stream.of(resources.getForward().getPathId(), resources.getReverse().getPathId())
                    .forEach(pathId ->
                            flowPathRepository.remove(pathId)
                                    .ifPresent(path -> removedSegments.addAll(path.getSegments())));

            updateIslsForSegments(removedSegments);

            transactionManager.doInTransaction(() ->
                    resourcesManager.deallocatePathResources(resources));
        }

        if (!stateMachine.isPathsBeenAllocated()) {
            flowRepository.remove(stateMachine.getFlowId());
        }

        stateMachine.saveActionToHistory("The resources have been deallocated");
    }

    private void updateIslsForSegments(List<PathSegment> pathSegments) {
        pathSegments.forEach(pathSegment ->
                transactionManager.doInTransaction(() ->
                        islRepository.updateAvailableBandwidth(
                                pathSegment.getSrcSwitchId(), pathSegment.getSrcPort(),
                                pathSegment.getDestSwitchId(), pathSegment.getDestPort())));
    }
}
