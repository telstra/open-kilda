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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.actions;

import static java.lang.String.format;

import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.HaPathIdsPair;
import org.openkilda.wfm.share.metrics.TimedExecution;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.service.history.FlowHistoryService;
import org.openkilda.wfm.topology.flowhs.service.history.HaFlowHistory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CompleteFlowPathInstallationAction extends
        FlowProcessingWithHistorySupportAction<HaFlowRerouteFsm, State, Event, HaFlowRerouteContext> {
    public CompleteFlowPathInstallationAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @TimedExecution("fsm.complete_ha_flow_path_install")
    @Override
    protected void perform(
            State from, State to, Event event, HaFlowRerouteContext context, HaFlowRerouteFsm stateMachine) {
        if (stateMachine.getNewPrimaryPathIds() != null) {
            PathId newForwardPathId = stateMachine.getNewPrimaryPathIds().getForward().getHaPathId();
            PathId newReversePathId = stateMachine.getNewPrimaryPathIds().getReverse().getHaPathId();

            log.debug("Completing installation of the HA-flow primary path {} / {}",
                    newForwardPathId, newReversePathId);
            updatePathStatuses(stateMachine.getNewPrimaryPathIds(), stateMachine);

            FlowHistoryService.using(stateMachine.getCarrier()).save(HaFlowHistory
                    .of(stateMachine.getCommandContext().getCorrelationId())
                    .withAction("HA-flow paths have been installed")
                    .withDescription(format("The HA-flow paths %s / %s were installed",
                            newForwardPathId, newReversePathId))
                    .withHaFlowId(stateMachine.getHaFlowId()));
        }

        if (stateMachine.getNewProtectedPathIds() != null) {
            PathId newProtectedForwardPathId = stateMachine.getNewProtectedPathIds().getForward().getHaPathId();
            PathId newProtectedReversePathId = stateMachine.getNewProtectedPathIds().getReverse().getHaPathId();

            log.debug("Completing installation of the HA-flow protected path {} / {}",
                    newProtectedForwardPathId, newProtectedReversePathId);
            updatePathStatuses(stateMachine.getNewProtectedPathIds(), stateMachine);

            FlowHistoryService.using(stateMachine.getCarrier()).save(HaFlowHistory
                    .of(stateMachine.getCommandContext().getCorrelationId())
                    .withAction("HA-flow paths have been installed")
                    .withDescription(format("The HA-flow paths %s / %s were installed",
                            newProtectedForwardPathId, newProtectedReversePathId))
                    .withHaFlowId(stateMachine.getHaFlowId()));
        }
    }

    private void updatePathStatuses(HaPathIdsPair haPathIds, HaFlowRerouteFsm stateMachine) {
        transactionManager.doInTransaction(() -> {
            for (PathId newSubPathId : haPathIds.getAllSubPathIds()) {
                flowPathRepository.updateStatus(newSubPathId, stateMachine.getNewPathStatus(
                        newSubPathId, stateMachine.isIgnoreBandwidth()));
            }
            for (PathId newHaFlowPathId : haPathIds.getAllHaFlowPathIds()) {
                haFlowPathRepository.updateStatus(newHaFlowPathId, stateMachine.getNewPathStatus(
                        newHaFlowPathId, stateMachine.isIgnoreBandwidth()));
            }
        });
    }
}
