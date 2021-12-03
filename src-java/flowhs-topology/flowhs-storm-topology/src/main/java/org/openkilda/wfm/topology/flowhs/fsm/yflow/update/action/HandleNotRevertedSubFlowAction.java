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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.update.action;

import static java.lang.String.format;

import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm.State;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.AnonymousAction;

@Slf4j
public class HandleNotRevertedSubFlowAction extends AnonymousAction<YFlowUpdateFsm, State, Event, YFlowUpdateContext> {

    @Override
    public void execute(State from, State to, Event event, YFlowUpdateContext context, YFlowUpdateFsm stateMachine) {
        String subFlowId = context.getSubFlowId();

        if (stateMachine.isFailedSubFlow(subFlowId)) {
            log.debug("Ignore an error event for already failed sub-flow " + subFlowId);
            return;
        }

        if (!stateMachine.isUpdatingSubFlow(subFlowId)) {
            throw new IllegalStateException("Received an event for non-pending sub-flow " + subFlowId);
        }

        stateMachine.saveErrorToHistory("Failed to revert sub-flow",
                format("Failed to revert sub-flow %s of y-flow %s: %s", subFlowId, stateMachine.getYFlowId(),
                        context.getError()));

        stateMachine.addFailedSubFlow(subFlowId);
        stateMachine.removeUpdatingSubFlow(subFlowId);
        stateMachine.notifyEventListeners(listener ->
                listener.onSubFlowProcessingFinished(stateMachine.getYFlowId(), subFlowId));

        if (stateMachine.getUpdatingSubFlows().isEmpty()) {
            stateMachine.fire(Event.FAILED_TO_UPDATE_SUB_FLOWS);
        }
    }
}
