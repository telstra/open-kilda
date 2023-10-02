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

import org.openkilda.wfm.share.flow.resources.HaFlowResources;
import org.openkilda.wfm.topology.flowhs.fsm.common.HaFlowPathSwappingFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.context.SpeakerResponseContext;
import org.openkilda.wfm.topology.flowhs.service.history.FlowHistoryService;
import org.openkilda.wfm.topology.flowhs.service.history.HaFlowHistory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HandleNotRevertedResourceAllocationAction<T extends HaFlowPathSwappingFsm<T, S, E, C, ?, ?>, S, E,
        C extends SpeakerResponseContext> extends HistoryRecordingAction<T, S, E, C> {

    @Override
    public void perform(S from, S to, E event, C context, T stateMachine) {
        HaFlowResources newPrimaryResources = stateMachine.getNewPrimaryResources();
        if (newPrimaryResources != null) {
            saveErrorToHistory(stateMachine, newPrimaryResources);
        }

        HaFlowResources newProtectedResources = stateMachine.getNewProtectedResources();
        if (newProtectedResources != null) {
            saveErrorToHistory(stateMachine, newProtectedResources);
        }
    }

    private void saveErrorToHistory(T stateMachine, HaFlowResources resourceType) {
        FlowHistoryService.using(stateMachine.getCarrier()).saveError(HaFlowHistory
                .of(stateMachine.getCommandContext().getCorrelationId())
                .withAction("Failed to revert resource allocation")
                .withDescription(format("Failed to revert resource allocation: %s", resourceType))
                .withHaFlowId(stateMachine.getHaFlowId()));
    }
}
