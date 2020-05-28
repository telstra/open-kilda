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
import org.openkilda.persistence.FetchStrategy;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.EncapsulationResources;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResources.PathResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class SwapFlowPathsAction extends FlowProcessingAction<FlowUpdateFsm, State, Event, FlowUpdateContext> {
    private final FlowResourcesManager resourcesManager;

    public SwapFlowPathsAction(PersistenceManager persistenceManager, FlowResourcesManager resourcesManager) {
        super(persistenceManager);
        this.resourcesManager = resourcesManager;
    }

    @Override
    protected void perform(State from, State to, Event event, FlowUpdateContext context, FlowUpdateFsm stateMachine) {
        persistenceManager.getTransactionManager().doInTransaction(() -> {
            Flow flow = getFlow(stateMachine.getFlowId(), FetchStrategy.DIRECT_RELATIONS);

            Optional<FlowPath> oldPrimaryForward = flow.getPath(flow.getForwardPathId());
            oldPrimaryForward.ifPresent(path -> {
                stateMachine.setOldPrimaryForwardPath(path.getPathId());
                stateMachine.setOldPrimaryForwardPathStatus(path.getStatus());
                flowPathRepository.updateStatus(path.getPathId(), FlowPathStatus.IN_PROGRESS);
            });

            Optional<FlowPath> oldPrimaryReverse = flow.getPath(flow.getReversePathId());
            oldPrimaryReverse.ifPresent(path -> {
                stateMachine.setOldPrimaryReversePath(path.getPathId());
                stateMachine.setOldPrimaryReversePathStatus(path.getStatus());
                flowPathRepository.updateStatus(path.getPathId(), FlowPathStatus.IN_PROGRESS);
            });

            FlowEncapsulationType oldFlowEncapsulationType = stateMachine.getOriginalFlow().getFlowEncapsulationType();

            if (oldPrimaryForward.isPresent() || oldPrimaryReverse.isPresent()) {
                FlowResources oldPrimaryResources = getResources(
                        oldPrimaryForward.orElseGet(oldPrimaryReverse::get),
                        oldPrimaryReverse.orElseGet(oldPrimaryForward::get),
                        oldFlowEncapsulationType);
                stateMachine.getOldResources().add(oldPrimaryResources);
            }

            PathId newPrimaryForward = stateMachine.getNewPrimaryForwardPath();
            flow.setForwardPath(newPrimaryForward);
            PathId newPrimaryReverse = stateMachine.getNewPrimaryReversePath();
            flow.setReversePath(newPrimaryReverse);

            log.debug("Swapping the primary paths {}/{} with {}/{}",
                    oldPrimaryForward.map(FlowPath::getPathId).orElse(null),
                    oldPrimaryReverse.map(FlowPath::getPathId).orElse(null),
                    newPrimaryForward, newPrimaryReverse);

            saveHistory(stateMachine, flow.getFlowId(), newPrimaryForward, newPrimaryReverse);

            Optional<FlowPath> oldProtectedForward = flow.getPath(flow.getProtectedForwardPathId());
            oldProtectedForward.ifPresent(path -> {
                stateMachine.setOldProtectedForwardPath(path.getPathId());
                stateMachine.setOldProtectedForwardPathStatus(path.getStatus());
                flowPathRepository.updateStatus(path.getPathId(), FlowPathStatus.IN_PROGRESS);
            });

            Optional<FlowPath> oldProtectedReverse = flow.getPath(flow.getProtectedReversePathId());
            oldProtectedReverse.ifPresent(path -> {
                stateMachine.setOldProtectedReversePath(path.getPathId());
                stateMachine.setOldProtectedReversePathStatus(path.getStatus());
                flowPathRepository.updateStatus(path.getPathId(), FlowPathStatus.IN_PROGRESS);
            });

            if (oldProtectedForward.isPresent() || oldProtectedReverse.isPresent()) {
                FlowResources oldProtectedResources = getResources(
                        oldProtectedForward.orElseGet(oldProtectedReverse::get),
                        oldProtectedReverse.orElseGet(oldProtectedForward::get),
                        oldFlowEncapsulationType);
                stateMachine.getOldResources().add(oldProtectedResources);
            }

            PathId newProtectedForward = stateMachine.getNewProtectedForwardPath();
            PathId newProtectedReverse = stateMachine.getNewProtectedReversePath();
            if (newProtectedForward != null && newProtectedReverse != null) {
                flow.setProtectedForwardPath(newProtectedForward);
                flow.setProtectedReversePath(newProtectedReverse);

                log.debug("Swapping the protected paths {}/{} with {}/{}",
                        flow.getProtectedForwardPathId(), flow.getProtectedReversePathId(),
                        newProtectedForward, newProtectedReverse);

                saveHistory(stateMachine, flow.getFlowId(), newProtectedForward, newProtectedReverse);
            } else {
                flow.setProtectedForwardPath((FlowPath) null);
                flow.setProtectedReversePath((FlowPath) null);
            }

            flowRepository.createOrUpdate(flow);
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
