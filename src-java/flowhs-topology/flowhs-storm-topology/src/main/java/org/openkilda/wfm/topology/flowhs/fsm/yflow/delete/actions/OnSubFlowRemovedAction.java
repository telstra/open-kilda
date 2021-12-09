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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.actions;

import static java.lang.String.format;

import org.openkilda.model.YFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.YFlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.YFlowDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.YFlowDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.YFlowDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.delete.YFlowDeleteFsm.State;
import org.openkilda.wfm.topology.flowhs.service.FlowDeleteService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OnSubFlowRemovedAction extends YFlowProcessingAction<YFlowDeleteFsm, State, Event, YFlowDeleteContext> {
    private final FlowDeleteService flowDeleteService;

    public OnSubFlowRemovedAction(PersistenceManager persistenceManager, FlowDeleteService flowDeleteService) {
        super(persistenceManager);
        this.flowDeleteService = flowDeleteService;
    }

    @Override
    protected void perform(State from, State to, Event event, YFlowDeleteContext context, YFlowDeleteFsm stateMachine) {
        String subFlowId = context.getSubFlowId();
        if (!stateMachine.isDeletingSubFlow(subFlowId)) {
            throw new IllegalStateException("Received an event for non-pending sub-flow " + subFlowId);
        }

        String yFlowId = stateMachine.getYFlowId();
        stateMachine.saveActionToHistory("Removed a sub-flow",
                format("Removed sub-flow %s of y-flow %s", subFlowId, yFlowId));

        stateMachine.removeDeletingSubFlow(subFlowId);
        stateMachine.notifyEventListeners(listener ->
                listener.onSubFlowProcessingFinished(yFlowId, subFlowId));

        // TODO: refactor to concurrent deleting once https://github.com/telstra/open-kilda/issues/3411 is fixed.
        YFlow yFlow = getYFlow(yFlowId);
        yFlow.getSubFlows().stream()
                .filter(subFlow -> !stateMachine.getSubFlows().contains(subFlow.getSubFlowId()))
                .findFirst()
                .ifPresent(subFlow -> {
                    String nextSubFlowId = subFlow.getSubFlowId();
                    stateMachine.addSubFlow(nextSubFlowId);
                    stateMachine.addDeletingSubFlow(nextSubFlowId);
                    stateMachine.notifyEventListeners(listener ->
                            listener.onSubFlowProcessingStart(yFlowId, nextSubFlowId));
                    CommandContext flowContext = stateMachine.getCommandContext().fork(nextSubFlowId);
                    flowDeleteService.startFlowDeletion(flowContext, nextSubFlowId);
                });

        if (stateMachine.getDeletingSubFlows().isEmpty()) {
            stateMachine.fire(Event.ALL_SUB_FLOWS_REMOVED);
        }
    }
}
