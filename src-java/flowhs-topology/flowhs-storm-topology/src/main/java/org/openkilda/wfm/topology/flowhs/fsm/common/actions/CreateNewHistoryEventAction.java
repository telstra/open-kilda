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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions;

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowProcessingWithHistorySupportFsm;

/**
 * This action is useful to execute before any other actions in an FSM. This way you don't need to worry about
 * creating a new history event, and you can focus on the business logic.
 * @param <T> State machine type must support sending history, the persistenceManager and the Flow ID must be available
 * @param <S> State type is generic
 * @param <E> Event type is generic
 * @param <C> Context type is generic
 */
public class CreateNewHistoryEventAction<T extends FlowProcessingWithHistorySupportFsm<T, S, E, C, ?, ?>, S, E, C>
        extends FlowProcessingWithHistorySupportAction<T, S, E, C> {
    private final FlowEventData.Event historyEvent;
    private final String actionTitle;

    public CreateNewHistoryEventAction(PersistenceManager persistenceManager, FlowEventData.Event historyEvent) {
        super(persistenceManager);
        this.historyEvent = historyEvent;
        this.actionTitle = historyEvent.getDescription() + " operation has been started.";
    }

    public CreateNewHistoryEventAction(PersistenceManager persistenceManager, FlowEventData.Event historyEvent,
                                       String actionTitle) {
        super(persistenceManager);
        this.historyEvent = historyEvent;
        this.actionTitle = actionTitle;
    }

    @Override
    protected void perform(S from, S to, E event, C context, T stateMachine) {
        stateMachine.saveNewEventToHistory(actionTitle, historyEvent);
    }
}
