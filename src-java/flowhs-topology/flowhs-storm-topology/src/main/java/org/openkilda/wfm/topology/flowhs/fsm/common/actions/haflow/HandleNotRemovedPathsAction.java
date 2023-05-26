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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow;

import static java.lang.String.format;

import org.openkilda.model.PathId;
import org.openkilda.wfm.topology.flowhs.fsm.common.HaFlowPathSwappingFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.context.SpeakerResponseContext;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class HandleNotRemovedPathsAction<T extends HaFlowPathSwappingFsm<T, S, E, C, ?, ?>, S, E,
        C extends SpeakerResponseContext> extends HistoryRecordingAction<T, S, E, C> {

    @Override
    public void perform(S from, S to, E event, C context, T stateMachine) {
        List<PathId> subPathIds = new ArrayList<>();
        List<PathId> haFlowPathIds = new ArrayList<>();
        if (stateMachine.getOldPrimaryPathIds() != null) {
            subPathIds.addAll(stateMachine.getOldPrimaryPathIds().getAllSubPathIds());
            haFlowPathIds.addAll(stateMachine.getOldPrimaryPathIds().getAllHaFlowPathIds());
        }
        if (stateMachine.getOldProtectedPathIds() != null) {
            subPathIds.addAll(stateMachine.getOldProtectedPathIds().getAllSubPathIds());
            haFlowPathIds.addAll(stateMachine.getOldProtectedPathIds().getAllHaFlowPathIds());
        }

        for (PathId subPathId : subPathIds) {
            if (subPathId != null) {
                stateMachine.saveErrorToHistory(format("Failed to remove the ha sub path %s", subPathId));
            }
        }
        for (PathId haFlowPathId : haFlowPathIds) {
            if (haFlowPathId != null) {
                stateMachine.saveErrorToHistory(format("Failed to remove the ha flow path %s", haFlowPathId));
            }
        }
    }
}
