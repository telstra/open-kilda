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

package org.openkilda.wfm.topology.flowhs.fsm.reroute.actions;

import static java.lang.String.format;
import static org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.REROUTE_RETRY_LIMIT;

import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.model.Flow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.model.FlowRerouteFact;
import org.openkilda.wfm.topology.flowhs.service.FlowRerouteHubCarrier;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.UUID;

@Slf4j
public class ValidateNonIngressRulesAction extends
        HistoryRecordingAction<FlowRerouteFsm, State, Event, FlowRerouteContext> {
    private final int speakerCommandRetriesLimit;
    private FlowRepository flowRepository;
    private FlowRerouteHubCarrier carrier;

    private final NoArgGenerator commandIdGenerator = Generators.timeBasedGenerator();

    public ValidateNonIngressRulesAction(int speakerCommandRetriesLimit, PersistenceManager persistenceManager,
                                         FlowRerouteHubCarrier carrier) {
        this.speakerCommandRetriesLimit = speakerCommandRetriesLimit;
        flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
        this.carrier = carrier;
    }

    @Override
    protected void perform(State from, State to, Event event, FlowRerouteContext context, FlowRerouteFsm stateMachine) {
        SpeakerFlowSegmentResponse response = context.getSpeakerFlowResponse();
        UUID commandId = response.getCommandId();
        FlowSegmentRequestFactory command = stateMachine.getNonIngressCommands().get(commandId);
        if (!stateMachine.getPendingCommands().contains(commandId) || command == null) {
            log.info("Received a response for unexpected command: {}", response);
            return;
        }

        if (response.isSuccess()) {
            stateMachine.getPendingCommands().remove(commandId);

            stateMachine.saveActionToHistory("Rule was validated",
                    format("The non ingress rule has been validated successfully: switch %s, cookie %s",
                            command.getSwitchId(), command.getCookie()));
        } else {
            FlowErrorResponse errorResponse = (FlowErrorResponse) response;

            int retries = stateMachine.getRetriedCommands().getOrDefault(commandId, 0);
            if (retries < speakerCommandRetriesLimit
                    && errorResponse.getErrorCode() != FlowErrorResponse.ErrorCode.MISSING_OF_FLOWS) {
                stateMachine.getRetriedCommands().put(commandId, ++retries);

                stateMachine.saveErrorToHistory("Rule validation failed", format(
                        "Failed to validate non ingress rule: commandId %s, switch %s, cookie %s. Error %s. "
                                + "Retrying (attempt %d)",
                        commandId, errorResponse.getSwitchId(), command.getCookie(), errorResponse, retries));

                stateMachine.getCarrier().sendSpeakerRequest(command.makeVerifyRequest(commandId));
            } else {
                stateMachine.getPendingCommands().remove(commandId);

                stateMachine.saveErrorToHistory("Rule validation failed",
                        format("Failed to validate non ingress rule: commandId %s, switch %s, cookie %s. Error %s",
                                commandId, errorResponse.getSwitchId(), command.getCookie(), errorResponse));

                stateMachine.getFailedValidationResponses().put(commandId, response);
            }
        }

        if (stateMachine.getPendingCommands().isEmpty()) {
            Collection<SpeakerFlowSegmentResponse> failedValidationResponses
                    = stateMachine.getFailedValidationResponses().values();
            if (failedValidationResponses.isEmpty()) {
                log.debug("Non ingress rules have been validated for flow {}", stateMachine.getFlowId());
                stateMachine.fire(Event.RULES_VALIDATED);
            } else {
                String flowId = stateMachine.getFlowId();
                Flow flow = flowRepository.findById(flowId)
                        .orElseThrow(() -> new IllegalStateException(format("Flow %s not found", flowId)));
                boolean isTerminatingSwitchFailed = failedValidationResponses.stream()
                        .anyMatch(errorResponse -> errorResponse.getSwitchId().equals(flow.getSrcSwitch().getSwitchId())
                                || errorResponse.getSwitchId().equals(flow.getDestSwitch().getSwitchId()));
                int rerouteCounter = stateMachine.getRerouteCounter();
                if (!isTerminatingSwitchFailed && rerouteCounter < REROUTE_RETRY_LIMIT) {
                    rerouteCounter += 1;
                    FlowRerouteContext initialContext = stateMachine.getInitialContext();
                    String newReason = format("%s: retry #%d", initialContext.getRerouteReason(), rerouteCounter);
                    FlowRerouteFact flowRerouteFact = new FlowRerouteFact(commandIdGenerator.generate().toString(),
                            stateMachine.getCommandContext().fork(format("retry #%d", rerouteCounter)),
                            stateMachine.getFlowId(), initialContext.getAffectedIsl(), initialContext.isForceReroute(),
                            initialContext.isEffectivelyDown(), newReason, rerouteCounter);
                    carrier.injectRetry(flowRerouteFact);
                    stateMachine.saveActionToHistory("Inject reroute retry",
                            format("Reroute counter %d", rerouteCounter));
                }

                stateMachine.saveErrorToHistory(format(
                        "Found missing rules or received error response(s) on %d validation commands",
                        failedValidationResponses.size()));
                stateMachine.fire(Event.MISSING_RULE_FOUND);
            }
        }
    }
}
