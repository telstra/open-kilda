/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.update.actions;

import static org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.Event.RULES_REVERTED;

import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.update.HaFlowUpdateFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OnReceivedRevertResponseAction extends BaseReceivedResponseAction {
    public static final String REINSTALL_ACTION = "re-install";
    public static final String REMOVE_ACTION = "remove";

    public OnReceivedRevertResponseAction(int speakerCommandRetriesLimit) {
        super(speakerCommandRetriesLimit, RULES_REVERTED);
    }

    @Override
    protected void perform(
            State from, State to, Event event, HaFlowUpdateContext context, HaFlowUpdateFsm stateMachine) {
        SpeakerCommandResponse response = context.getSpeakerResponse();
        String actionName;
        BaseSpeakerCommandsRequest request = stateMachine.getRemoveCommands().get(response.getCommandId());

        if (request != null) {
            actionName = REMOVE_ACTION;
        } else {
            request = stateMachine.getInstallCommand(response.getCommandId());
            actionName = REINSTALL_ACTION;
        }
        handleResponse(response, request, actionName, stateMachine);
    }
}
