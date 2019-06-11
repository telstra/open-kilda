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
import org.openkilda.model.FlowPath;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.stream.Stream;

@Slf4j
public class CompleteFlowPathRemovalAction extends BaseFlowPathRemovalAction {

    public CompleteFlowPathRemovalAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(State from, State to,
                           Event event, FlowRerouteContext context, FlowRerouteFsm stateMachine) {
        transactionManager.doInTransaction(() -> {
            Flow flow = getFlow(stateMachine.getFlowId());

            FlowPath oldPrimaryForward = null;
            FlowPath oldPrimaryReverse = null;
            if (stateMachine.getOldPrimaryForwardPath() != null && stateMachine.getOldPrimaryReversePath() != null) {
                oldPrimaryForward = getFlowPath(flow, stateMachine.getOldPrimaryForwardPath());
                oldPrimaryReverse = getFlowPath(flow, stateMachine.getOldPrimaryReversePath());
            }
            FlowPath oldProtectedForward = null;
            FlowPath oldProtectedReverse = null;
            if (stateMachine.getOldProtectedForwardPath() != null
                    && stateMachine.getOldProtectedReversePath() != null) {
                oldProtectedForward = getFlowPath(flow, stateMachine.getOldProtectedForwardPath());
                oldProtectedReverse = getFlowPath(flow, stateMachine.getOldProtectedReversePath());
            }

            flowPathRepository.lockInvolvedSwitches(Stream.of(oldPrimaryForward, oldPrimaryReverse,
                    oldProtectedForward, oldProtectedReverse).filter(Objects::nonNull).toArray(FlowPath[]::new));

            if (oldPrimaryForward != null && oldPrimaryReverse != null) {
                log.debug("Completing removal of the flow path {} / {}", oldPrimaryForward, oldPrimaryReverse);
                deleteFlowPaths(oldPrimaryForward, oldPrimaryReverse);

                saveHistory(flow, oldPrimaryForward, oldPrimaryReverse, stateMachine);
            }

            if (oldProtectedForward != null && oldProtectedReverse != null) {
                log.debug("Completing removal of the flow path {} / {}", oldProtectedForward, oldProtectedReverse);
                deleteFlowPaths(oldProtectedForward, oldProtectedReverse);

                saveHistory(flow, oldProtectedForward, oldProtectedReverse, stateMachine);
            }
        });
    }
}
