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
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.PathId;
import org.openkilda.persistence.FetchStrategy;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.PathSwappingAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SwapFlowPathsAction extends PathSwappingAction<FlowRerouteFsm, State, Event, FlowRerouteContext> {
    public SwapFlowPathsAction(PersistenceManager persistenceManager, FlowResourcesManager resourcesManager) {
        super(persistenceManager, resourcesManager);
    }

    @Override
    protected void perform(State from, State to, Event event, FlowRerouteContext context, FlowRerouteFsm stateMachine) {
        persistenceManager.getTransactionManager().doInTransaction(() -> {
            Flow flow = getFlow(stateMachine.getFlowId(), FetchStrategy.DIRECT_RELATIONS);

            final FlowEncapsulationType flowEncapsulationType = flow.getEncapsulationType();

            if (stateMachine.getNewPrimaryForwardPath() != null && stateMachine.getNewPrimaryReversePath() != null) {
                flowPathRepository.updateStatus(flow.getForwardPathId(), FlowPathStatus.IN_PROGRESS);
                flowPathRepository.updateStatus(flow.getReversePathId(), FlowPathStatus.IN_PROGRESS);

                FlowPath newForward = stateMachine.getNewPrimaryForwardPath().getPath();
                FlowPath newReverse = stateMachine.getNewPrimaryReversePath().getPath();

                log.debug("Swapping the primary paths {}/{} with {}/{}",
                          flow.getForwardPathId(), flow.getForwardPathId(),
                          newForward.getPathId(), newReverse.getPathId());

                saveOldPrimaryPaths(
                        stateMachine, flow, flow.getForwardPath(), flow.getReversePath(), flowEncapsulationType);

                flow.setForwardPath(newForward.getPathId());
                flow.setReversePath(newReverse.getPathId());

                saveHistory(stateMachine, flow.getFlowId(), newForward.getPathId(), newReverse.getPathId());
            }

            if (stateMachine.getNewProtectedForwardPath() != null
                    && stateMachine.getNewProtectedReversePath() != null) {
                flowPathRepository.updateStatus(flow.getProtectedForwardPathId(), FlowPathStatus.IN_PROGRESS);
                flowPathRepository.updateStatus(flow.getProtectedReversePathId(), FlowPathStatus.IN_PROGRESS);

                FlowPath newForward = stateMachine.getNewProtectedForwardPath().getPath();
                FlowPath newReverse = stateMachine.getNewProtectedReversePath().getPath();

                log.debug("Swapping the protected paths {}/{} with {}/{}",
                          flow.getProtectedForwardPathId(), flow.getProtectedReversePathId(),
                          newForward, newReverse);

                saveOldProtectedPaths(
                        stateMachine, flow, flow.getProtectedForwardPath(), flow.getProtectedReversePath(),
                        flowEncapsulationType);

                flow.setProtectedForwardPath(newForward);
                flow.setProtectedReversePath(newReverse);

                saveHistory(stateMachine, flow.getFlowId(), newForward.getPathId(), newReverse.getPathId());
            }

            if (stateMachine.getNewEncapsulationType() != null) {
                flow.setEncapsulationType(stateMachine.getNewEncapsulationType());
            }

            flowRepository.createOrUpdate(flow);
        });
    }

    private void saveHistory(FlowRerouteFsm stateMachine, String flowId, PathId forwardPath, PathId reversePath) {
        stateMachine.saveActionToHistory("Flow was updated with new paths",
                format("The flow %s was updated with paths %s / %s", flowId, forwardPath, reversePath));
    }
}
