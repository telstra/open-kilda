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

import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.create.YFlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.mapper.YFlowRequestMapper;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;
import org.openkilda.wfm.topology.flowhs.service.FlowCreateService;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;

@Slf4j
public class CreateSubFlowsAction extends HistoryRecordingAction<YFlowCreateFsm, State, Event, YFlowCreateContext> {
    private final FlowCreateService flowCreateService;

    public CreateSubFlowsAction(FlowCreateService flowCreateService) {
        this.flowCreateService = flowCreateService;
    }

    @Override
    public void perform(State from, State to, Event event, YFlowCreateContext context, YFlowCreateFsm stateMachine) {
        String yFlowId = stateMachine.getYFlowId();
        Collection<RequestedFlow> requestedFlows =
                YFlowRequestMapper.INSTANCE.toRequestedFlows(stateMachine.getTargetFlow());
        log.debug("Start creating {} sub-flows for y-flow {}", requestedFlows.size(), yFlowId);
        stateMachine.clearCreatingSubFlows();

        requestedFlows.forEach(requestedFlow -> {
            String subFlowId = requestedFlow.getFlowId();
            stateMachine.addSubFlow(subFlowId);
            stateMachine.addCreatingSubFlow(subFlowId);
            stateMachine.notifyEventListeners(listener -> listener.onSubFlowProcessingStart(yFlowId, subFlowId));
            CommandContext flowContext = stateMachine.getCommandContext().fork(subFlowId);
            flowCreateService.startFlowCreation(flowContext, requestedFlow, false);
        });
    }
}
