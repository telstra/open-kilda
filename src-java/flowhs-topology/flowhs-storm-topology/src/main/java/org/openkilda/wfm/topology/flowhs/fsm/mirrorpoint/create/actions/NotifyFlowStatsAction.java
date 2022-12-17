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

package org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.actions;

import org.openkilda.messaging.info.stats.UpdateFlowPathInfo;
import org.openkilda.model.Flow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.mappers.FlowPathMapper;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.State;

public class NotifyFlowStatsAction extends FlowProcessingWithHistorySupportAction<FlowMirrorPointCreateFsm,
        FlowMirrorPointCreateFsm.State, FlowMirrorPointCreateFsm.Event, FlowMirrorPointCreateContext> {

    public NotifyFlowStatsAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(FlowMirrorPointCreateFsm.State from, State to, Event event,
                           FlowMirrorPointCreateContext context, FlowMirrorPointCreateFsm stateMachine) {
        flowPathRepository.findById(stateMachine.getFlowPathId()).ifPresent(flowPath -> {
            Flow flow = flowPath.getFlow();
            UpdateFlowPathInfo pathInfo = new UpdateFlowPathInfo(
                    flow.getFlowId(), flow.getYFlowId(), flow.getYPointSwitchId(), flowPath.getCookie(),
                    flowPath.getMeterId(), FlowPathMapper.INSTANCE.mapToPathNodes(flow, flowPath),
                    flow.getVlanStatistics(), flowPath.hasIngressMirror(), flowPath.hasEgressMirror());
            stateMachine.getCarrier().sendNotifyFlowStats(pathInfo);
        });
    }
}
