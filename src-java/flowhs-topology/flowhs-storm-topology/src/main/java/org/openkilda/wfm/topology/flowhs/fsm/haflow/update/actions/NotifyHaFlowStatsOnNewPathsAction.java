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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.update.actions;

import org.openkilda.messaging.info.stats.UpdateHaFlowPathInfo;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.mappers.FlowPathMapper;
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
        Stream.concat(stateMachine.getNewPrimaryPathIds().getAllSubPathIds().stream(),
                        Optional.ofNullable(stateMachine.getNewProtectedPathIds())
                                .map(haPathIdsPair -> haPathIdsPair.getAllSubPathIds().stream())
                                .orElse(Stream.empty()))
                .forEach(pathId -> flowPathRepository.findById(pathId).ifPresent(flowPath -> {
                    UpdateHaFlowPathInfo flowPathInfo = new UpdateHaFlowPathInfo(
                            flowPath.getHaFlowId(),
                            flowPath.getCookie(),
                            flowPath.isForward() ? flowPath.getHaFlowPath().getSharedPointMeterId()
                                    : flowPath.getMeterId(),
                            FlowPathMapper.INSTANCE.mapToPathNodes(stateMachine.getOriginalHaFlow(), flowPath),
                            false,
                            false,
                            flowPath.getCookie().getDirection() == FlowPathDirection.FORWARD
                                    ? flowPath.getHaFlowPath().getYPointGroupId() : null,
                            flowPath.getCookie().getDirection() == FlowPathDirection.REVERSE
                                    ? flowPath.getHaFlowPath().getYPointMeterId() : null,
                            flowPath.getHaSubFlowId(),
                            flowPath.getHaFlowPath().getYPointSwitchId(),
                            flowPath.getHaFlowPath().getSharedSwitchId());
                    carrier.sendNotifyFlowStats(flowPathInfo);
                }));
    }
}
