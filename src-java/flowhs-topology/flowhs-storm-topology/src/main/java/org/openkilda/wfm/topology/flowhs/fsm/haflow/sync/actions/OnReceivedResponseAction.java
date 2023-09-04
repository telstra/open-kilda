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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.sync.actions;

import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.BaseReceivedResponseAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.sync.HaFlowSyncContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.sync.HaFlowSyncFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.sync.HaFlowSyncFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.sync.HaFlowSyncFsm.State;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class OnReceivedResponseAction
        extends BaseReceivedResponseAction<HaFlowSyncFsm, State, Event, HaFlowSyncContext> {
    public static final String INSTALL_ACTION_NAME = "install";

    public OnReceivedResponseAction(
            int speakerCommandRetriesLimit, Event finishEvent, FlowGenericCarrier carrier) {
        super(speakerCommandRetriesLimit, finishEvent, carrier);
    }

    @Override
    protected void perform(
            State from, State to, Event event, HaFlowSyncContext context, HaFlowSyncFsm stateMachine) {
        SpeakerCommandResponse response = context.getSpeakerResponse();
        UUID commandId = response.getCommandId();
        BaseSpeakerCommandsRequest request = stateMachine.getSpeakerCommand(commandId).orElse(null);
        log.error("LLDP: response sw {} result {} full {} , request {}",
                response.getSwitchId(), response.isSuccess(), response, request);
        handleResponse(response, request, INSTALL_ACTION_NAME, stateMachine);
    }
}
