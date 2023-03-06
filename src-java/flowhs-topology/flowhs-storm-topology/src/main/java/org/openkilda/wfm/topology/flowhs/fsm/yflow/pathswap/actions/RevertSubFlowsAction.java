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

import org.openkilda.model.PathId;
import org.openkilda.model.YFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.YFlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapFsm.State;
import org.openkilda.wfm.topology.flowhs.service.FlowPathSwapService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RevertSubFlowsAction extends
        YFlowProcessingWithHistorySupportAction<YFlowPathSwapFsm, State, Event, YFlowPathSwapContext> {
    private final FlowPathSwapService flowPathSwapService;

    public RevertSubFlowsAction(PersistenceManager persistenceManager, FlowPathSwapService flowPathSwapService) {
        super(persistenceManager);
        this.flowPathSwapService = flowPathSwapService;
    }

    @Override
    public void perform(State from, State to, Event event, YFlowPathSwapContext context,
                        YFlowPathSwapFsm stateMachine) {
        String yFlowId = stateMachine.getYFlowId();
        YFlow yFlow = getYFlow(yFlowId);
        log.debug("Start reverting paths on {} sub-flows of y-flow {}", yFlow.getSubFlows().size(), yFlowId);
        stateMachine.clearSwappingSubFlows();
        stateMachine.clearFailedSubFlows();

        yFlow.getSubFlows().forEach(subFlow -> {
            String subFlowId = subFlow.getSubFlowId();
            if (stateMachine.getSubFlows().contains(subFlowId)) {
                PathId forwardPath = subFlow.getFlow().getForwardPathId();
                if (stateMachine.getNewPrimaryPaths().contains(forwardPath)) {
                    log.debug("Sub-flow {} paths of y-flow {} are to be reverted (swapped)", subFlowId, yFlowId);
                    stateMachine.addSwappingSubFlow(subFlowId);
                    stateMachine.notifyEventListeners(listener ->
                            listener.onSubFlowProcessingStart(yFlowId, subFlowId));
                    CommandContext flowContext = stateMachine.getCommandContext().fork(subFlowId);
                    flowPathSwapService.startFlowPathSwapping(flowContext, subFlowId);
                }
            }
        });
    }
}
