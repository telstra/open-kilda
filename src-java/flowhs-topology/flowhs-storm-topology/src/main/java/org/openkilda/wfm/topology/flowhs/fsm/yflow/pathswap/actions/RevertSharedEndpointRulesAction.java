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

import org.openkilda.floodlight.api.request.rulemanager.DeleteSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.InstallSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.SwitchId;
import org.openkilda.model.YFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.converters.FlowRulesConverter;
import org.openkilda.wfm.topology.flowhs.fsm.common.converters.OfCommandConverter;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Slf4j
public class RevertSharedEndpointRulesAction extends UpdateSharedEndpointRulesAction {
    public RevertSharedEndpointRulesAction(PersistenceManager persistenceManager, RuleManager ruleManager) {
        super(persistenceManager, ruleManager);
    }

    @Override
    protected void perform(State from, State to, Event event, YFlowPathSwapContext context,
                           YFlowPathSwapFsm stateMachine) {
        stateMachine.clearPendingAndRetriedAndFailedCommands();

        String yFlowId = stateMachine.getYFlowId();
        YFlow yFlow = yFlowRepository.findById(yFlowId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.INTERNAL_ERROR,
                        format("Y-flow %s not found", yFlowId)));

        SwitchId sharedEndpoint = yFlow.getSharedEndpoint().getSwitchId();
        List<OfCommand> installOfCommands = buildFlowOnlyOfCommands(sharedEndpoint,
                stateMachine.getOldPrimaryPaths());
        InstallSpeakerCommandsRequest installRequest =
                FlowRulesConverter.INSTANCE.buildFlowInstallCommand(sharedEndpoint, installOfCommands,
                        stateMachine.getCommandContext());
        stateMachine.addInstallSpeakerCommand(installRequest.getCommandId(), installRequest);

        List<OfCommand> deleteOfCommands = stateMachine.getInstallNewYFlowOfCommands();
        DeleteSpeakerCommandsRequest deleteRequest = null;
        if (deleteOfCommands != null) {
            deleteOfCommands = OfCommandConverter.INSTANCE.reverseDependenciesForDeletion(deleteOfCommands);
            deleteRequest = FlowRulesConverter.INSTANCE.buildFlowDeleteCommand(sharedEndpoint, deleteOfCommands,
                    stateMachine.getCommandContext());
            stateMachine.addDeleteSpeakerCommand(deleteRequest.getCommandId(), deleteRequest);
        }

        if (stateMachine.getInstallSpeakerRequests().isEmpty() && stateMachine.getDeleteSpeakerRequests().isEmpty()) {
            stateMachine.saveActionToHistory("No need to revert y-flow rules");
            stateMachine.fire(Event.ALL_YFLOW_RULES_REVERTED);
        } else {
            // emitting
            Stream.of(installRequest, deleteRequest).filter(Objects::nonNull).forEach(command -> {
                stateMachine.getCarrier().sendSpeakerRequest(command);
                stateMachine.addPendingCommand(command.getCommandId(), command.getSwitchId());
            });

            stateMachine.saveActionToHistory("Commands for reverting y-flow rules have been sent");
        }
    }
}
