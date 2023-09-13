/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.update.actions;

import static java.lang.String.format;
import static org.openkilda.wfm.topology.flowhs.utils.HaFlowUtils.getPathId;

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.EncapsulationResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.HaFlowResources;
import org.openkilda.wfm.share.flow.resources.HaPathIdsPair;
import org.openkilda.wfm.share.flow.resources.HaPathIdsPair.HaFlowPathIds;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.State;
import org.openkilda.wfm.topology.flowhs.service.haflow.history.HaFlowHistory;
import org.openkilda.wfm.topology.flowhs.service.haflow.history.HaFlowHistoryService;
import org.openkilda.wfm.topology.flowhs.utils.HaFlowUtils;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class SwapFlowPathsAction extends
        FlowProcessingWithHistorySupportAction<HaFlowUpdateFsm, State, Event, HaFlowUpdateContext> {
    private final FlowResourcesManager resourcesManager;

    public SwapFlowPathsAction(PersistenceManager persistenceManager, FlowResourcesManager resourcesManager) {
        super(persistenceManager);
        this.resourcesManager = resourcesManager;
    }

    @Override
    protected void perform(
            State from, State to, Event event, HaFlowUpdateContext context, HaFlowUpdateFsm stateMachine) {
        swapPrimaryPaths(stateMachine);
        swapProtectedPaths(stateMachine);
    }

    private void swapPrimaryPaths(HaFlowUpdateFsm stateMachine) {
        log.info("swapProimryPaths started");
        PathId newForwardPathId = stateMachine.getNewPrimaryPathIds().getForward().getHaPathId();
        PathId newReversePathId = stateMachine.getNewPrimaryPathIds().getReverse().getHaPathId();
        if (newForwardPathId != null && newReversePathId != null) {
            transactionManager.doInTransaction(() -> {
                HaFlow haFlow = getHaFlow(stateMachine.getHaFlowId());

                HaFlowPath oldForward = haFlow.getForwardPath();
                if (oldForward != null) {
                    stateMachine.savePathsAndSetInProgressStatuses(oldForward);
                }

                HaFlowPath oldReverse = haFlow.getReversePath();
                if (oldReverse != null) {
                    stateMachine.savePathsAndSetInProgressStatuses(oldReverse);
                }

                if (oldForward != null || oldReverse != null) {
                    FlowEncapsulationType oldFlowEncapsulationType =
                            stateMachine.getOriginalHaFlow().getEncapsulationType();
                    HaFlowResources oldResources = buildHaResources(
                            oldForward != null ? oldForward : oldReverse,
                            oldReverse != null ? oldReverse : oldForward,
                            oldFlowEncapsulationType);
                    stateMachine.getOldResources().add(oldResources);
                }

                haFlow.setForwardPathId(newForwardPathId);
                haFlow.setReversePathId(newReversePathId);

                log.debug("Swapping the primary paths {}/{} with {}/{}",
                        getPathId(oldForward), getPathId(oldReverse), newForwardPathId, newReversePathId);
                saveHistory(stateMachine, haFlow, newForwardPathId, newReversePathId);
            });
        }
        log.info("swapProimryPaths finished");
    }

    private void swapProtectedPaths(HaFlowUpdateFsm stateMachine) {

        transactionManager.doInTransaction(() -> {
            HaFlow haFlow = getHaFlow(stateMachine.getHaFlowId());

            HaFlowPath oldForward = haFlow.getProtectedForwardPath();
            if (oldForward != null) {
                stateMachine.savePathsAndSetInProgressStatuses(oldForward);
            }

            HaFlowPath oldReverse = haFlow.getProtectedReversePath();
            if (oldReverse != null) {
                stateMachine.savePathsAndSetInProgressStatuses(oldReverse);
            }

            if (oldForward != null || oldReverse != null) {
                FlowEncapsulationType oldFlowEncapsulationType =
                        stateMachine.getOriginalHaFlow().getEncapsulationType();
                HaFlowResources oldProtectedResources = buildHaResources(
                        oldForward != null ? oldForward : oldReverse,
                        oldReverse != null ? oldReverse : oldForward,
                        oldFlowEncapsulationType);
                stateMachine.getOldResources().add(oldProtectedResources);
            }

            PathId newForward = Optional.ofNullable(stateMachine.getNewProtectedPathIds())
                    .map(HaPathIdsPair::getForward)
                    .map(HaFlowPathIds::getHaPathId)
                    .orElse(null);
            PathId newReverse = Optional.ofNullable(stateMachine.getNewProtectedPathIds())
                    .map(HaPathIdsPair::getReverse)
                    .map(HaFlowPathIds::getHaPathId)
                    .orElse(null);
            haFlow.setProtectedForwardPathId(newForward);
            haFlow.setProtectedReversePathId(newReverse);

            if (newForward != null && newReverse != null) {
                log.debug("Swapping the protected paths {}/{} with {}/{}",
                        getPathId(oldForward), getPathId(oldReverse), newForward, newReverse);
                saveHistory(stateMachine, haFlow, newForward, newReverse);
            }
        });
    }

    private HaFlowResources buildHaResources(
            HaFlowPath forwardPath, HaFlowPath reversePath, FlowEncapsulationType encapsulationType) {
        EncapsulationResources encapsulationResources = resourcesManager.getEncapsulationResources(
                forwardPath.getHaPathId(), reversePath.getHaPathId(), encapsulationType).orElse(null);
        return HaFlowUtils.buildHaResources(forwardPath, reversePath, encapsulationResources);
    }

    private void saveHistory(
            HaFlowUpdateFsm stateMachine, HaFlow haFlow, PathId forwardPathId, PathId reversePathId) {

        HaFlowHistoryService.using(stateMachine.getCarrier()).save(HaFlowHistory
                .withTaskId(stateMachine.getCommandContext().getCorrelationId())
                .withAction("HA-flow was updated with new paths")
                .withDescription(format("The HA-flow %s has been updated with paths %s / %s",
                        haFlow.getHaFlowId(), forwardPathId, reversePathId))
                .withHaFlowId(stateMachine.getHaFlowId())
                .withHaFlowDumpAfter(haFlow));
    }

}
