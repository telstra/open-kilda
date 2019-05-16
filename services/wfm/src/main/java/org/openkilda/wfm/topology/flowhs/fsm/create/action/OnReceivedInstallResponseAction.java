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

package org.openkilda.wfm.topology.flowhs.fsm.create.action;

import static java.lang.String.format;

import org.openkilda.floodlight.flow.request.InstallFlowRule;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.floodlight.flow.response.FlowResponse;
import org.openkilda.model.Flow;
import org.openkilda.wfm.share.history.model.FlowHistoryData;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.State;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.AnonymousAction;

import java.time.Instant;
import java.util.UUID;

@Slf4j
public class OnReceivedInstallResponseAction extends AnonymousAction<FlowCreateFsm, State, Event, FlowCreateContext> {
    @Override
    public void execute(State from, State to, Event event, FlowCreateContext context, FlowCreateFsm stateMachine) {
        FlowResponse response = context.getFlowResponse();
        stateMachine.getPendingCommands().remove(response.getCommandId());
        handleResponse(stateMachine, response);

        if (stateMachine.getPendingCommands().isEmpty()) {
            log.debug("Received responses for all pending commands");
            stateMachine.fire(Event.NEXT);
        }
    }

    void handleResponse(FlowCreateFsm stateMachine, FlowResponse response) {
        UUID commandId = response.getCommandId();
        InstallFlowRule installRule;
        if (stateMachine.getNonIngressCommands().containsKey(response.getCommandId())) {
            installRule = stateMachine.getNonIngressCommands().get(commandId);
        } else if (stateMachine.getIngressCommands().containsKey(response.getCommandId())) {
            installRule = stateMachine.getIngressCommands().get(commandId);
        } else {
            throw new IllegalStateException(format("Failed to find install rule command with id %s", commandId));
        }

        if (response.isSuccess()) {
            log.debug("Rule {} was installed on switch {}", installRule.getCookie(), response.getSwitchId());
            sendHistoryUpdate(stateMachine, "Rule installed",
                    format("Rule was installed successfully: cookie %s, switch %s",
                            installRule.getCookie(), response.getSwitchId()));
        } else {
            FlowErrorResponse errorResponse = (FlowErrorResponse) response;
            String message = format("Failed to install rule %s, on the switch %s: %s. Description: %s",
                    installRule.getCookie(), errorResponse.getSwitchId(),
                    errorResponse.getErrorCode(), errorResponse.getDescription());
            log.error(message);
            sendHistoryUpdate(stateMachine, "Rule not installed", message);
        }
    }

    final void sendHistoryUpdate(FlowCreateFsm stateMachine, String action, String description) {
        Flow flow = stateMachine.getFlow();

        FlowHistoryHolder historyHolder = FlowHistoryHolder.builder()
                .taskId(stateMachine.getCommandContext().getCorrelationId())
                .flowHistoryData(FlowHistoryData.builder()
                        .action(action)
                        .description(description)
                        .time(Instant.now())
                        .flowId(flow.getFlowId())
                        .build())
                .build();
        stateMachine.getCarrier().sendHistoryUpdate(historyHolder);
    }
}
