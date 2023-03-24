/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.actions;

import static java.lang.String.format;

import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.UUID;

@Slf4j
public class HandleNotCompletedCommandsAction
        extends HistoryRecordingAction<FlowMirrorPointCreateFsm, State, Event, FlowMirrorPointCreateContext> {
    @Override
    public void perform(State from, State to, Event event, FlowMirrorPointCreateContext context,
                        FlowMirrorPointCreateFsm stateMachine) {
        for (UUID requestId : stateMachine.getPendingCommands().keySet()) {
            Optional<BaseSpeakerCommandsRequest> request = stateMachine.getSpeakerCommand(requestId);
            if (request.isPresent()) {
                for (OfCommand command : request.get().getCommands()) {
                    stateMachine.saveErrorToHistory("Command is not finished yet",
                            format("Completing the install operation although the remove command may not be "
                                            + "finished yet: requestId %s, switch %s, command uuid %s", requestId,
                                    request.get().getSwitchId(), command.getUuid()));
                }
            }
        }

        log.debug("Abandoning all pending commands: {}", stateMachine.getPendingCommands());
        stateMachine.getPendingCommands().clear();
    }
}
