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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.update.actions;

import static java.lang.String.format;

import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm.State;
import org.openkilda.wfm.topology.flowhs.service.FlowUpdateService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OnSubFlowUpdatedAction extends
        HistoryRecordingAction<YFlowUpdateFsm, State, Event, YFlowUpdateContext> {
    private final FlowUpdateService flowUpdateService;

    public OnSubFlowUpdatedAction(FlowUpdateService flowUpdateService) {
        this.flowUpdateService = flowUpdateService;
    }

    @Override
    protected void perform(State from, State to, Event event, YFlowUpdateContext context, YFlowUpdateFsm stateMachine) {
        String subFlowId = context.getSubFlowId();
        if (!stateMachine.isUpdatingSubFlow(subFlowId)) {
            throw new IllegalStateException("Received an event for non-pending sub-flow " + subFlowId);
        }

        stateMachine.saveActionToHistory("Updated a sub-flow",
                format("Updated sub-flow %s of y-flow %s", subFlowId, stateMachine.getYFlowId()));

        stateMachine.removeUpdatingSubFlow(subFlowId);
        stateMachine.notifyEventListeners(listener ->
                listener.onSubFlowProcessingFinished(stateMachine.getYFlowId(), subFlowId));

        String yFlowId = stateMachine.getYFlowId();
        if (subFlowId.equals(stateMachine.getMainAffinityFlowId())) {
            stateMachine.getRequestedFlows().forEach(requestedFlow -> {
                String requestedFlowId = requestedFlow.getFlowId();
                if (!requestedFlowId.equals(subFlowId)) {
                    stateMachine.addUpdatingSubFlow(requestedFlowId);
                    stateMachine.notifyEventListeners(listener ->
                            listener.onSubFlowProcessingStart(yFlowId, requestedFlowId));
                    CommandContext flowContext = stateMachine.getCommandContext().fork(requestedFlowId);
                    requestedFlow.setDiverseFlowId(stateMachine.getDiverseFlowId());
                    flowUpdateService.startFlowUpdating(flowContext, requestedFlow, yFlowId);
                }
            });
        }

        if (stateMachine.getUpdatingSubFlows().isEmpty()) {
            if (stateMachine.getFailedSubFlows().isEmpty()) {
                stateMachine.fire(Event.ALL_SUB_FLOWS_UPDATED);
            } else {
                stateMachine.fire(Event.FAILED_TO_UPDATE_SUB_FLOWS);
            }
        }
    }
}
