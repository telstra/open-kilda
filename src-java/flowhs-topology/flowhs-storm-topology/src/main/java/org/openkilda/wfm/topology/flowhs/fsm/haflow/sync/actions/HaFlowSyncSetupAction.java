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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.sync.actions;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.HaFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.sync.HaFlowSyncContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.sync.HaFlowSyncFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.sync.HaFlowSyncFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.sync.HaFlowSyncFsm.State;
import org.openkilda.wfm.topology.flowhs.validation.HaFlowValidator;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class HaFlowSyncSetupAction extends
        NbTrackableWithHistorySupportAction<HaFlowSyncFsm, State, Event, HaFlowSyncContext> {
    private final HaFlowValidator haFlowValidator;
    private final FlowOperationsDashboardLogger dashboardLogger;

    public HaFlowSyncSetupAction(PersistenceManager persistenceManager, FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);
        this.haFlowValidator = new HaFlowValidator(persistenceManager);
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected Optional<Message> performWithResponse(
            State from, State to, Event event, HaFlowSyncContext context, HaFlowSyncFsm stateMachine)
            throws FlowProcessingException {
        transactionManager.doInTransaction(() -> transaction(stateMachine));
        stateMachine.saveNewEventToHistory("HA-flow was validated successfully", FlowEventData.Event.CREATE);
        return Optional.empty();
    }

    protected void transaction(HaFlowSyncFsm stateMachine) {
        HaFlow haFlow = getHaFlow(stateMachine.getHaFlowId());
        dashboardLogger.onHaFlowSync(haFlow.getHaFlowId());
        // TODO save history https://github.com/telstra/open-kilda/issues/5169
        // stateMachine.saveNewEventToHistory(
        //         "Started HA-flow paths sync", FlowEventData.Event.SYNC, FlowEventData.Initiator.NB,
        //         "Performing HA-flow paths sync operation on NB request");

        haFlowValidator.validateHaFlowStatusIsNotInProgress(haFlow);
        haFlow.setStatus(FlowStatus.IN_PROGRESS);
        haFlow.getHaSubFlows().forEach(subFlow -> subFlow.setStatus(FlowStatus.IN_PROGRESS));
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not sync HA-flow";
    }

    @Override
    protected void handleError(HaFlowSyncFsm stateMachine, Exception ex, ErrorType errorType, boolean logTraceback) {
        super.handleError(stateMachine, ex, errorType, logTraceback);

        // Notify about failed validation.
        stateMachine.notifyEventListeners(listener ->
                listener.onFailed(stateMachine.getHaFlowId(), stateMachine.getErrorReason(), errorType));
    }
}
