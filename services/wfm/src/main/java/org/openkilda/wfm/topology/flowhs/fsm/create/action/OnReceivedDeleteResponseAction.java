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

import org.openkilda.floodlight.flow.request.RemoveRule;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.floodlight.flow.response.FlowResponse;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class OnReceivedDeleteResponseAction extends OnReceivedInstallResponseAction {
    public OnReceivedDeleteResponseAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    void handleResponse(FlowCreateFsm stateMachine, FlowCreateContext context) {
        FlowResponse response = context.getSpeakerFlowResponse();
        UUID commandId = response.getCommandId();
        if (!stateMachine.getRemoveCommands().containsKey(commandId)) {
            log.info("Failed to find a delete rule command with id {}", commandId);
            return;
        }

        RemoveRule rule = stateMachine.getRemoveCommands().get(commandId);
        if (response.isSuccess()) {
            stateMachine.saveActionToHistory("Rule was deleted",
                    format("The rule was deleted: switch %s, cookie %s", rule.getSwitchId(), rule.getCookie()));
        } else {
            FlowErrorResponse errorResponse = (FlowErrorResponse) response;
            stateMachine.getFailedCommands().put(errorResponse.getCommandId(), errorResponse);
            stateMachine.saveErrorToHistory("Failed to delete rule",
                    format("Failed to delete the rule: commandId %s, switch %s. Error: %s",
                            errorResponse.getCommandId(), errorResponse.getSwitchId(), errorResponse));
        }
    }
}
