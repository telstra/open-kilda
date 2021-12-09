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
public class RemoveSubFlowsAction extends YFlowProcessingAction<YFlowDeleteFsm, State, Event, YFlowDeleteContext> {
    private final FlowDeleteService flowDeleteService;

    public RemoveSubFlowsAction(PersistenceManager persistenceManager, FlowDeleteService flowDeleteService) {
        super(persistenceManager);
        this.flowDeleteService = flowDeleteService;
    }

    @Override
    public void perform(State from, State to, Event event, YFlowDeleteContext context, YFlowDeleteFsm stateMachine) {
        String yFlowId = stateMachine.getYFlowId();
        YFlow yFlow = getYFlow(yFlowId);
        log.debug("Start removing 1 of {} sub-flows of y-flow {}", yFlow.getSubFlows().size(), yFlowId);
        stateMachine.clearDeletingSubFlows();

        // TODO: refactor to concurrent deleting once https://github.com/telstra/open-kilda/issues/3411 is fixed.
        yFlow.getSubFlows().stream()
                .findFirst()
                .ifPresent(subFlow -> {
                    String subFlowId = subFlow.getSubFlowId();
                    stateMachine.addSubFlow(subFlowId);
                    stateMachine.addDeletingSubFlow(subFlowId);
                    stateMachine.notifyEventListeners(listener ->
                            listener.onSubFlowProcessingStart(yFlowId, subFlowId));
                    CommandContext flowContext = stateMachine.getCommandContext().fork(subFlowId);
                    flowDeleteService.startFlowDeletion(flowContext, subFlowId);
                });
    }
}
