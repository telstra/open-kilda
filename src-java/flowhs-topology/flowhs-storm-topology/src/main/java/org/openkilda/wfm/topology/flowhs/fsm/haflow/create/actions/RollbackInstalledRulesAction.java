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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.create.actions;

import org.openkilda.floodlight.api.request.rulemanager.DeleteSpeakerCommandsRequest;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HaFlowRuleManagerProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;

@Slf4j
public class RollbackInstalledRulesAction extends
        HaFlowRuleManagerProcessingAction<HaFlowCreateFsm, State, Event, HaFlowCreateContext> {

    public RollbackInstalledRulesAction(PersistenceManager persistenceManager, RuleManager ruleManager) {
        super(persistenceManager, ruleManager);
    }

    @Override
    protected void perform(
            State from, State to, Event event, HaFlowCreateContext context, HaFlowCreateFsm stateMachine) {
        stateMachine.getPendingCommands().clear();
        stateMachine.getFailedCommands().clear();
        stateMachine.getRetriedCommands().clear();
        stateMachine.getSpeakerCommands().clear();

        Collection<DeleteSpeakerCommandsRequest> deleteRequests = buildHaFlowDeleteRequests(
                stateMachine.getSentCommands(), stateMachine.getCommandContext());

        if (deleteRequests.isEmpty()) {
            stateMachine.saveActionToHistory("No need to rollback ha-flow rules");
            stateMachine.fire(Event.RULES_REMOVED);
        } else {
            // emitting
            deleteRequests.forEach(request -> {
                stateMachine.getCarrier().sendSpeakerRequest(request);
                stateMachine.addSpeakerCommand(request.getCommandId(), request);
                stateMachine.addPendingCommand(request.getCommandId(), request.getSwitchId());
            });

            stateMachine.saveActionToHistory("Commands for rolling back ha-flow rules have been sent");
        }
    }
}
