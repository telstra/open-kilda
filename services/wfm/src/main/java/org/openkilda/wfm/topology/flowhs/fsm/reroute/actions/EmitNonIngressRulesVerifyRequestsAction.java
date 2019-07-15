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

package org.openkilda.wfm.topology.flowhs.fsm.reroute.actions;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestProxiedFactory;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.AnonymousAction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

@Slf4j
public class EmitNonIngressRulesVerifyRequestsAction extends
        AnonymousAction<FlowRerouteFsm, State, Event, FlowRerouteContext> {
    private final NoArgGenerator commandIdGenerator = Generators.timeBasedGenerator();

    @Override
    public void execute(State from, State to,
                        Event event, FlowRerouteContext context, FlowRerouteFsm stateMachine) {
        log.debug("Validating installed non ingress rules for the flow {}",
                stateMachine.getFlowId());

        Map<UUID, FlowSegmentRequestProxiedFactory> nonIngressRequests = stateMachine.getNonIngressCommands();

        Map<UUID, FlowSegmentRequestProxiedFactory> requestsMap = new HashMap<>();
        if (nonIngressRequests.isEmpty()) {
            log.debug("No need to validate non ingress rules for one switch flow");
            stateMachine.fire(Event.RULES_VALIDATED);
        } else {
            for (FlowSegmentRequestProxiedFactory factory : nonIngressRequests.values()) {
                FlowSegmentRequest request = factory.makeVerifyRequest(commandIdGenerator.generate());
                // TODO ensure no conflicts
                requestsMap.put(request.getCommandId(), factory);
                stateMachine.getCarrier().sendSpeakerRequest(request);
            }
        }

        stateMachine.setPendingCommands(new HashSet<>(requestsMap.keySet()));
        stateMachine.setNonIngressCommands(requestsMap);
    }
}
