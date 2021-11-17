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

import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OnSubFlowReroutedAction extends
        HistoryRecordingAction<YFlowRerouteFsm, State, Event, YFlowRerouteContext> {
    @Override
    protected void perform(State from, State to, Event event,
                           YFlowRerouteContext context, YFlowRerouteFsm stateMachine) {
        String flowId = context.getSubFlowId();
        if (!stateMachine.isReroutingSubFlow(flowId)) {
            throw new IllegalStateException("Received an event for non-pending sub-flow " + flowId);
        }

        stateMachine.saveActionToHistory("Rerouted a sub-flow",
                format("Rerouted sub-flow %s of y-flow %s", flowId, stateMachine.getYFlowId()));

        stateMachine.removeReroutingSubFlow(flowId);

        if (stateMachine.getReroutingSubFlows().isEmpty()) {
            if (stateMachine.getFailedSubFlows().isEmpty()) {
                stateMachine.fire(Event.ALL_SUB_FLOWS_REROUTED);
            } else {
                stateMachine.fire(Event.FAILED_TO_REROUTE_SUB_FLOWS);
                stateMachine.setErrorReason(format("Failed to reroute sub-flows %s of y-flow %s",
                        stateMachine.getFailedSubFlows(), stateMachine.getYFlowId()));
            }
        }
    }
}
