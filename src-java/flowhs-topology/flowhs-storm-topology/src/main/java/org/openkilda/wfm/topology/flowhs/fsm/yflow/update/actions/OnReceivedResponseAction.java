/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.yflow.update.actions;

import org.openkilda.messaging.Message;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.yflow.update.YFlowUpdateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class OnReceivedResponseAction extends
        NbTrackableWithHistorySupportAction<YFlowUpdateFsm, State, Event, YFlowUpdateContext> {
    public OnReceivedResponseAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected Optional<Message> performWithResponse(State from, State to, Event event, YFlowUpdateContext context,
                                                    YFlowUpdateFsm stateMachine) {
        String flowId = context.getSubFlowId();

        if (stateMachine.isFailedSubFlow(flowId)) {
            log.debug("Ignore an error event for already failed sub-flow " + flowId);

            return Optional.empty();
        }

        if (!stateMachine.isUpdatingSubFlow(flowId)) {
            throw new IllegalStateException("Received an event for non-pending sub-flow " + flowId);
        }

        stateMachine.removeUpdatingSubFlow(flowId);

        if (stateMachine.getUpdatingSubFlows().isEmpty()) {
            stateMachine.fire(Event.REVERT_YFLOW);
        }

        return Optional.empty();
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not update y-flow";
    }
}
