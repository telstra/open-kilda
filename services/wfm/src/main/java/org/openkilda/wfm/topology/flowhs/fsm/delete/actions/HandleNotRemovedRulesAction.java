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

import org.openkilda.floodlight.flow.request.RemoveRule;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.UUID;

@Slf4j
public class HandleNotRemovedRulesAction extends
        HistoryRecordingAction<FlowDeleteFsm, State, Event, FlowDeleteContext> {
    @Override
    public void perform(State from, State to, Event event, FlowDeleteContext context, FlowDeleteFsm stateMachine) {
        if (!stateMachine.getPendingCommands().isEmpty() || !stateMachine.getFailedCommands().isEmpty()) {
            for (UUID commandId : stateMachine.getPendingCommands()) {
                RemoveRule nonDeletedRule = stateMachine.getRemoveCommands().get(commandId);
                if (nonDeletedRule != null) {
                    stateMachine.saveErrorToHistory("Failed to remove rule",
                            format("Failed to remove the rule: commandId %s, switch %s, cookie %s", commandId,
                                    nonDeletedRule.getSwitchId(), nonDeletedRule.getCookie()));
                }
            }

            for (UUID commandId : stateMachine.getFailedCommands().keySet()) {
                FlowErrorResponse errorResponse = stateMachine.getFailedCommands().get(commandId);
                stateMachine.saveErrorToHistory("Failed to remove rule",
                        format("Failed to remove the rule: commandId %s, switch %s. Error %s", commandId,
                                errorResponse.getSwitchId(), errorResponse));
            }
        }

        log.info("Canceling all pending commands: {}", stateMachine.getPendingCommands());
        stateMachine.setPendingCommands(new HashSet<>());
    }
}
