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

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.persistence.FetchStrategy;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.action.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OnNoPathFoundAction extends
        FlowProcessingAction<FlowRerouteFsm, State, Event, FlowRerouteContext> {

    public OnNoPathFoundAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(State from, State to, Event event, FlowRerouteContext context, FlowRerouteFsm stateMachine) {
        String flowId = stateMachine.getFlowId();
        log.debug("Set the flow status of {} to down.", flowId);

        persistenceManager.getTransactionManager().doInTransaction(() -> {
            flowRepository.updateStatus(flowId, FlowStatus.DOWN);
            stateMachine.setOriginalFlowStatus(null);

            Flow flow = getFlow(flowId, FetchStrategy.NO_RELATIONS);
            if (stateMachine.isReroutePrimary() && stateMachine.getNewPrimaryForwardPath() == null
                    && stateMachine.getNewPrimaryReversePath() == null) {
                log.debug("Set the flow path status of {}/{} to inactive.",
                        flow.getForwardPathId(), flow.getReversePathId());
                flowPathRepository.updateStatus(flow.getForwardPathId(), FlowPathStatus.INACTIVE);
                flowPathRepository.updateStatus(flow.getReversePathId(), FlowPathStatus.INACTIVE);
            }

            if (stateMachine.isRerouteProtected() && stateMachine.getNewProtectedForwardPath() == null
                    && stateMachine.getNewProtectedReversePath() == null) {
                log.debug("Set the flow path status of {}/{} to inactive.",
                        flow.getProtectedForwardPathId(), flow.getProtectedReversePathId());
                flowPathRepository.updateStatus(flow.getProtectedForwardPathId(), FlowPathStatus.INACTIVE);
                flowPathRepository.updateStatus(flow.getProtectedReversePathId(), FlowPathStatus.INACTIVE);
            }
        });

        saveHistory(stateMachine, stateMachine.getCarrier(), flowId,
                "Set the flow status to down.");
    }
}
