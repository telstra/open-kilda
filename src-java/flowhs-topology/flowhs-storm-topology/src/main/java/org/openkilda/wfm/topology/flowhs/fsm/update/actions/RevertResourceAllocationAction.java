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

package org.openkilda.wfm.topology.flowhs.fsm.update.actions;

import static java.lang.String.format;

import org.openkilda.model.Flow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.BaseFlowPathRemovalAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.stream.Stream;

@Slf4j
public class RevertResourceAllocationAction extends
        BaseFlowPathRemovalAction<FlowUpdateFsm, State, Event, FlowUpdateContext> {
    private final FlowResourcesManager resourcesManager;

    public RevertResourceAllocationAction(PersistenceManager persistenceManager,
                                          FlowResourcesManager resourcesManager) {
        super(persistenceManager);
        this.resourcesManager = resourcesManager;
    }

    @Override
    protected void perform(State from, State to, Event event, FlowUpdateContext context, FlowUpdateFsm stateMachine) {
        Flow flow = getFlow(stateMachine.getFlowId());

        Stream.of(stateMachine.getNewPrimaryResources(), stateMachine.getNewProtectedResources())
                .filter(Objects::nonNull)
                .forEach(resources -> {
                    transactionManager.doInTransaction(() ->
                            resourcesManager.deallocatePathResources(resources));
                    saveHistory(stateMachine, resources);
                });

        stateMachine.getRejectedResources().forEach(flowResources -> {
            transactionManager.doInTransaction(() ->
                    resourcesManager.deallocatePathResources(flowResources));
            saveHistory(stateMachine, flowResources);
        });

        Stream.of(stateMachine.getNewPrimaryForwardPath(), stateMachine.getNewPrimaryReversePath(),
                stateMachine.getNewProtectedForwardPath(), stateMachine.getNewProtectedReversePath())
                .forEach(pathId ->
                        flowPathRepository.remove(pathId)
                                .ifPresent(flowPath -> {
                                    updateIslsForFlowPath(flowPath);
                                    saveRemovalActionWithDumpToHistory(stateMachine, flow, flowPath);
                                }));

        stateMachine.getRejectedPaths().stream()
                .forEach(pathId ->
                        flowPathRepository.remove(pathId)
                                .ifPresent(flowPath -> {
                                    updateIslsForFlowPath(flowPath);
                                    saveRemovalActionWithDumpToHistory(stateMachine, flow, flowPath);
                                }));

        stateMachine.setNewPrimaryResources(null);
        stateMachine.setNewPrimaryForwardPath(null);
        stateMachine.setNewPrimaryReversePath(null);
        stateMachine.setNewProtectedResources(null);
        stateMachine.setNewProtectedForwardPath(null);
        stateMachine.setNewProtectedReversePath(null);
    }

    private void saveHistory(FlowUpdateFsm stateMachine, FlowResources resources) {
        stateMachine.saveActionToHistory("Flow resources were deallocated",
                format("The flow resources for %s / %s were deallocated",
                        resources.getForward().getPathId(), resources.getReverse().getPathId()));
    }
}
