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

import static org.openkilda.wfm.share.history.model.HaFlowEventData.Initiator.AUTO;
import static org.openkilda.wfm.share.history.model.HaFlowEventData.Initiator.NB;

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.history.model.HaFlowEventData;
import org.openkilda.wfm.topology.flowhs.fsm.common.HaFlowPathSwappingFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.HaFlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.service.history.FlowHistoryService;

public class CreateNewHaHistoryEventForRerouteAction
        <T extends HaFlowPathSwappingFsm<T, S, E, C, ?, ?>, S, E, C extends HaFlowRerouteContext>
        extends HaFlowProcessingWithHistorySupportAction<T, S, E, C> {

    public CreateNewHaHistoryEventForRerouteAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(S from, S to, E event, C context, T stateMachine) {
        String rerouteReason = context.getRerouteReason();

        FlowHistoryService.using(stateMachine.getCarrier()).saveNewHaFlowEvent(HaFlowEventData.builder()
                .taskId(stateMachine.getCommandContext().getCorrelationId())
                .event(HaFlowEventData.Event.REROUTE)
                .haFlowId(stateMachine.getHaFlowId())
                .action(HaFlowEventData.Event.REROUTE +  " operation has been started.")
                .initiator(rerouteReason == null ? NB : AUTO)
                .details(rerouteReason == null ? null : "Reason: " + rerouteReason)
                .build());
    }
}
