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

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.State;
import org.openkilda.wfm.topology.flowhs.service.common.HistoryUpdateCarrier;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CompleteFlowPathRemovalAction extends
        BaseHaFlowPathRemovalAction<HaFlowUpdateFsm, State, Event, HaFlowUpdateContext> {

    public CompleteFlowPathRemovalAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(
            State from, State to, Event event, HaFlowUpdateContext context, HaFlowUpdateFsm stateMachine) {
        HistoryUpdateCarrier carrier = stateMachine.getCarrier();
        String correlationId = stateMachine.getCommandContext().getCorrelationId();

        removeFlowPaths(stateMachine.getOldPrimaryPathIds(), carrier, correlationId);
        removeFlowPaths(stateMachine.getOldProtectedPathIds(), carrier, correlationId);
        removeRejectedPaths(stateMachine.getRejectedSubPathsIds(), stateMachine.getRejectedHaPathsIds(),
                carrier, correlationId);
    }
}
