/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.sync.actions;

import static java.lang.String.format;

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.exceptions.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.sync.FlowSyncContext;
import org.openkilda.wfm.topology.flowhs.fsm.sync.FlowSyncFsm;
import org.openkilda.wfm.topology.flowhs.fsm.sync.FlowSyncFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.sync.FlowSyncFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FlowSyncSetupAction
        extends FlowProcessingWithHistorySupportAction<FlowSyncFsm, State, Event, FlowSyncContext> {
    private final FlowOperationsDashboardLogger dashboardLogger;

    public FlowSyncSetupAction(PersistenceManager persistenceManager, FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected void perform(State from, State to, Event event, FlowSyncContext context, FlowSyncFsm stateMachine) {
        try {
            transactionManager.doInTransaction(() -> transaction(stateMachine));
            stateMachine.fireNext(context);
        } catch (FlowProcessingException e) {
            FlowSyncContext errorContext = FlowSyncContext.builder()
                    .errorType(e.getErrorType())
                    .errorDetails(e.getMessage())
                    .build();
            stateMachine.fireError(errorContext);
        }
    }


    private void transaction(FlowSyncFsm stateMachine) {
        Flow flow = getFlow(stateMachine.getFlowId());

        dashboardLogger.onFlowSync(stateMachine.getFlowId());
        stateMachine.saveNewEventToHistory(
                "Started flow paths sync", FlowEventData.Event.SYNC, FlowEventData.Initiator.NB,
                "Performing flow paths sync operation on NB request");

        ensureNoCollision(flow);

        flow.setStatus(FlowStatus.IN_PROGRESS);
        stateMachine.setDangerousSync(applyFlowPostponedChanges(flow));
    }

    private void ensureNoCollision(Flow flow) {
        if (flow.getStatus() == FlowStatus.IN_PROGRESS) {
            String message = format("Flow %s is in progress now", flow.getFlowId());
            throw new FlowProcessingException(ErrorType.REQUEST_INVALID, message);
        }
    }

    private boolean applyFlowPostponedChanges(Flow flow) {
        if (flow.getTargetPathComputationStrategy() != null) {
            log.warn(
                    "Changing flow \"{}\" path computation strategy from {} to {}, because of this the SYNC operation "
                            + "become UNSAFE (client traffic during path update can be interrupted, flow can become "
                            + "corrupted if any path segment will not be installed)",
                    flow.getFlowId(), flow.getPathComputationStrategy(), flow.getTargetPathComputationStrategy());
            flow.setPathComputationStrategy(flow.getTargetPathComputationStrategy());
            flow.setTargetPathComputationStrategy(null);
            return true;
        }
        return false;
    }
}
