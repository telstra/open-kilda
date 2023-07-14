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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.actions;

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.HaFlowPathSwapContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.HaFlowPathSwapFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.HaFlowPathSwapFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.HaFlowPathSwapFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class HaFlowValidateAction extends
        NbTrackableWithHistorySupportAction<HaFlowPathSwapFsm, State, Event, HaFlowPathSwapContext> {
    private final FlowOperationsDashboardLogger dashboardLogger;

    public HaFlowValidateAction(PersistenceManager persistenceManager, FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, HaFlowPathSwapContext context,
                                                    HaFlowPathSwapFsm stateMachine) throws FlowProcessingException {
        transactionManager.doInTransaction(() -> {
            String haFlowId = stateMachine.getHaFlowId();
            HaFlow haFlow = haFlowRepository.findById(haFlowId)
                    .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                            format("Could not swap paths: HA-flow %s not found", haFlowId)));
            dashboardLogger.onHaFlowPathsSwap(haFlowId);
            stateMachine.setPeriodicPingsEnabled(haFlow.isPeriodicPings());
            if (haFlow.getStatus() == FlowStatus.IN_PROGRESS) {
                throw new FlowProcessingException(ErrorType.REQUEST_INVALID,
                        format("HA-flow %s is in progress now", haFlowId));
            }
            if (!haFlow.isAllocateProtectedPath()
                    || haFlow.getProtectedForwardPath() == null || haFlow.getProtectedReversePath() == null) {
                throw new FlowProcessingException(ErrorType.REQUEST_INVALID,
                        format("Could not swap paths: HA-flow %s doesn't have protected path", haFlowId));
            }
            if (FlowPathStatus.ACTIVE != haFlow.getProtectedForwardPath().getStatus()
                    || FlowPathStatus.ACTIVE != haFlow.getProtectedReversePath().getStatus()) {
                throw new FlowProcessingException(ErrorType.REQUEST_INVALID,
                        format("Could not swap paths: Protected path of HA-flow %s is not in ACTIVE state", haFlowId));
            }
            haFlow.setStatus(FlowStatus.IN_PROGRESS);
            haFlow.getHaSubFlows().forEach(subFlow -> subFlow.setStatus(FlowStatus.IN_PROGRESS));
            saveAndSetInProgressStatuses(haFlow.getForwardPath(), stateMachine);
            saveAndSetInProgressStatuses(haFlow.getReversePath(), stateMachine);
            saveAndSetInProgressStatuses(haFlow.getProtectedForwardPath(), stateMachine);
            saveAndSetInProgressStatuses(haFlow.getProtectedReversePath(), stateMachine);
        });
        stateMachine.saveNewEventToHistory("HA-flow was validated successfully", FlowEventData.Event.PATH_SWAP);
        return Optional.empty();
    }

    private static void saveAndSetInProgressStatuses(HaFlowPath haFlowPath, HaFlowPathSwapFsm stateMachine) {
        stateMachine.setOldPathStatuses(haFlowPath);

        haFlowPath.setStatus(FlowPathStatus.IN_PROGRESS);
        for (FlowPath subPath : haFlowPath.getSubPaths()) {
            subPath.setStatus(FlowPathStatus.IN_PROGRESS);
        }
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not swap paths for flow";
    }
}
