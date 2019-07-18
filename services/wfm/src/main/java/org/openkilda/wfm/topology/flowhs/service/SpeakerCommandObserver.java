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

package org.openkilda.wfm.topology.flowhs.service;

import org.openkilda.floodlight.flow.request.SpeakerFlowRequest;
import org.openkilda.floodlight.flow.response.FlowResponse;
import org.openkilda.wfm.topology.flowhs.fsm.common.SpeakerCommandFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.SpeakerCommandFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.common.SpeakerCommandFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class SpeakerCommandObserver {

    private final UUID commandId;
    private final SpeakerCommandFsm commandExecutor;

    public SpeakerCommandObserver(SpeakerCommandFsm.Builder builder, SpeakerFlowRequest request) {
        this.commandId = request.getCommandId();
        this.commandExecutor = builder.newInstance(request);
    }

    /**
     * Starts execution of speaker command: sends a command and waits for a response from a speaker.
     */
    public void start() {
        commandExecutor.start();

        if (commandExecutor.getCurrentState() != null && commandExecutor.getCurrentState() == State.INIT) {
            commandExecutor.fire(Event.NEXT);
        } else {
            log.warn("Failed to start processing {} command: it is already in progress", commandId);
        }
    }

    /**
     * Processes a response. If command wasn't executed successfully then it will be retried if limit is not exceeded.
     * @param response a response from a speaker.
     */
    public void handleResponse(FlowResponse response) {
        if (response != null && commandExecutor.getCurrentState() == State.IN_PROGRESS) {
            commandExecutor.fire(Event.REPLY, response);
        } else {
            log.debug("Received response while fsm is not in progress state (current state {}): {}",
                    commandExecutor.getCurrentState(), response);
        }
    }

    /**
     * Tells whether command execution is completed.
     */
    public boolean isFinished() {
        return commandExecutor.getCurrentState() == State.SUCCESS || commandExecutor.getCurrentState() == State.FAILED;
    }
}
