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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions;

import static java.lang.String.format;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.persistence.FetchStrategy;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.history.model.FlowDumpData;
import org.openkilda.wfm.share.history.model.FlowDumpData.DumpType;
import org.openkilda.wfm.share.mappers.HistoryMapper;
import org.openkilda.wfm.topology.flow.model.FlowPathPair;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowPathSwappingFsm;

import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.stream.Stream;

@Slf4j
public class RevertResourceAllocationAction<T extends FlowPathSwappingFsm<T, S, E, C>, S, E, C>
        extends BaseFlowPathRemovalAction<T, S, E, C> {
    private final FlowResourcesManager resourcesManager;

    public RevertResourceAllocationAction(PersistenceManager persistenceManager,
                                          FlowResourcesManager resourcesManager) {
        super(persistenceManager);
        this.resourcesManager = resourcesManager;
    }

    @Override
    public void perform(S from, S to, E event, C context, T stateMachine) {
        persistenceManager.getTransactionManager().doInTransaction(() -> {
            Flow flow = getFlow(stateMachine.getFlowId(), FetchStrategy.DIRECT_RELATIONS);

            if (stateMachine.hasNewPrimaryResources()) {
                FlowResources newPrimaryResources = stateMachine.getNewPrimaryResources();
                resourcesManager.deallocatePathResources(newPrimaryResources);
                saveHistory(stateMachine, flow, newPrimaryResources);
            }

            if (stateMachine.hasNewProtectedResources()) {
                FlowResources newProtectedResources = stateMachine.getNewProtectedResources();
                resourcesManager.deallocatePathResources(newProtectedResources);
                saveHistory(stateMachine, flow, newProtectedResources);
            }

            FlowPath newPrimaryForward = null;
            FlowPath newPrimaryReverse = null;
            if (stateMachine.hasNewPrimaryPaths()) {
                newPrimaryForward = getFlowPath(stateMachine.getNewPrimaryForwardPath());
                newPrimaryReverse = getFlowPath(stateMachine.getNewPrimaryReversePath());
            }

            FlowPath newProtectedForward = null;
            FlowPath newProtectedReverse = null;
            if (stateMachine.hasNewProtectedPaths()) {
                newProtectedForward = getFlowPath(stateMachine.getNewProtectedForwardPath());
                newProtectedReverse = getFlowPath(stateMachine.getNewProtectedReversePath());
            }

            flowPathRepository.lockInvolvedSwitches(Stream.of(newPrimaryForward, newPrimaryReverse,
                    newProtectedForward, newProtectedReverse).filter(Objects::nonNull).toArray(FlowPath[]::new));

            if (stateMachine.hasNewPrimaryPaths()) {
                log.debug("Removing the new primary paths {} / {}", newPrimaryForward, newPrimaryReverse);
                FlowPathPair pathsToDelete = FlowPathPair.builder()
                        .forward(newPrimaryForward).reverse(newPrimaryReverse).build();
                deleteFlowPaths(pathsToDelete);

                saveRemovalActionWithDumpToHistory(stateMachine, flow, pathsToDelete);
            }

            if (stateMachine.hasNewProtectedPaths()) {
                log.debug("Removing the new protected paths {} / {}", newProtectedForward, newProtectedReverse);
                FlowPathPair pathsToDelete = FlowPathPair.builder()
                        .forward(newProtectedForward).reverse(newProtectedReverse).build();
                deleteFlowPaths(pathsToDelete);

                saveRemovalActionWithDumpToHistory(stateMachine, flow, pathsToDelete);
            }
        });

        stateMachine.resetNewPrimaryPathsAndResources();
        stateMachine.resetNewProtectedPathsAndResources();
    }

    private void saveHistory(T stateMachine, Flow flow, FlowResources resources) {
        FlowDumpData flowDumpData = HistoryMapper.INSTANCE.map(flow, resources, DumpType.STATE_BEFORE);
        stateMachine.saveActionWithDumpToHistory("Flow resources were deallocated",
                format("The flow resources for %s / %s were deallocated",
                        resources.getForward().getPathId(), resources.getReverse().getPathId()), flowDumpData);
    }
}
