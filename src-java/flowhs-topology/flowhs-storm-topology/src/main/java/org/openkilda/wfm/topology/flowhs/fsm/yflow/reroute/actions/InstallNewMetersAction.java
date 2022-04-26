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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.actions;

import org.openkilda.floodlight.api.request.rulemanager.InstallSpeakerCommandsRequest;
import org.openkilda.model.YFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.YFlowRuleManagerProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;

@Slf4j
public class InstallNewMetersAction
        extends YFlowRuleManagerProcessingAction<YFlowRerouteFsm, State, Event, YFlowRerouteContext> {
    public InstallNewMetersAction(PersistenceManager persistenceManager, RuleManager ruleManager) {
        super(persistenceManager, ruleManager);
    }

    @Override
    protected void perform(State from, State to, Event event,
                           YFlowRerouteContext context, YFlowRerouteFsm stateMachine) {
        stateMachine.clearPendingAndRetriedAndFailedCommands();

        String yFlowId = stateMachine.getYFlowId();
        YFlow yFlow = getYFlow(yFlowId);
        Collection<InstallSpeakerCommandsRequest> commands =
                buildYFlowInstallRequests(yFlow, stateMachine.getCommandContext());

        if (commands.isEmpty()) {
            stateMachine.saveActionToHistory("No need to install y-flow meters");
            stateMachine.fire(Event.YFLOW_METERS_INSTALLED);
        } else {
            // emitting
            commands.forEach(command -> {
                stateMachine.getCarrier().sendSpeakerRequest(command);
                stateMachine.addInstallSpeakerCommand(command.getCommandId(), command);
                stateMachine.addPendingCommand(command.getCommandId(), command.getSwitchId());
            });

            stateMachine.saveActionToHistory("Commands for installing y-flow rules have been sent");
        }
    }
}
