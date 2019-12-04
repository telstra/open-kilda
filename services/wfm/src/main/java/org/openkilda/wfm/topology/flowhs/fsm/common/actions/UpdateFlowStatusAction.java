/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions;

import static java.lang.String.format;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowStatus;
import org.openkilda.persistence.FetchStrategy;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowPathSwappingFsm;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UpdateFlowStatusAction<T extends FlowPathSwappingFsm<T, S, E, C>, S, E, C>
        extends FlowProcessingAction<T, S, E, C> {
    private final FlowOperationsDashboardLogger dashboardLogger;

    public UpdateFlowStatusAction(PersistenceManager persistenceManager,
                                  FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected void perform(S from, S to, E event, C context, T stateMachine) {
        FlowStatus resultStatus = persistenceManager.getTransactionManager().doInTransaction(() -> {
            String flowId = stateMachine.getFlowId();
            Flow flow = getFlow(flowId, FetchStrategy.DIRECT_RELATIONS);
            FlowStatus flowStatus = flow.computeFlowStatus();
            if (flowStatus != flow.getStatus()) {
                dashboardLogger.onFlowStatusUpdate(flowId, flowStatus);
                flowRepository.updateStatus(flowId, flowStatus);
            }
            stateMachine.setNewFlowStatus(flowStatus);
            return flowStatus;
        });

        stateMachine.saveActionToHistory(format("The flow status was set to %s", resultStatus));
    }
}
