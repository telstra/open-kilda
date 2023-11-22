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

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.history.model.HaFlowEventData;
import org.openkilda.wfm.topology.flowhs.fsm.common.HaFlowProcessingFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.context.SpeakerResponseContext;
import org.openkilda.wfm.topology.flowhs.service.history.FlowHistoryService;

public class CreateNewHaFlowHistoryEventAction
        <T extends HaFlowProcessingFsm<T, S, E, C, ?, ?>, S, E, C extends SpeakerResponseContext>
        extends HaFlowProcessingWithHistorySupportAction<T, S, E, C> {

    private final HaFlowEventData.Event historyEvent;
    private final String actionTitle;

    public CreateNewHaFlowHistoryEventAction(PersistenceManager persistenceManager,
                                                HaFlowEventData.Event historyEvent) {
        super(persistenceManager);
        this.historyEvent = historyEvent;
        this.actionTitle = historyEvent.getDescription() + " operation has been started.";
    }

    public CreateNewHaFlowHistoryEventAction(PersistenceManager persistenceManager,
                                                HaFlowEventData.Event historyEvent,
                                                String actionTitle) {
        super(persistenceManager);
        this.historyEvent = historyEvent;
        this.actionTitle = actionTitle;
    }

    @Override
    protected void perform(S from, S to, E event, C context, T stateMachine) {
        FlowHistoryService flowHistoryService = FlowHistoryService.using(stateMachine.getCarrier());

        flowHistoryService.saveNewHaFlowEvent(HaFlowEventData.builder()
                .action(actionTitle)
                .event(historyEvent)
                .taskId(stateMachine.getCommandContext().getCorrelationId())
                .haFlowId(stateMachine.getHaFlowId())
                .build());
    }
}
