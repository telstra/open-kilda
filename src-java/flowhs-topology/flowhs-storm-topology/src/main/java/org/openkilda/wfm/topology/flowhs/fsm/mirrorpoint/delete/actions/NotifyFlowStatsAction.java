/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.actions;

import org.openkilda.messaging.info.stats.RemoveFlowPathInfo;
import org.openkilda.model.Flow;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.mappers.FlowPathMapper;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm.State;

import java.util.Map;
import java.util.stream.Collectors;

public class NotifyFlowStatsAction extends FlowProcessingWithHistorySupportAction<
        FlowMirrorPointDeleteFsm, State, Event, FlowMirrorPointDeleteContext> {

    public NotifyFlowStatsAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(State from, State to, Event event, FlowMirrorPointDeleteContext context,
                           FlowMirrorPointDeleteFsm stateMachine) {
        flowPathRepository.findById(stateMachine.getFlowPathId()).ifPresent(flowPath -> {
            Flow flow = flowPath.getFlow();
            Map<Long, SwitchId> switchIdByGroupId = flowPath.getFlowMirrorPointsSet().stream()
                    .collect(Collectors.toMap(key -> key.getMirrorGroup().getGroupId().getValue(),
                            val -> val.getMirrorGroup().getSwitchId()));
            RemoveFlowPathInfo pathInfo = new RemoveFlowPathInfo(
                    flow.getFlowId(), flow.getYFlowId(), flow.getYPointSwitchId(), flowPath.getCookie(),
                    flowPath.getMeterId(), switchIdByGroupId, FlowPathMapper.INSTANCE.mapToPathNodes(flow, flowPath),
                    flow.getVlanStatistics(), flowPath.hasIngressMirror(), flowPath.hasEgressMirror());
            stateMachine.getCarrier().sendNotifyFlowStats(pathInfo);
        });
    }
}
