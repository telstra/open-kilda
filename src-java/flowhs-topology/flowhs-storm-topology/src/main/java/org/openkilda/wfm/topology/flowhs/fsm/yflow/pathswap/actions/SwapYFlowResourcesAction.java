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

import org.openkilda.model.YFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.YFlowRuleManagerProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SwapYFlowResourcesAction extends
        YFlowRuleManagerProcessingAction<YFlowPathSwapFsm, State, Event, YFlowPathSwapContext> {
    public SwapYFlowResourcesAction(PersistenceManager persistenceManager, RuleManager ruleManager) {
        super(persistenceManager, ruleManager);
    }

    @Override
    protected void perform(State from, State to, Event event, YFlowPathSwapContext context,
                           YFlowPathSwapFsm stateMachine) {
        String yFlowId = stateMachine.getYFlowId();
        transactionManager.doInTransaction(() -> {
            YFlow yFlow = getYFlow(yFlowId);
            stateMachine.setOldYPoint(yFlow.getYPoint());
            stateMachine.setOldMeterId(yFlow.getMeterId());
            stateMachine.setOldProtectedPathYPoint(yFlow.getProtectedPathYPoint());
            stateMachine.setOldProtectedPathMeterId(yFlow.getProtectedPathMeterId());
            yFlow.setYPoint(yFlow.getProtectedPathYPoint());
            yFlow.setMeterId(yFlow.getProtectedPathMeterId());
            yFlow.setProtectedPathYPoint(stateMachine.getOldYPoint());
            yFlow.setProtectedPathMeterId(stateMachine.getOldMeterId());
        });

        stateMachine.saveActionToHistory(format("The y-flow main and protected y-points and meters were swapped: "
                        + "%s / %s, %s / %s",
                stateMachine.getOldYPoint(), stateMachine.getOldProtectedPathYPoint(),
                stateMachine.getOldMeterId(), stateMachine.getOldProtectedPathMeterId()));
    }
}
