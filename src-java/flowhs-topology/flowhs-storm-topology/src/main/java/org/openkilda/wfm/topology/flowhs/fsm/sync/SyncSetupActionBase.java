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

package org.openkilda.wfm.topology.flowhs.fsm.sync;

import static java.lang.String.format;

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;

import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public abstract class SyncSetupActionBase<F extends SyncFsmBase<F, S, E>, S, E, T>
        extends FlowProcessingWithHistorySupportAction<F, S, E, FlowSyncContext> {

    protected final FlowOperationsDashboardLogger dashboardLogger;

    public SyncSetupActionBase(PersistenceManager persistenceManager, FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected void perform(S from, S to, E event, FlowSyncContext context, F stateMachine) {
        try {
            transactionManager.doInTransaction(() -> transaction(stateMachine, loadContainer(stateMachine)));
            stateMachine.fireNext(context);
        } catch (FlowProcessingException e) {
            FlowSyncContext errorContext = FlowSyncContext.builder()
                    .errorType(e.getErrorType())
                    .errorDetails(e.getMessage())
                    .build();
            stateMachine.fireError(errorContext);
        }
    }

    protected void transaction(F stateMachine, T container) {
        boolean isPotentiallyDestructiveSync = false;
        Set<String> targets = new HashSet<>();
        for (Flow flow : collectAffectedFlows(container)) {
            dashboardLogger.onFlowSync(flow.getFlowId());
            stateMachine.saveNewEventToHistory(
                    "Started flow paths sync", FlowEventData.Event.SYNC, FlowEventData.Initiator.NB,
                    "Performing flow paths sync operation on NB request");

            ensureNoCollision(flow);

            targets.add(flow.getFlowId());
            flow.setStatus(FlowStatus.IN_PROGRESS);
            isPotentiallyDestructiveSync = isPotentiallyDestructiveSync || applyFlowPostponedChanges(flow);
        }

        stateMachine.getTargets().addAll(targets);
        stateMachine.setDangerousSync(isPotentiallyDestructiveSync);
    }

    protected abstract T loadContainer(F stateMachine);

    protected abstract List<Flow> collectAffectedFlows(T container);

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
