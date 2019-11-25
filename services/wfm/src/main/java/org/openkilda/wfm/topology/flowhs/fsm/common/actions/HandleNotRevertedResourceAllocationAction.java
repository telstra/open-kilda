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

import org.openkilda.wfm.topology.flowhs.fsm.common.FlowPathSwappingFsm;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HandleNotRevertedResourceAllocationAction<T extends FlowPathSwappingFsm<T, S, E, C>, S, E, C>
        extends HistoryRecordingAction<T, S, E, C> {
    @Override
    public void perform(S from, S to, E event, C context, T stateMachine) {
        if (stateMachine.hasNewPrimaryResources()) {
            stateMachine.saveErrorToHistory("Failed to revert resource allocation",
                    format("Failed to revert resource allocation: %s", stateMachine.getNewPrimaryResources()));
        }

        if (stateMachine.hasNewProtectedResources()) {
            stateMachine.saveErrorToHistory("Failed to revert resource allocation",
                    format("Failed to revert resource allocation: %s", stateMachine.getNewProtectedResources()));
        }
    }
}
