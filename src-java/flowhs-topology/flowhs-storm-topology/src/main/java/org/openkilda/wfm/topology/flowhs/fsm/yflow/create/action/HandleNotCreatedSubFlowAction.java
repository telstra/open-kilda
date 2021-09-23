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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.create.action;

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class HandleNotCreatedSubFlowAction extends NbTrackableAction<YFlowCreateFsm, State, Event, YFlowCreateContext> {
    public HandleNotCreatedSubFlowAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, YFlowCreateContext context,
                                                    YFlowCreateFsm stateMachine) {
        String flowId = context.getSubFlowId();

        if (stateMachine.isFailedSubFlow(flowId)) {
            log.debug("Ignore an error event for already failed sub-flow " + flowId);

            return Optional.empty();
        }

        if (!stateMachine.isPendingSubFlow(flowId)) {
            throw new IllegalStateException("Received an event for non-pending sub-flow " + flowId);
        }

        stateMachine.saveErrorToHistory("Failed to create sub-flow",
                format("Failed to create sub-flow %s of y-flow %s: %s", flowId, stateMachine.getYFlowId(),
                        context.getError()));

        // Send only a single error response on the first occurrence.
        if (stateMachine.getFailedSubFlows().isEmpty()) {
            Message message = buildErrorMessage(stateMachine, context.getErrorType(), getGenericErrorMessage(),
                    context.getError());
            stateMachine.sendNorthboundResponse(message);
        }

        stateMachine.addFailedSubFlow(flowId);
        stateMachine.removePendingSubFlow(flowId);

        if (stateMachine.getPendingSubFlows().isEmpty()) {
            stateMachine.fire(Event.FAILED_TO_CREATE_SUB_FLOWS);
        }

        return Optional.empty();
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not create y-flow";
    }
}
