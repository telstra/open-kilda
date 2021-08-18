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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions;

import org.openkilda.messaging.info.stats.UpdateFlowPathInfo;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.mappers.FlowPathMapper;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowPathSwappingFsm;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;

import java.util.Optional;
import java.util.stream.Stream;

public class NotifyFlowStatsOnNewPathsAction<T extends FlowPathSwappingFsm<T, S, E, C>, S, E, C> extends
        FlowProcessingAction<T, S, E, C> {

    private FlowGenericCarrier carrier;

    public NotifyFlowStatsOnNewPathsAction(PersistenceManager persistenceManager, FlowGenericCarrier carrier) {
        super(persistenceManager);
        this.carrier = carrier;
    }

    @Override
    protected void perform(S from, S to, E event, C context, T stateMachine) {
        Stream.of(stateMachine.getNewPrimaryForwardPath(), stateMachine.getNewPrimaryReversePath(),
                        stateMachine.getNewProtectedForwardPath(), stateMachine.getNewProtectedReversePath())
                .map(flowPathRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(flowPath -> {
                    UpdateFlowPathInfo pathInfo = new UpdateFlowPathInfo(
                            flowPath.getFlowId(), flowPath.getCookie(), flowPath.getMeterId(),
                            FlowPathMapper.INSTANCE.mapToPathNodes(flowPath));
                    carrier.sendNotifyFlowStats(pathInfo);
                });
    }
}
