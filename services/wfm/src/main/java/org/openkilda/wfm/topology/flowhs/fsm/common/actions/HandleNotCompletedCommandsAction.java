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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions;

import static java.lang.String.format;

import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowInstallingFsm;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class HandleNotCompletedCommandsAction<T extends FlowInstallingFsm<T, S, E, C>, S, E, C>
        extends HistoryRecordingAction<T, S, E, C> {
    private final String fsmOperationName;

    public HandleNotCompletedCommandsAction(@NonNull String fsmOperationName) {
        this.fsmOperationName = fsmOperationName;
    }

    @Override
    public void perform(S from, S to, E event, C context, T stateMachine) {
        for (UUID commandId : stateMachine.getPendingCommands()) {
            FlowSegmentRequestFactory removeRequest = stateMachine.getRemoveCommands().get(commandId);
            if (removeRequest != null) {
                stateMachine.saveErrorToHistory("Command is not finished yet",
                        format("Completing the %s operation although the remove command may not be "
                                        + "finished yet: commandId %s, switch %s, cookie %s", fsmOperationName,
                                commandId, removeRequest.getSwitchId(), removeRequest.getCookie()));
            } else {
                FlowSegmentRequestFactory installRequest = stateMachine.getInstallCommand(commandId);
                if (installRequest != null) {
                    stateMachine.saveErrorToHistory("Command is not finished yet",
                            format("Completing the %s operation although the install command may not be "
                                            + "finished yet: commandId %s, switch %s, cookie %s", fsmOperationName,
                                    commandId, installRequest.getSwitchId(), installRequest.getCookie()));
                } else {
                    stateMachine.saveErrorToHistory("Command is not finished yet",
                            format("Completing the %s operation although the command may not be "
                                            + "finished yet: commandId %s", fsmOperationName,
                                    commandId));
                }
            }
        }

        log.debug("Abandoning all pending commands: {}", stateMachine.getPendingCommands());
        stateMachine.resetPendingCommands();
    }
}
