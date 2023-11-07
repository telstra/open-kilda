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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.actions;

import static java.lang.String.format;

import org.openkilda.model.FlowStatus;
import org.openkilda.model.HaFlow;
import org.openkilda.persistence.repositories.HaFlowRepository;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HistoryRecordingAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.service.haflow.HaFlowRerouteHubCarrier;
import org.openkilda.wfm.topology.flowhs.service.history.FlowHistoryService;
import org.openkilda.wfm.topology.flowhs.service.history.HaFlowHistory;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class OnFinishedAction extends HistoryRecordingAction<HaFlowRerouteFsm, State, Event, HaFlowRerouteContext> {

    private final FlowOperationsDashboardLogger dashboardLogger;
    private final HaFlowRerouteHubCarrier carrier;
    private final HaFlowRepository haFlowRepository;

    public OnFinishedAction(FlowOperationsDashboardLogger dashboardLogger, HaFlowRerouteHubCarrier carrier,
                            HaFlowRepository haFlowRepository) {
        this.dashboardLogger = dashboardLogger;
        this.carrier = carrier;
        this.haFlowRepository = haFlowRepository;
    }

    @Override
    public void perform(
            State from, State to, Event event, HaFlowRerouteContext context, HaFlowRerouteFsm stateMachine) {
        HaFlowHistory haFlowHistory = HaFlowHistory.of(stateMachine.getCommandContext().getCorrelationId())
                .withHaFlowId(stateMachine.getHaFlowId());

        if (stateMachine.getNewHaFlowStatus() == FlowStatus.UP) {
            dashboardLogger.onSuccessfulHaFlowReroute(stateMachine.getHaFlowId());
            haFlowHistory.withAction("HA-flow has been rerouted successfully");

            sendPeriodicPingNotification(stateMachine);
        } else {
            haFlowHistory.withAction("HA-flow reroute completed")
                    .withDescription(format("HA-flow reroute completed with status %s and error %s",
                            stateMachine.getNewHaFlowStatus(), stateMachine.getErrorReason()));
        }
        log.info("HA-flow {} reroute success", stateMachine.getFlowId());
        carrier.sendRerouteResultStatus(stateMachine.getHaFlowId(), stateMachine.getRerouteError(),
                stateMachine.getCommandContext().getCorrelationId());

        saveHistoryWithDump(stateMachine, haFlowHistory);
    }

    private void sendPeriodicPingNotification(HaFlowRerouteFsm stateMachine) {
        stateMachine.getCarrier().sendPeriodicPingNotification(stateMachine.getFlowId(),
                stateMachine.isPeriodicPingsEnabled());
    }

    private void saveHistoryWithDump(HaFlowRerouteFsm stateMachine, HaFlowHistory haFlowHistory) {
        Optional<HaFlow> resultHaFlow = haFlowRepository.findById(stateMachine.getHaFlowId());
        if (resultHaFlow.isPresent()) {
            FlowHistoryService.using(stateMachine.getCarrier()).save(haFlowHistory
                    .withHaFlowDumpAfter(resultHaFlow.get()));
        } else {
            FlowHistoryService.using(stateMachine.getCarrier()).save(haFlowHistory);
        }
    }
}
