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

package org.openkilda.wfm.topology.flowhs.fsm.update.actions;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class PostResourceAllocationAction extends
        NbTrackableAction<FlowUpdateFsm, State, Event, FlowUpdateContext> {
    public PostResourceAllocationAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, FlowUpdateContext context,
                                                    FlowUpdateFsm stateMachine) {
        String flowId = stateMachine.getFlowId();

        Flow flow = getFlow(flowId);
        Message message = buildResponseMessage(flow, stateMachine.getCommandContext());
        stateMachine.setOperationResultMessage(message);

        // Notify about successful allocation.
        stateMachine.notifyEventListeners(listener -> listener.onResourcesAllocated(flowId));

        return Optional.of(message);
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not update flow";
    }

    @Override
    protected void handleError(FlowUpdateFsm stateMachine, Exception ex, ErrorType errorType, boolean logTraceback) {
        super.handleError(stateMachine, ex, errorType, logTraceback);

        // Notify about failed allocation.
        stateMachine.notifyEventListenersOnError(errorType, stateMachine.getErrorReason());

    }
}
