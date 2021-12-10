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

package org.openkilda.wfm.topology.flowhs.fsm.create.actions;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.SpeakerCommandFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.SpeakerCommandFsm.Builder;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.service.SpeakerCommandObserver;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.UUID;

@Slf4j
public class RollbackInstalledRulesAction extends
        FlowProcessingWithHistorySupportAction<FlowCreateFsm, State, Event, FlowCreateContext> {
    private final SpeakerCommandFsm.Builder speakerCommandFsmBuilder;

    public RollbackInstalledRulesAction(Builder fsmBuilder, PersistenceManager persistenceManager) {
        super(persistenceManager);
        this.speakerCommandFsmBuilder = fsmBuilder;
    }

    @Override
    protected void perform(State from, State to, Event event, FlowCreateContext context, FlowCreateFsm stateMachine) {
        stateMachine.getPendingCommands().clear();
        stateMachine.getFailedCommands().clear();

        Map<UUID, SpeakerCommandObserver> pendingRequests = stateMachine.getPendingCommands();
        for (FlowSegmentRequestFactory factory : stateMachine.getSentCommands()) {
            FlowSegmentRequest request = factory.makeRemoveRequest(commandIdGenerator.generate());

            SpeakerCommandObserver commandObserver = new SpeakerCommandObserver(speakerCommandFsmBuilder, request);
            commandObserver.start();

            // TODO ensure no conflicts
            pendingRequests.put(request.getCommandId(), commandObserver);
        }

        stateMachine.saveActionToHistory(String.format(
                "Commands to rollback installed rules have been sent. Total amount: %s", pendingRequests.size()));
    }
}
