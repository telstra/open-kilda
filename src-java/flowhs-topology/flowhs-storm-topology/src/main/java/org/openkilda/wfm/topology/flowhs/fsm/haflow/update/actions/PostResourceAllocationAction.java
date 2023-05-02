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

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class PostResourceAllocationAction extends
        NbTrackableWithHistorySupportAction<HaFlowUpdateFsm, State, Event, HaFlowUpdateContext> {
    public PostResourceAllocationAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected Optional<Message> performWithResponse(
            State from, State to, Event event, HaFlowUpdateContext context, HaFlowUpdateFsm stateMachine) {
        String haFlowId = stateMachine.getHaFlowId();
        Message message = buildResponseMessage(getHaFlow(haFlowId), stateMachine.getCommandContext());
        return Optional.of(message);
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not update ha-flow";
    }

    @Override
    protected void handleError(HaFlowUpdateFsm stateMachine, Exception ex, ErrorType errorType, boolean logTraceback) {
        super.handleError(stateMachine, ex, errorType, logTraceback);

        // Notify about failed allocation.
        stateMachine.notifyEventListenersOnError(errorType, stateMachine.getErrorReason());
    }
}
