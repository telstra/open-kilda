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

package org.openkilda.wfm.topology.flowhs.fsm.update.actions;

import org.openkilda.floodlight.flow.request.GetInstalledRule;
import org.openkilda.floodlight.flow.request.SpeakerFlowRequest;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public class DumpIngressRulesAction extends HistoryRecordingAction<FlowUpdateFsm, State, Event, FlowUpdateContext> {
    @Override
    public void perform(State from, State to, Event event, FlowUpdateContext context, FlowUpdateFsm stateMachine) {
        Collection<GetInstalledRule> dumpFlowRules = stateMachine.getIngressCommands().values().stream()
                .map(command -> new GetInstalledRule(command.getMessageContext(), command.getCommandId(),
                        command.getFlowId(), command.getSwitchId(), command.getCookie(), false))
                .collect(Collectors.toList());

        dumpFlowRules.forEach(command -> stateMachine.getCarrier().sendSpeakerRequest(command));

        Set<UUID> commandIds = dumpFlowRules.stream()
                .map(SpeakerFlowRequest::getCommandId)
                .collect(Collectors.toSet());
        stateMachine.setPendingCommands(commandIds);
        stateMachine.resetFailedCommandsAndRetries();

        stateMachine.saveActionToHistory("Started validation of installed ingress rules");
    }
}
