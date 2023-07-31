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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.actions;

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.service.haflow.history.HaFlowHistory;
import org.openkilda.wfm.topology.flowhs.service.haflow.history.HaFlowHistoryService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InstallNonIngressRulesAction extends
        FlowProcessingWithHistorySupportAction<HaFlowRerouteFsm, State, Event, HaFlowRerouteContext> {
    public InstallNonIngressRulesAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(
            State from, State to, Event event, HaFlowRerouteContext context, HaFlowRerouteFsm stateMachine) {
        stateMachine.clearPendingAndRetriedAndFailedCommands();

        if (stateMachine.getNonIngressCommands().isEmpty()) {
            HaFlowHistoryService.using(stateMachine.getCarrier()).save(HaFlowHistory
                    .withTaskId(stateMachine.getCommandContext().getCorrelationId())
                    .withAction("No need to install ingress rules")
                    .withHaFlowId(stateMachine.getHaFlowId()));

            stateMachine.fire(Event.RULES_INSTALLED);
        } else {
            stateMachine.getNonIngressCommands().values().forEach(request -> {
                stateMachine.getCarrier().sendSpeakerRequest(request);
                stateMachine.addPendingCommand(request.getCommandId(), request.getSwitchId());
            });
            HaFlowHistoryService.using(stateMachine.getCarrier()).save(HaFlowHistory
                    .withTaskId(stateMachine.getCommandContext().getCorrelationId())
                    .withAction("Commands for installing non-ingress HA-flow rules have been sent")
                    .withHaFlowId(stateMachine.getHaFlowId()));
        }
    }
}
