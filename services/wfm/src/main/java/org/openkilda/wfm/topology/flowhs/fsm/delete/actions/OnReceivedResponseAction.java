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

package org.openkilda.wfm.topology.flowhs.fsm.delete.actions;

import static java.lang.String.format;

import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.floodlight.flow.response.FlowResponse;
import org.openkilda.model.Cookie;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class OnReceivedResponseAction extends RuleProcessingAction {
    @Override
    protected void perform(State from, State to,
                           Event event, FlowDeleteContext context,
                           FlowDeleteFsm stateMachine) {
        FlowResponse response = context.getFlowResponse();
        if (!response.isSuccess() || response instanceof FlowErrorResponse) {
            throw new IllegalArgumentException(
                    format("Invoked %s for an error response: %s", this.getClass(), response));
        }

        UUID commandId = response.getCommandId();
        if (!stateMachine.getPendingCommands().remove(commandId)) {
            log.warn("Received a response for unexpected command: {}", response);
            return;
        }

        Cookie cookie = getCookieForCommand(stateMachine, commandId);
        String message = format("The rule was deleted: switch %s, cookie %s", response.getSwitchId(), cookie);
        log.debug(message);
        sendHistoryUpdate(stateMachine, "Rule was deleted", message);

        if (stateMachine.getPendingCommands().isEmpty()) {
            if (stateMachine.getErrorResponses().isEmpty()) {
                log.debug("Received responses for all pending remove commands of the flow {}",
                        stateMachine.getFlowId());
                stateMachine.fire(Event.RULES_REMOVED);
            } else {
                log.warn(format("Received error response(s) for %d remove commands",
                        stateMachine.getErrorResponses().size()));
                stateMachine.fire(Event.RULES_REMOVED);
            }
        }
    }
}
