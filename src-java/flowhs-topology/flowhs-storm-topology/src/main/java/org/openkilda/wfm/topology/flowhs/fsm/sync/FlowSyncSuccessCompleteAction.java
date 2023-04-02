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

package org.openkilda.wfm.topology.flowhs.fsm.sync;

import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowRerouteResponse;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.share.mappers.FlowPathMapper;
import org.openkilda.wfm.topology.flowhs.fsm.sync.FlowSyncFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.sync.FlowSyncFsm.State;
import org.openkilda.wfm.topology.flowhs.service.FlowSyncCarrier;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FlowSyncSuccessCompleteAction extends SuccessCompleteActionBase<FlowSyncFsm, State, Event> {
    public FlowSyncSuccessCompleteAction(
            @NonNull PersistenceManager persistenceManager, @NonNull FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager, dashboardLogger);
    }

    @Override
    protected void updateStatus(FlowSyncFsm stateMachine) {
        Flow flow = getFlow(stateMachine.getFlowId());
        if (! updateFlowStatus(flow)) {
            stateMachine.fireError();
        }

        sendResponse(flow.getForwardPath(), stateMachine.getCarrier(), stateMachine.getCommandContext());
        dashboardLogger.onSuccessfulFlowSync(flow.getFlowId());
    }

    private void sendResponse(FlowPath path, FlowSyncCarrier carrier, CommandContext commandContext) {
        // Setting "rerouted" payload field into false, because paths are always kept unchanged now
        FlowRerouteResponse payload = new FlowRerouteResponse(FlowPathMapper.INSTANCE.map(path), false);
        carrier.sendNorthboundResponse(
                new InfoMessage(payload, System.currentTimeMillis(), commandContext.getCorrelationId()));
    }
}
