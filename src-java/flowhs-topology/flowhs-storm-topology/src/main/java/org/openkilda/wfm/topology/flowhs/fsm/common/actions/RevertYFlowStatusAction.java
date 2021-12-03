/* Copyright 2021 Telstra Open Source
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

import org.openkilda.model.FlowStatus;
import org.openkilda.model.YFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.common.YFlowProcessingFsm;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RevertYFlowStatusAction<T extends YFlowProcessingFsm<T, S, E, C, ?, ?>, S, E, C>
        extends YFlowProcessingAction<T, S, E, C>  {
    private final FlowOperationsDashboardLogger dashboardLogger;

    public RevertYFlowStatusAction(PersistenceManager persistenceManager,
                                   FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected void perform(S from, S to, E event, C context, T stateMachine) {
        String yFlowId = stateMachine.getYFlowId();
        FlowStatus originalStatus = stateMachine.getOriginalYFlowStatus();
        if (originalStatus != null) {
            log.debug("Reverting the y-flow status of {} to {}", yFlowId, originalStatus);

            transactionManager.doInTransaction(() -> {
                YFlow flow = getYFlow(yFlowId);
                flow.setStatus(originalStatus);
            });

            dashboardLogger.onYFlowStatusUpdate(yFlowId, originalStatus);
            stateMachine.saveActionToHistory(format("The y-flow status was reverted to %s", originalStatus));
        }
    }
}
