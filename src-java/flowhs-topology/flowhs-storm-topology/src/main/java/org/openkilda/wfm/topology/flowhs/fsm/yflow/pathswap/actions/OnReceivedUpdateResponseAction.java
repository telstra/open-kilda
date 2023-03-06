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

import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.BaseOnReceivedResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.pathswap.YFlowPathSwapFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.UUID;

@Slf4j
public class OnReceivedUpdateResponseAction extends BaseOnReceivedResponseAction<YFlowPathSwapFsm,
        State, Event, YFlowPathSwapContext> {
    public OnReceivedUpdateResponseAction(int speakerCommandRetriesLimit) {
        super(speakerCommandRetriesLimit);
    }

    @Override
    public void perform(State from, State to, Event event, YFlowPathSwapContext context,
                        YFlowPathSwapFsm stateMachine) {
        SpeakerCommandResponse response = context.getSpeakerResponse();
        UUID commandId = response.getCommandId();
        if (!stateMachine.hasPendingCommand(commandId)) {
            log.info("Received a response for unexpected command: {}", response);
            return;
        }

        boolean isInstallCommand = stateMachine.getInstallSpeakerCommand(commandId).isPresent();

        if (response.isSuccess()) {
            stateMachine.removePendingCommand(commandId);
            String commandName = isInstallCommand ? "installed" : "deleted";
            stateMachine.saveActionToHistory("Rule was " + commandName,
                    format("The rule was %s: switch %s", commandName, response.getSwitchId()));
        } else {
            int attempt = stateMachine.doRetryForCommand(commandId);
            if (attempt <= speakerCommandRetriesLimit) {
                String commandName = isInstallCommand ? "install" : "delete";
                response.getFailedCommandIds().forEach((uuid, message) ->
                        stateMachine.saveErrorToHistory("Failed to " + commandName + " rule",
                                format("Failed to %s the rule: commandId %s, ruleId %s, switch %s. "
                                                + "Error %s. Retrying (attempt %d)", commandName,
                                        commandId, uuid, response.getSwitchId(), message, attempt)));

                Set<UUID> failedUuids = response.getFailedCommandIds().keySet();
                stateMachine.getInstallSpeakerCommand(commandId)
                        .ifPresent(command -> stateMachine.getCarrier()
                                .sendSpeakerRequest(command.toBuilder()
                                        .commands(filterOfCommands(command.getCommands(), failedUuids)).build()));
                stateMachine.getDeleteSpeakerCommand(commandId)
                        .ifPresent(command -> stateMachine.getCarrier()
                                .sendSpeakerRequest(command.toBuilder()
                                        .commands(filterOfCommands(command.getCommands(), failedUuids)).build()));
            } else {
                stateMachine.addFailedCommand(commandId, response);
                stateMachine.removePendingCommand(commandId);

                String commandName = isInstallCommand ? "install" : "delete";
                response.getFailedCommandIds().forEach((uuid, message) ->
                        stateMachine.saveErrorToHistory("Failed to " + commandName + " rule",
                                format("Failed to %s the rule: commandId %s, ruleId %s, switch %s. Error %s.",
                                        commandName, commandId, uuid, response.getSwitchId(), message)));
            }
        }

        if (stateMachine.getPendingCommands().isEmpty()) {
            if (stateMachine.getFailedCommands().isEmpty()) {
                log.debug("Received responses for all pending install / delete commands of the y-flow {}",
                        stateMachine.getYFlowId());
                stateMachine.fire(Event.ALL_YFLOW_RULES_UPDATED);
            } else {
                String errorMessage = format("Received error response(s) for %d install / delete commands",
                        stateMachine.getFailedCommands().size());
                stateMachine.saveErrorToHistory(errorMessage);
                stateMachine.fireError(errorMessage);
            }
        }
    }
}
