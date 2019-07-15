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

import org.openkilda.floodlight.api.request.FlowSegmentBlankGenericResolver;
import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.wfm.topology.flowhs.fsm.common.SpeakerCommandFsm;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.State;
import org.openkilda.wfm.topology.flowhs.service.SpeakerCommandObserver;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.AnonymousAction;

import java.util.Map;
import java.util.UUID;

@Slf4j
public class EmitIngressRulesVerifyRequestsAction
        extends AnonymousAction<FlowCreateFsm, State, Event, FlowCreateContext> {
    private final SpeakerCommandFsm.Builder speakerCommandFsmBuilder;

    public EmitIngressRulesVerifyRequestsAction(SpeakerCommandFsm.Builder speakerCommandFsmBuilder) {
        this.speakerCommandFsmBuilder = speakerCommandFsmBuilder;
    }

    @Override
    public void execute(State from, State to, Event event, FlowCreateContext context, FlowCreateFsm stateMachine) {
        log.debug("Started validation of installed ingress rules for the flow {}", stateMachine.getFlowId());

        final Map<UUID, SpeakerCommandObserver> pendingRequests = stateMachine.getPendingRequests();
        for (FlowSegmentBlankGenericResolver blank : stateMachine.getIngressCommands().values()) {
            FlowSegmentRequest request = blank.makeVerifyRequest();
            pendingRequests.put(request.getCommandId(), new SpeakerCommandObserver(speakerCommandFsmBuilder, request));
        }
    }
}
