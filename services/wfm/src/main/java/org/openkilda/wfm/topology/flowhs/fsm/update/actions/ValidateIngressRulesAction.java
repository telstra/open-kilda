/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.update.actions;

import static java.lang.String.format;

import org.openkilda.floodlight.flow.request.GetInstalledRule;
import org.openkilda.floodlight.flow.request.InstallIngressRule;
import org.openkilda.floodlight.flow.response.FlowResponse;
import org.openkilda.floodlight.flow.response.FlowRuleResponse;
import org.openkilda.model.Switch;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.State;
import org.openkilda.wfm.topology.flowhs.validation.rules.IngressRulesValidator;
import org.openkilda.wfm.topology.flowhs.validation.rules.RulesValidator;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class ValidateIngressRulesAction extends
        HistoryRecordingAction<FlowUpdateFsm, State, Event, FlowUpdateContext> {
    private final SwitchRepository switchRepository;
    private final int speakerCommandRetriesLimit;

    public ValidateIngressRulesAction(PersistenceManager persistenceManager, int speakerCommandRetriesLimit) {
        this.switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        this.speakerCommandRetriesLimit = speakerCommandRetriesLimit;
    }

    @Override
    protected void perform(State from, State to, Event event, FlowUpdateContext context, FlowUpdateFsm stateMachine) {
        FlowResponse response = context.getSpeakerFlowResponse();
        UUID commandId = response.getCommandId();
        InstallIngressRule command = stateMachine.getIngressCommands().get(commandId);
        if (!stateMachine.getPendingCommands().contains(commandId) || command == null) {
            log.info("Received a response for unexpected command: {}", response);
            return;
        }

        if (response.isSuccess() && response instanceof FlowRuleResponse) {
            stateMachine.getPendingCommands().remove(commandId);

            Switch switchObj = switchRepository.findById(command.getSwitchId())
                    .orElseThrow(() -> new IllegalStateException(format("Failed to find switch %s",
                            command.getSwitchId())));

            RulesValidator validator = new IngressRulesValidator(command, (FlowRuleResponse) response,
                    switchObj.getFeatures());
            if (validator.validate()) {
                stateMachine.saveActionToHistory("Rule was validated",
                        format("The ingress rule has been validated successfully: switch %s, cookie %s",
                                command.getSwitchId(), command.getCookie()));
            } else {
                stateMachine.saveErrorToHistory("Rule is missing or invalid",
                        format("The ingress rule is missing or invalid: switch %s, cookie %s",
                                command.getSwitchId(), command.getCookie()));

                stateMachine.getFailedValidationResponses().put(commandId, response);
            }
        } else {
            int retries = stateMachine.getRetriedCommands().getOrDefault(commandId, 0);
            if (retries < speakerCommandRetriesLimit) {
                stateMachine.getRetriedCommands().put(commandId, ++retries);

                stateMachine.saveErrorToHistory("Rule validation failed", format(
                        "Failed to validate the ingress rule: commandId %s, switch %s, cookie %s. Error %s. "
                                + "Retrying (attempt %d)",
                        commandId, response.getSwitchId(), command.getCookie(), response, retries));

                GetInstalledRule dumpFlowRule = new GetInstalledRule(command.getMessageContext(),
                        command.getCommandId(), command.getFlowId(), command.getSwitchId(), command.getCookie(), false);

                stateMachine.getCarrier().sendSpeakerRequest(dumpFlowRule);
            } else {
                stateMachine.getPendingCommands().remove(commandId);

                stateMachine.saveErrorToHistory("Rule validation failed",
                        format("Failed to validate the ingress rule: commandId %s, switch %s, cookie %s. Error %s",
                                commandId, response.getSwitchId(), command.getCookie(), response));

                stateMachine.getFailedValidationResponses().put(commandId, response);
            }
        }

        if (stateMachine.getPendingCommands().isEmpty()) {
            if (stateMachine.getFailedValidationResponses().isEmpty()) {
                log.debug("Ingress rules have been validated for flow {}", stateMachine.getFlowId());
                stateMachine.fire(Event.RULES_VALIDATED);
            } else {
                stateMachine.saveErrorToHistory(format(
                        "Found missing rules or received error response(s) on %d validation commands",
                        stateMachine.getFailedValidationResponses().size()));
                stateMachine.fire(Event.MISSING_RULE_FOUND);
            }
        }
    }
}
