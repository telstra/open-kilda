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

import static org.openkilda.wfm.topology.flowhs.utils.HaFlowUtils.buildUpdateHaFlowPathInfo;

import org.openkilda.messaging.info.stats.UpdateHaFlowPathInfo;
import org.openkilda.model.FlowPath;
import org.openkilda.model.HaFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.HaFlowPathSwappingFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.common.context.SpeakerResponseContext;
import org.openkilda.wfm.topology.flowhs.service.haflow.HaFlowGenericCarrier;

import java.util.Optional;
import java.util.stream.Stream;

public class NotifyHaFlowStatsOnNewPathsAction<T extends HaFlowPathSwappingFsm<T, S, E, C, ?, ?>, S, E, C
        extends SpeakerResponseContext> extends FlowProcessingWithHistorySupportAction<T, S, E, C> {
    private final HaFlowGenericCarrier carrier;

    public NotifyHaFlowStatsOnNewPathsAction(PersistenceManager persistenceManager, HaFlowGenericCarrier carrier) {
        super(persistenceManager);
        this.carrier = carrier;
    }

    @Override
    protected void perform(S from, S to, E event, C context, T stateMachine) {
        haFlowRepository.findById(stateMachine.getHaFlowId()).ifPresent(haFlow ->
                Stream.concat(stateMachine.getNewPrimaryPathIds().getAllSubPathIds().stream(),
                                Optional.ofNullable(stateMachine.getNewProtectedPathIds())
                                        .map(haPathIdsPair -> haPathIdsPair.getAllSubPathIds().stream())
                                        .orElse(Stream.empty()))
                        .forEach(pathId -> flowPathRepository.findById(pathId)
                                .ifPresent(flowPath -> sendUpdateHaFlowPathInfo(haFlow, flowPath))));
    }

    private void sendUpdateHaFlowPathInfo(HaFlow haFlow, FlowPath flowPath) {
        UpdateHaFlowPathInfo updateHaFlowPathInfo = buildUpdateHaFlowPathInfo(flowPath, haFlow);
        carrier.sendNotifyFlowStats(updateHaFlowPathInfo);
    }
}
