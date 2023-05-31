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

import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.info.flow.RemoveFlowCommand;
import org.openkilda.messaging.info.haflow.UpdateHaSubFlowCommand;
import org.openkilda.model.HaFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowProcessingWithHistorySupportFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.service.FlowGenericCarrier;

import java.util.Optional;

public class NotifyHaFlowMonitorAction<T extends FlowProcessingWithHistorySupportFsm<T, S, E, C, ?, ?>, S, E, C>
        extends FlowProcessingWithHistorySupportAction<T, S, E, C> {
    private final FlowGenericCarrier carrier;

    public NotifyHaFlowMonitorAction(PersistenceManager persistenceManager, FlowGenericCarrier carrier) {
        super(persistenceManager);
        this.carrier = carrier;
    }

    @Override
    protected void perform(S from, S to, E event, C context, T stateMachine) {
        String haFlowId = stateMachine.getFlowId();
        Optional<HaFlow> haFlowOptional = haFlowRepository.findById(haFlowId);
        if (!haFlowOptional.isPresent()) {
            carrier.sendNotifyFlowMonitor(new RemoveFlowCommand(haFlowId));
            return;
        }

        HaFlow haFlow = haFlowOptional.get();
        haFlow.getHaSubFlows()
                .forEach(haSubFlow -> carrier.sendNotifyFlowMonitor(getHaSubFlowInfo(haSubFlow.getHaSubFlowId(),
                        haFlow.getMaxLatency(), haFlow.getMaxLatencyTier2())));
    }

    private CommandData getHaSubFlowInfo(String haSubFlowId, Long maxLatency, Long maxLatencyTier2) {
        return new UpdateHaSubFlowCommand(haSubFlowId, maxLatency, maxLatencyTier2);
    }
}
