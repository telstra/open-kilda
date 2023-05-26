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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow;

import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.wfm.topology.flowhs.fsm.common.HaFlowPathSwappingFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.context.SpeakerResponseContext;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OnReceivedRemoveResponseAction<T extends HaFlowPathSwappingFsm<T, S, E, C, ?, ?>, S, E,
        C extends SpeakerResponseContext> extends BaseReceivedResponseAction<T, S, E, C> {
    public static final String REMOVE_ACTION = "remove";

    public OnReceivedRemoveResponseAction(int speakerCommandRetriesLimit, E successEvent, FlowGenericCarrier carrier) {
        super(speakerCommandRetriesLimit, successEvent, carrier);
    }

    @Override
    protected void perform(S from, S to, E event, C context, T stateMachine) {
        SpeakerCommandResponse response = context.getSpeakerResponse();
        BaseSpeakerCommandsRequest request = stateMachine.getRemoveCommands().get(response.getCommandId());
        handleResponse(response, request, REMOVE_ACTION, stateMachine);
    }
}
