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

package org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.actions;

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OnFinishedWithErrorAction
        extends FlowProcessingAction<FlowMirrorPointDeleteFsm, State, Event, FlowMirrorPointDeleteContext> {
    private final FlowOperationsDashboardLogger dashboardLogger;

    public OnFinishedWithErrorAction(PersistenceManager persistenceManager,
                                     FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    public void perform(State from, State to, Event event, FlowMirrorPointDeleteContext context,
                        FlowMirrorPointDeleteFsm stateMachine) {
        if (stateMachine.getFlowStatus() != null) {
            flowRepository.updateStatus(stateMachine.getFlowId(), stateMachine.getFlowStatus());
        }

        dashboardLogger.onFailedFlowMirrorPointDelete(stateMachine.getFlowId(), stateMachine.getMirrorPathId().getId(),
                stateMachine.getErrorReason());
        stateMachine.saveActionToHistory("Failed to delete the flow mirror point", stateMachine.getErrorReason());
    }
}
