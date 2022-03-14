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

import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;
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
public class RevertYFlowResourcesAction extends
        YFlowRuleManagerProcessingAction<YFlowPathSwapFsm, State, Event, YFlowPathSwapContext> {
    public RevertYFlowResourcesAction(PersistenceManager persistenceManager, RuleManager ruleManager) {
        super(persistenceManager, ruleManager);
    }

    @Override
    protected void perform(State from, State to, Event event, YFlowPathSwapContext context,
                           YFlowPathSwapFsm stateMachine) {
        String yFlowId = stateMachine.getYFlowId();
        SwitchId oldYPoint = stateMachine.getOldYPoint();
        MeterId oldMeterId = stateMachine.getOldMeterId();
        if (oldYPoint == null) {
            stateMachine.saveActionToHistory("Skip reverting of y-flow resources, not required");
            return;
        }

        transactionManager.doInTransaction(() -> {
            YFlow yFlow = getYFlow(yFlowId);
            if (yFlow.getYPoint() != oldYPoint) {
                yFlow.setYPoint(oldYPoint);
            }
            if (yFlow.getMeterId() != oldMeterId) {
                yFlow.setMeterId(oldMeterId);
            }
            SwitchId oldProtectedPathYPoint = stateMachine.getOldProtectedPathYPoint();
            if (yFlow.getProtectedPathYPoint() != oldProtectedPathYPoint) {
                yFlow.setProtectedPathYPoint(oldProtectedPathYPoint);
            }
            MeterId oldProtectedPathMeterId = stateMachine.getOldProtectedPathMeterId();
            if (yFlow.getProtectedPathMeterId() != oldProtectedPathMeterId) {
                yFlow.setProtectedPathMeterId(oldProtectedPathMeterId);
            }
        });

        stateMachine.saveActionToHistory(format("The y-flow main and protected y-points and meters were reverted: "
                        + "%s / %s, %s / %s",
                stateMachine.getOldYPoint(), stateMachine.getOldProtectedPathYPoint(),
                stateMachine.getOldMeterId(), stateMachine.getOldProtectedPathMeterId()));

    }
}
