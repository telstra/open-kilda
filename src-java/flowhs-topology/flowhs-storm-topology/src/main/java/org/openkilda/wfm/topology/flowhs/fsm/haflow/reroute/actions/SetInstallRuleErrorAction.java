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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.actions;

import static java.lang.String.format;

import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.messaging.info.reroute.error.SpeakerRequestError;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.service.haflow.history.HaFlowHistory;
import org.openkilda.wfm.topology.flowhs.service.haflow.history.HaFlowHistoryService;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.AnonymousAction;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class SetInstallRuleErrorAction extends AnonymousAction<HaFlowRerouteFsm, State, Event, HaFlowRerouteContext> {

    @Override
    public void execute(
            State from, State to, Event event, HaFlowRerouteContext context, HaFlowRerouteFsm stateMachine) {
        Set<SwitchId> switches = Stream.concat(stateMachine.getPendingCommands().values().stream(),
                stateMachine.getFailedCommands().values().stream().map(SpeakerResponse::getSwitchId))
                .collect(Collectors.toSet());
        stateMachine.setRerouteError(new SpeakerRequestError("Failed to install rules", switches));
        log.debug("Abandoning all pending commands: {}", stateMachine.getPendingCommands());

        HaFlowHistoryService.using(stateMachine.getCarrier()).saveError(HaFlowHistory
                .withTaskId(stateMachine.getCommandContext().getCorrelationId())
                .withAction("Failed to install rules")
                .withDescription(format("Abandoning all pending commands: %s", stateMachine.getPendingCommands()))
                .withHaFlowId(stateMachine.getHaFlowId()));

        stateMachine.clearPendingCommands();
    }
}
