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

import org.openkilda.model.Flow;
import org.openkilda.model.FlowStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RevertFlowModificationsAction
        extends FlowProcessingAction<FlowRerouteFsm, State, Event, FlowRerouteContext> {
    public RevertFlowModificationsAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(State from, State to, Event event, FlowRerouteContext context, FlowRerouteFsm stateMachine) {
        String flowId = stateMachine.getFlowId();

        Flow flow = getFlow(flowId);
        Flow originalFlow = stateMachine.getOriginalFlow();
        flow.setEncapsulationType(originalFlow.getEncapsulationType());

        FlowStatus originalStatus = stateMachine.getOriginalFlowStatus();
        if (originalStatus != null) {
            log.debug("Reverting the flow status of {} to {}", flowId, originalStatus);

            flow.setStatus(originalStatus);
            stateMachine.saveActionToHistory(format("The flow status was reverted to %s", originalStatus));
        }

        flowRepository.createOrUpdate(flow);
    }
}
