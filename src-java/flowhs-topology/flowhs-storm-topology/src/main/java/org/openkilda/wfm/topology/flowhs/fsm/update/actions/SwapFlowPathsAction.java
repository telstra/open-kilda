/* Copyright 2020 Telstra Open Source
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
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.EncapsulationResources;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResources.PathResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SwapFlowPathsAction extends
        FlowProcessingWithHistorySupportAction<FlowUpdateFsm, State, Event, FlowUpdateContext> {
    private final FlowResourcesManager resourcesManager;

    public SwapFlowPathsAction(PersistenceManager persistenceManager, FlowResourcesManager resourcesManager) {
        super(persistenceManager);
        this.resourcesManager = resourcesManager;
    }

    @Override
    protected void perform(State from, State to, Event event, FlowUpdateContext context, FlowUpdateFsm stateMachine) {
        swapPrimaryPaths(stateMachine);
        swapProtectedPaths(stateMachine);
    }

    private void swapPrimaryPaths(FlowUpdateFsm stateMachine) {
        PathId newForward = stateMachine.getNewPrimaryForwardPath();
        PathId newReverse = stateMachine.getNewPrimaryReversePath();
        if (newForward != null && newReverse != null) {
            transactionManager.doInTransaction(() -> {
                Flow flow = getFlow(stateMachine.getFlowId());

                FlowPath oldForward = flow.getForwardPath();
                if (oldForward != null) {
                    stateMachine.setOldPrimaryForwardPath(oldForward.getPathId());
                    stateMachine.setOldPrimaryForwardPathStatus(oldForward.getStatus());
                    oldForward.setStatus(FlowPathStatus.IN_PROGRESS);
                }

                FlowPath oldReverse = flow.getReversePath();
                if (oldReverse != null) {
                    stateMachine.setOldPrimaryReversePath(oldReverse.getPathId());
                    stateMachine.setOldPrimaryReversePathStatus(oldReverse.getStatus());
                    oldReverse.setStatus(FlowPathStatus.IN_PROGRESS);
                }

                if (oldForward != null || oldReverse != null) {
                    FlowEncapsulationType oldFlowEncapsulationType =
                            stateMachine.getOriginalFlow().getFlowEncapsulationType();
                    FlowResources oldResources = getResources(
                            oldForward != null ? oldForward : oldReverse,
                            oldReverse != null ? oldReverse : oldForward,
                            oldFlowEncapsulationType);
                    stateMachine.getOldResources().add(oldResources);
                }

                flow.setForwardPathId(newForward);
                flow.setReversePathId(newReverse);

                log.debug("Swapping the primary paths {}/{} with {}/{}",
                        oldForward != null ? oldForward.getPathId() : null,
                        oldReverse != null ? oldReverse.getPathId() : null,
                        newForward, newReverse);
            });
            saveHistory(stateMachine, stateMachine.getFlowId(), newForward, newReverse);
        }
    }

    private void swapProtectedPaths(FlowUpdateFsm stateMachine) {
        transactionManager.doInTransaction(() -> {
            Flow flow = getFlow(stateMachine.getFlowId());
            FlowPath oldForward = flow.getProtectedForwardPath();
            if (oldForward != null) {
                stateMachine.setOldProtectedForwardPath(oldForward.getPathId());
                stateMachine.setOldProtectedForwardPathStatus(oldForward.getStatus());
                oldForward.setStatus(FlowPathStatus.IN_PROGRESS);
            }

            FlowPath oldReverse = flow.getProtectedReversePath();
            if (oldReverse != null) {
                stateMachine.setOldProtectedReversePath(oldReverse.getPathId());
                stateMachine.setOldProtectedReversePathStatus(oldReverse.getStatus());
                oldReverse.setStatus(FlowPathStatus.IN_PROGRESS);
            }

            if (oldForward != null || oldReverse != null) {
                FlowEncapsulationType oldFlowEncapsulationType =
                        stateMachine.getOriginalFlow().getFlowEncapsulationType();
                FlowResources oldProtectedResources = getResources(
                        oldForward != null ? oldForward : oldReverse,
                        oldReverse != null ? oldReverse : oldForward,
                        oldFlowEncapsulationType);
                stateMachine.getOldResources().add(oldProtectedResources);
            }

            PathId newForward = stateMachine.getNewProtectedForwardPath();
            PathId newReverse = stateMachine.getNewProtectedReversePath();
            flow.setProtectedForwardPathId(newForward);
            flow.setProtectedReversePathId(newReverse);

            if (newForward != null && newReverse != null) {
                log.debug("Swapping the protected paths {}/{} with {}/{}",
                        oldForward != null ? oldForward.getPathId() : null,
                        oldReverse != null ? oldReverse.getPathId() : null,
                        newForward, newReverse);

                saveHistory(stateMachine, stateMachine.getFlowId(), newForward, newReverse);
            }
        });
    }

    private FlowResources getResources(FlowPath forwardPath, FlowPath reversePath,
                                       FlowEncapsulationType flowEncapsulationType) {
        EncapsulationResources encapsulationResources = resourcesManager.getEncapsulationResources(
                forwardPath.getPathId(), reversePath.getPathId(), flowEncapsulationType).orElse(null);
        return FlowResources.builder()
                .unmaskedCookie(forwardPath.getCookie().getFlowEffectiveId())
                .forward(PathResources.builder()
                        .pathId(forwardPath.getPathId())
                        .meterId(forwardPath.getMeterId())
                        .encapsulationResources(encapsulationResources)
                        .build())
                .reverse(PathResources.builder()
                        .pathId(reversePath.getPathId())
                        .meterId(reversePath.getMeterId())
                        .encapsulationResources(encapsulationResources)
                        .build())
                .build();
    }

    private void saveHistory(FlowUpdateFsm stateMachine, String flowId, PathId forwardPath, PathId reversePath) {
        stateMachine.saveActionToHistory("Flow was updated with new paths",
                format("The flow %s was updated with paths %s / %s", flowId, forwardPath, reversePath));
    }
}
