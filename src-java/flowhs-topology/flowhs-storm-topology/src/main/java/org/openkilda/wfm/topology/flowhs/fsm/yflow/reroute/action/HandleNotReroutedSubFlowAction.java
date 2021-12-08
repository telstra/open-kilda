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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.action;

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class HandleNotReroutedSubFlowAction
        extends NbTrackableAction<YFlowRerouteFsm, State, Event, YFlowRerouteContext> {
    public HandleNotReroutedSubFlowAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, YFlowRerouteContext context,
                                                    YFlowRerouteFsm stateMachine) {
        String subFlowId = context.getSubFlowId();

        if (stateMachine.isFailedSubFlow(subFlowId)) {
            log.debug("Ignore an error event for already failed sub-flow " + subFlowId);

            return Optional.empty();
        }

        if (!stateMachine.isReroutingSubFlow(subFlowId)) {
            throw new IllegalStateException("Received an event for non-pending sub-flow " + subFlowId);
        }

        stateMachine.saveErrorToHistory("Failed to reroute sub-flow",
                format("Failed to reroute sub-flow %s of y-flow %s: %s", subFlowId, stateMachine.getYFlowId(),
                        context.getError()));

        // Send only a single error response on the first occurrence.
        final boolean isFirstError = stateMachine.getFailedSubFlows().isEmpty();

        stateMachine.addFailedSubFlow(subFlowId);
        stateMachine.removeReroutingSubFlow(subFlowId);
        stateMachine.notifyEventListeners(listener ->
                listener.onSubFlowProcessingFinished(stateMachine.getYFlowId(), subFlowId));

        if (stateMachine.getReroutingSubFlows().isEmpty()) {
            stateMachine.fire(Event.FAILED_TO_REROUTE_SUB_FLOWS);
            stateMachine.setErrorReason(format("Failed to reroute sub-flows %s of y-flow %s",
                    stateMachine.getFailedSubFlows(), stateMachine.getYFlowId()));
        }

        if (isFirstError) {
            Message message = buildErrorMessage(stateMachine, context.getErrorType(), getGenericErrorMessage(),
                    context.getError());
            return Optional.of(message);
        }

        return Optional.empty();
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not reroute y-flow";
    }
}
