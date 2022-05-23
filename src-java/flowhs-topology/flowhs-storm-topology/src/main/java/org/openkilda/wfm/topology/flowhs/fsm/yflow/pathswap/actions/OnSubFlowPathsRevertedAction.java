/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.actions;

import static java.lang.String.format;

import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OnSubFlowPathsRevertedAction extends
        HistoryRecordingAction<YFlowPathSwapFsm, State, Event, YFlowPathSwapContext> {
    @Override
    protected void perform(State from, State to, Event event, YFlowPathSwapContext context,
                           YFlowPathSwapFsm stateMachine) {
        String subFlowId = context.getSubFlowId();
        if (!stateMachine.isSwappingSubFlow(subFlowId)) {
            throw new IllegalStateException("Received an event for non-pending sub-flow " + subFlowId);
        }

        stateMachine.saveActionToHistory("Paths swapped on a sub-flow",
                format("Paths swapped on sub-flow %s of y-flow %s", subFlowId, stateMachine.getYFlowId()));

        stateMachine.removeSwappingSubFlow(subFlowId);
        stateMachine.notifyEventListeners(listener ->
                listener.onSubFlowProcessingFinished(stateMachine.getYFlowId(), subFlowId));

        if (stateMachine.getSwappingSubFlows().isEmpty()) {
            if (stateMachine.getFailedSubFlows().isEmpty()) {
                stateMachine.fire(Event.ALL_SUB_FLOWS_REVERTED);
            } else {
                stateMachine.fire(Event.FAILED_TO_REVERT_SUB_FLOWS);
            }
        }
    }
}
