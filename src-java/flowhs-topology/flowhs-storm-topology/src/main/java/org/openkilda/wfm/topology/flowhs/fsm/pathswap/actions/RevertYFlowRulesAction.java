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

package org.openkilda.wfm.topology.flowhs.fsm.pathswap.actions;

import static java.lang.String.format;

import org.openkilda.floodlight.api.request.rulemanager.DeleteSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.InstallSpeakerCommandsRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.SwitchId;
import org.openkilda.model.YFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapContext;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.stream.Stream;

@Slf4j
public class RevertYFlowRulesAction extends UpdateYFlowRulesAction {
    public RevertYFlowRulesAction(PersistenceManager persistenceManager, RuleManager ruleManager) {
        super(persistenceManager, ruleManager);
    }

    @Override
    protected void perform(State from, State to, Event event, FlowPathSwapContext context,
                           FlowPathSwapFsm stateMachine) {
        String flowId = stateMachine.getFlowId();
        Flow flow = getFlow(flowId);
        String yFlowId = flow.getYFlowId();
        if (yFlowId == null) {
            stateMachine.saveActionToHistory("No need to revert y-flow rules - it's not a sub-flow");
            stateMachine.fire(Event.SKIP_YFLOW_RULES_REVERT);
            return;
        }
        YFlow yFlow = yFlowRepository.findById(yFlowId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.INTERNAL_ERROR,
                        format("Y-flow %s not found in persistent storage", yFlowId)));

        stateMachine.clearPendingAndRetriedAndFailedCommands();

        SwitchId sharedEndpoint = yFlow.getSharedEndpoint().getSwitchId();
        InstallSpeakerCommandsRequest installRequest = buildYFlowInstallRequest(sharedEndpoint,
                stateMachine.getOldPrimaryForwardPath(), stateMachine.getCommandContext());
        stateMachine.addInstallSpeakerCommand(installRequest.getCommandId(), installRequest);
        DeleteSpeakerCommandsRequest deleteRequest = buildYFlowDeleteRequest(sharedEndpoint,
                stateMachine.getNewPrimaryForwardPath(), stateMachine.getCommandContext());
        stateMachine.addDeleteSpeakerCommand(deleteRequest.getCommandId(), deleteRequest);

        // emitting
        Stream.of(installRequest, deleteRequest).forEach(command -> {
            stateMachine.getCarrier().sendSpeakerRequest(command);
            stateMachine.addPendingCommand(command.getCommandId(), command.getSwitchId());
        });

        stateMachine.saveActionToHistory("Commands for reverting y-flow rules have been sent");
    }
}
