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

package org.openkilda.wfm.topology.flowhs.fsm.reroute.actions;

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.history.model.FlowEventData;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowProcessingWithHistorySupportFsm;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;

public class CreateNewHistoryEventForRerouteAction
        <T extends FlowProcessingWithHistorySupportFsm<T, S, E, C, ?, ?>, S, E, C extends FlowRerouteContext>
        extends FlowProcessingWithHistorySupportAction<T, S, E, C> {
    private final FlowEventData.Event historyEvent;
    private final String actionTitle;

    public CreateNewHistoryEventForRerouteAction(PersistenceManager persistenceManager,
                                                 FlowEventData.Event historyEvent) {
        super(persistenceManager);
        this.historyEvent = historyEvent;
        this.actionTitle = historyEvent.getDescription() + " operation has been started.";
    }

    public CreateNewHistoryEventForRerouteAction(PersistenceManager persistenceManager,
                                                 FlowEventData.Event historyEvent,
                                                 String actionTitle) {
        super(persistenceManager);
        this.historyEvent = historyEvent;
        this.actionTitle = actionTitle;
    }

    @Override
    protected void perform(S from, S to, E event, C context, T stateMachine) {
        String rerouteReason = context.getRerouteReason();

        stateMachine.saveNewEventToHistory(actionTitle, FlowEventData.Event.REROUTE,
                rerouteReason == null ? FlowEventData.Initiator.NB : FlowEventData.Initiator.AUTO,
                rerouteReason == null ? null : "Reason: " + rerouteReason);
    }
}
