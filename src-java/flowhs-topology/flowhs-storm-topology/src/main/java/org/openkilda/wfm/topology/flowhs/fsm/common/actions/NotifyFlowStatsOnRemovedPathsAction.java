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

import org.openkilda.messaging.info.stats.RemoveFlowPathInfo;
import org.openkilda.model.Flow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.mappers.FlowPathMapper;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowPathSwappingFsm;
import org.openkilda.wfm.topology.flowhs.mapper.RequestedFlowMapper;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;

import java.util.Optional;
import java.util.stream.Stream;

public class NotifyFlowStatsOnRemovedPathsAction<T extends FlowPathSwappingFsm<T, S, E, C, ?, ?>, S, E, C> extends
        FlowProcessingWithHistorySupportAction<T, S, E, C> {
    private FlowGenericCarrier carrier;

    public NotifyFlowStatsOnRemovedPathsAction(PersistenceManager persistenceManager, FlowGenericCarrier carrier) {
        super(persistenceManager);
        this.carrier = carrier;
    }

    @Override
    protected void perform(S from, S to, E event, C context, T stateMachine) {
        Flow originalFlow = RequestedFlowMapper.INSTANCE.toFlow(stateMachine.getOriginalFlow());

        Stream.of(stateMachine.getOldPrimaryForwardPath(), stateMachine.getOldPrimaryReversePath(),
                        stateMachine.getOldProtectedForwardPath(), stateMachine.getOldProtectedReversePath())
                .map(flowPathRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(flowPath -> {
                    Flow flow = flowPath.getFlow();
                    RemoveFlowPathInfo pathInfo = new RemoveFlowPathInfo(
                            flow.getFlowId(), flow.getYFlowId(), flow.getYPointSwitchId(), flowPath.getCookie(),
                            flowPath.getMeterId(), FlowPathMapper.INSTANCE.mapToPathNodes(originalFlow, flowPath),
                            flow.getVlanStatistics(), flowPath.hasIngressMirror(), flowPath.hasEgressMirror());
                    carrier.sendNotifyFlowStats(pathInfo);
                });
    }
}
