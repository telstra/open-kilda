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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.actions;

import org.openkilda.messaging.Message;
import org.openkilda.model.HaFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.HaFlowPathSwapContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.HaFlowPathSwapFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.HaFlowPathSwapFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.pathswap.HaFlowPathSwapFsm.State;
import org.openkilda.wfm.topology.flowhs.service.haflow.history.HaFlowHistory;
import org.openkilda.wfm.topology.flowhs.service.haflow.history.HaFlowHistoryService;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class UpdateHaFlowPathsAction extends
        NbTrackableWithHistorySupportAction<HaFlowPathSwapFsm, State, Event, HaFlowPathSwapContext> {
    public UpdateHaFlowPathsAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, HaFlowPathSwapContext context,
                                                    HaFlowPathSwapFsm stateMachine) {

        HaFlow haFlow = transactionManager.doInTransaction(() -> {
            String haFlowId = stateMachine.getHaFlowId();
            HaFlow foundHaFlow = getHaFlow(haFlowId);

            log.debug("Swapping primary and protected paths for HA-flow {}", haFlowId);

            foundHaFlow.swapPathIds();
            return foundHaFlow;
        });

        saveActionToHistory(stateMachine);
        CommandContext commandContext = stateMachine.getCommandContext();
        return Optional.of(buildResponseMessage(haFlow, commandContext));
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not swap HA-flow paths";
    }

    private void saveActionToHistory(HaFlowPathSwapFsm stateMachine) {
        HaFlowHistoryService.using(stateMachine.getCarrier()).save(HaFlowHistory
                .withTaskId(stateMachine.getCommandContext().getCorrelationId())
                .withAction("The HA-flow paths were updated")
                .withHaFlowId(stateMachine.getHaFlowId()));
    }
}
