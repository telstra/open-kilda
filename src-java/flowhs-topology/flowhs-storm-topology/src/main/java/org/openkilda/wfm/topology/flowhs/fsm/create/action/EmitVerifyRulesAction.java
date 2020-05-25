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

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.floodlight.flow.response.FlowErrorResponse.ErrorCode;
import org.openkilda.wfm.topology.flowhs.fsm.common.SpeakerCommandFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.service.SpeakerCommandObserver;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

abstract class EmitVerifyRulesAction
        extends HistoryRecordingAction<FlowCreateFsm, FlowCreateFsm.State, FlowCreateFsm.Event, FlowCreateContext> {

    private final NoArgGenerator commandIdGenerator = Generators.timeBasedGenerator();
    private final SpeakerCommandFsm.Builder speakerCommandFsmBuilder;

    public EmitVerifyRulesAction(SpeakerCommandFsm.Builder speakerCommandFsmBuilder) {
        this.speakerCommandFsmBuilder = speakerCommandFsmBuilder;
    }

    protected void emitVerifyRequests(
            FlowCreateFsm stateMachine, Collection<FlowSegmentRequestFactory> requestFactories) {
        final Map<UUID, SpeakerCommandObserver> pendingCommands = stateMachine.getPendingCommands();
        for (FlowSegmentRequestFactory factory : requestFactories) {
            FlowSegmentRequest request = factory.makeVerifyRequest(commandIdGenerator.generate());

            SpeakerCommandObserver commandObserver = new SpeakerCommandObserver(speakerCommandFsmBuilder,
                    Collections.singleton(ErrorCode.MISSING_OF_FLOWS), request);
            commandObserver.start();
            pendingCommands.put(request.getCommandId(), commandObserver);
        }
    }
}
