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

import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HandleNotRemovedSubFlowAction extends
        HistoryRecordingAction<YFlowCreateFsm, State, Event, YFlowCreateContext> {
    protected void perform(State from, State to, Event event, YFlowCreateContext context, YFlowCreateFsm stateMachine) {
        String subFlowId = context.getSubFlowId();
        if (!stateMachine.isDeletingSubFlow(subFlowId)) {
            throw new IllegalStateException("Received an event for non-pending sub-flow " + subFlowId);
        }

        stateMachine.saveErrorToHistory("Failed to remove sub-flow",
                format("Failed to remove sub-flow %s of y-flow %s: %s", subFlowId, stateMachine.getYFlowId(),
                        context.getError()));

        stateMachine.removeDeletingSubFlow(subFlowId);
        stateMachine.notifyEventListeners(listener ->
                listener.onSubFlowProcessingFinished(stateMachine.getYFlowId(), subFlowId));

        if (stateMachine.getDeletingSubFlows().isEmpty()) {
            stateMachine.fire(Event.ALL_SUB_FLOWS_REMOVED);
        }
    }
}
