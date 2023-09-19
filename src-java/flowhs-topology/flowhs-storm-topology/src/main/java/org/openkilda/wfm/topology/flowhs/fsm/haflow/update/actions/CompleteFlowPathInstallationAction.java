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

import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.HaPathIdsPair;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.State;
import org.openkilda.wfm.topology.flowhs.service.history.FlowHistoryService;
import org.openkilda.wfm.topology.flowhs.service.history.HaFlowHistory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CompleteFlowPathInstallationAction extends
        FlowProcessingWithHistorySupportAction<HaFlowUpdateFsm, State, Event, HaFlowUpdateContext> {
    public CompleteFlowPathInstallationAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(
            State from, State to, Event event, HaFlowUpdateContext context, HaFlowUpdateFsm stateMachine) {
        PathId newPrimaryForwardPathId = stateMachine.getNewPrimaryPathIds().getForward().getHaPathId();
        PathId newPrimaryReversePathId = stateMachine.getNewPrimaryPathIds().getReverse().getHaPathId();

        log.debug("Completing installation of the HA-flow primary path {} / {}",
                newPrimaryForwardPathId, newPrimaryReversePathId);

        updatePathStatuses(stateMachine.getNewPrimaryPathIds(), stateMachine);

        FlowHistoryService.using(stateMachine.getCarrier()).save(HaFlowHistory
                .of(stateMachine.getCommandContext().getCorrelationId())
                .withAction("HA-flow paths have been installed")
                .withDescription(format("The HA-flow paths %s / %s have been installed",
                        newPrimaryForwardPathId, newPrimaryReversePathId))
                .withHaFlowId(stateMachine.getHaFlowId()));

        if (stateMachine.getNewProtectedPathIds() != null) {
            PathId newProtectedForwardPathId = stateMachine.getNewProtectedPathIds().getForward().getHaPathId();
            PathId newProtectedReversePathId = stateMachine.getNewProtectedPathIds().getReverse().getHaPathId();

            log.debug("Completing installation of the HA-flow protected path {} / {}",
                    newProtectedForwardPathId, newProtectedReversePathId);
            updatePathStatuses(stateMachine.getNewProtectedPathIds(), stateMachine);

            FlowHistoryService.using(stateMachine.getCarrier()).save(HaFlowHistory
                    .of(stateMachine.getCommandContext().getCorrelationId())
                    .withAction("HA-flow paths have been installed")
                    .withDescription(format("The HA-flow paths %s / %s have been installed",
                            newProtectedForwardPathId, newProtectedReversePathId))
                    .withHaFlowId(stateMachine.getHaFlowId()));
        }
    }

    private void updatePathStatuses(HaPathIdsPair haPathIds, HaFlowUpdateFsm stateMachine) {
        transactionManager.doInTransaction(() -> {
            for (PathId newSubPathId : haPathIds.getAllSubPathIds()) {
                flowPathRepository.updateStatus(newSubPathId, stateMachine.getNewPathStatus(newSubPathId));
            }
            for (PathId newHaFlowPathId : haPathIds.getAllHaFlowPathIds()) {
                haFlowPathRepository.updateStatus(newHaFlowPathId, stateMachine.getNewPathStatus(newHaFlowPathId));
            }
        });
    }
}
