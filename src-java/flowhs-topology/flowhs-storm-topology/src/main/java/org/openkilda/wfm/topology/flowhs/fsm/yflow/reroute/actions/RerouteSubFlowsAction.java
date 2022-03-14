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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.actions;

import org.openkilda.messaging.command.flow.FlowRerouteRequest;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.reroute.YFlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.service.FlowRerouteService;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

@Slf4j
public class RerouteSubFlowsAction extends HistoryRecordingAction<YFlowRerouteFsm, State, Event, YFlowRerouteContext> {
    private final FlowRerouteService flowRerouteService;

    public RerouteSubFlowsAction(FlowRerouteService flowRerouteService) {
        this.flowRerouteService = flowRerouteService;
    }

    @Override
    public void perform(State from, State to, Event event, YFlowRerouteContext context, YFlowRerouteFsm stateMachine) {
        Collection<FlowRerouteRequest> rerouteRequests = new ArrayList<>();
        Set<String> targetSubFlowIds = stateMachine.getTargetSubFlowIds();
        log.debug("Start rerouting {} sub-flows for y-flow {}", targetSubFlowIds.size(), stateMachine.getYFlowId());

        boolean isMainAffinityFlowRequestSent = false;
        String yFlowId = stateMachine.getYFlowId();
        for (String subFlowId : targetSubFlowIds) {
            stateMachine.addSubFlow(subFlowId);
            FlowRerouteRequest rerouteRequest = new FlowRerouteRequest(subFlowId, stateMachine.isForceReroute(),
                    false,
                    stateMachine.isIgnoreBandwidth(), stateMachine.getAffectedIsls(),
                    stateMachine.getRerouteReason(), false);
            rerouteRequests.add(rerouteRequest);

            if (subFlowId.equals(stateMachine.getMainAffinityFlowId())) {
                sendRerouteRequest(stateMachine, rerouteRequest, yFlowId);
                isMainAffinityFlowRequestSent = true;
            }
        }

        if (!isMainAffinityFlowRequestSent) {
            rerouteRequests.forEach(rerouteRequest -> sendRerouteRequest(stateMachine, rerouteRequest, yFlowId));
        }

        stateMachine.setRerouteRequests(rerouteRequests);
    }

    private void sendRerouteRequest(YFlowRerouteFsm stateMachine, FlowRerouteRequest rerouteRequest, String yFlowId) {
        String subFlowId = rerouteRequest.getFlowId();
        stateMachine.addReroutingSubFlow(subFlowId);
        stateMachine.notifyEventListeners(listener ->
                listener.onSubFlowProcessingStart(yFlowId, subFlowId));
        CommandContext flowContext = stateMachine.getCommandContext().fork(subFlowId);
        flowRerouteService.startFlowRerouting(rerouteRequest, flowContext, yFlowId);
    }
}
