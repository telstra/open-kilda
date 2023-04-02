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

import org.openkilda.model.Flow;
import org.openkilda.model.FlowStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class SuccessCompleteActionBase<F extends SyncFsmBase<F, S, E>, S, E>
        extends FlowProcessingWithHistorySupportAction<F, S, E, FlowSyncContext> {

    protected final FlowOperationsDashboardLogger dashboardLogger;

    public SuccessCompleteActionBase(
            @NonNull PersistenceManager persistenceManager, @NonNull FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);

        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected void perform(S from, S to, E event, FlowSyncContext context, F stateMachine) {
        updateStatus(stateMachine);
        stateMachine.fireNext(context);
    }

    protected abstract void updateStatus(F stateMachine);

    protected boolean updateFlowStatus(Flow flow) {
        FlowStatus status = flow.computeFlowStatus();
        flow.setStatus(status);
        if (status == FlowStatus.UP) {
            flow.setStatusInfo(null);
        } else if (status == FlowStatus.DEGRADED) {
            flow.setStatusInfo(String.format(
                    "Final flow %s status after SYNC operation is %s", flow.getFlowId(), status));
        } else {
            // error reporting action will fill all required info
            return false;
        }

        // final flow status is within expected boundaries
        return true;
    }
}
