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

package org.openkilda.wfm.topology.flowhs.fsm.reroute.actions;

import static java.lang.String.format;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowStatus;
import org.openkilda.persistence.FetchStrategy;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.NbTrackableAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;

import java.util.Optional;

public class LockFlowAction
        extends NbTrackableAction<FlowRerouteFsm, FlowRerouteFsm.State, FlowRerouteFsm.Event, FlowRerouteContext> {
    public LockFlowAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected Optional<Message> performWithResponse(FlowRerouteFsm.State from, FlowRerouteFsm.State to,
                                                    FlowRerouteFsm.Event event, FlowRerouteContext context,
                                                    FlowRerouteFsm stateMachine) {
        final String flowId = stateMachine.getFlowId();
        persistenceManager.getTransactionManager().doInTransaction(() -> {
            Flow foundFlow = getFlow(flowId, FetchStrategy.DIRECT_RELATIONS);
            if (foundFlow.getStatus() == FlowStatus.IN_PROGRESS) {
                throw new FlowProcessingException(
                        ErrorType.REQUEST_INVALID, format("Flow %s is in progress now", flowId));
            }

            // grab original flow state before any modifications
            stateMachine.setOriginalFlow(new Flow(foundFlow));
            flowRepository.updateStatus(foundFlow.getFlowId(), FlowStatus.IN_PROGRESS);
        });

        return Optional.empty();
    }

    @Override
    protected String getGenericErrorMessage() {
        return "Could not lock flow for reroute (flow already busy)";
    }
}
