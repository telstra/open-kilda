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

import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.floodlight.flow.response.FlowResponse;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class OnReceivedInstallResponseAction extends RuleProcessingAction {
    @Override
    protected void perform(FlowRerouteFsm.State from, FlowRerouteFsm.State to,
                           FlowRerouteFsm.Event event, FlowRerouteContext context, FlowRerouteFsm stateMachine) {
        FlowResponse response = context.getFlowResponse();
        UUID commandId = response.getCommandId();
        if (stateMachine.getPendingCommands().remove(commandId)) {
            long cookie = getCookieForCommand(stateMachine, commandId);

            if (response.isSuccess()) {
                String message = format("Rule %s was installed successfully on switch %s", cookie,
                        response.getSwitchId());
                log.debug(message);
                sendHistoryUpdate(stateMachine, "Rule installed", message);
            } else {
                FlowErrorResponse errorResponse = (FlowErrorResponse) response;
                String message = format("Failed to install rule %s on switch %s: %s. Description: %s",
                        cookie, errorResponse.getSwitchId(), errorResponse.getErrorCode(),
                        errorResponse.getDescription());
                log.warn(message);
                sendHistoryUpdate(stateMachine, "Rule not installed", message);

                stateMachine.getErrorResponses().put(commandId, errorResponse);
            }
        } else {
            log.warn("Received a response for unexpected command: {}", response);
        }

        if (stateMachine.getPendingCommands().isEmpty()) {
            if (stateMachine.getErrorResponses().isEmpty()) {
                log.debug("Received responses for all pending install commands of the flow {}",
                        stateMachine.getFlowId());
                stateMachine.fire(Event.RULES_INSTALLED);
            } else {
                log.warn("Received error response(s) for some install commands of the flow {}",
                        stateMachine.getFlowId());
                stateMachine.fireError();
            }
        }
    }
}
