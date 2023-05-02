/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.update.actions;

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HaFlowRuleManagerProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InstallNonIngressRulesAction
        extends HaFlowRuleManagerProcessingAction<HaFlowUpdateFsm, State, Event, HaFlowUpdateContext> {

    public InstallNonIngressRulesAction(PersistenceManager persistenceManager, RuleManager ruleManager) {
        super(persistenceManager, ruleManager);
    }

    @Override
    protected void perform(
            State from, State to, Event event, HaFlowUpdateContext context, HaFlowUpdateFsm stateMachine) {
        stateMachine.clearPendingAndRetriedAndFailedCommands();

        if (stateMachine.getNonIngressCommands().isEmpty()) {
            stateMachine.saveActionToHistory("No need to install non ingress rules");
            stateMachine.fire(Event.RULES_INSTALLED);
        } else {
            stateMachine.getNonIngressCommands().values().forEach(request -> {
                stateMachine.getCarrier().sendSpeakerRequest(request);
                stateMachine.addPendingCommand(request.getCommandId(), request.getSwitchId());
            });
            stateMachine.saveActionToHistory("Commands for installing non ingress ha-flow rules have been sent");
        }
    }
}
