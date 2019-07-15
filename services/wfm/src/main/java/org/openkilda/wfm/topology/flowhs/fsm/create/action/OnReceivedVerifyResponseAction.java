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

import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.action.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.service.SpeakerCommandObserver;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OnReceivedVerifyResponseAction
        extends FlowProcessingAction<FlowCreateFsm, State, Event, FlowCreateContext>  {
    public OnReceivedVerifyResponseAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(State from, State to, Event event, FlowCreateContext context, FlowCreateFsm stateMachine) {
        SpeakerFlowSegmentResponse response = context.getSpeakerFlowResponse();
        if (! stateMachine.isPendingRequest(response.getCommandId())) {
            log.warn("Received response for non-pending request: {}", response.getCommandId());
            return;
        }

        SpeakerCommandObserver commandObserver = stateMachine.getPendingRequests().get(response.getCommandId());
        if (commandObserver.handleResponse(response)) {
            stateMachine.getPendingRequests().remove(response.getCommandId());
            handleResponse(stateMachine, context);
        }
    }

    void handleResponse(FlowCreateFsm stateMachine, FlowCreateContext context) {
        SpeakerFlowSegmentResponse response = context.getSpeakerFlowResponse();
        if (response.isSuccess()) {
            stateMachine.fire(Event.RULE_RECEIVED, context);
        } else {
            log.info("Failed to perform OF flows validation on switch {}, command: id {}",
                     response.getSwitchId(), response.getCommandId());
            stateMachine.getFailedCommands().add(response.getCommandId());
        }
    }
}
