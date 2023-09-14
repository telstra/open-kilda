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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.create.actions;

import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.BaseReceivedResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class OnReceivedResponseAction
        extends BaseReceivedResponseAction<HaFlowCreateFsm, State, Event, HaFlowCreateContext> {
    private final String actionName;

    public OnReceivedResponseAction(
            int speakerCommandRetriesLimit, String actionName, Event finishEvent, FlowGenericCarrier carrier) {
        super(speakerCommandRetriesLimit, finishEvent, carrier);
        this.actionName = actionName;
    }

    @Override
    protected void perform(
            State from, State to, Event event, HaFlowCreateContext context, HaFlowCreateFsm stateMachine) {
        SpeakerCommandResponse response = context.getSpeakerResponse();
        UUID commandId = response.getCommandId();
        BaseSpeakerCommandsRequest request = stateMachine.getSpeakerCommand(commandId).orElse(null);
        handleResponse(response, request, actionName, stateMachine);
    }
}
