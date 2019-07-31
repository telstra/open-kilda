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
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.PathId;
import org.openkilda.persistence.FetchStrategy;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.history.model.FlowHistoryData;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.topology.flowhs.fsm.common.action.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.reroute.FlowRerouteFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

@Slf4j
public class RevertPathsSwapAction extends
        FlowProcessingAction<FlowRerouteFsm, State, Event, FlowRerouteContext> {

    public RevertPathsSwapAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(State from, State to,
                           Event event, FlowRerouteContext context, FlowRerouteFsm stateMachine) {
        persistenceManager.getTransactionManager().doInTransaction(() -> {
            Flow flow = getFlow(stateMachine.getFlowId(), FetchStrategy.DIRECT_RELATIONS);

            if (stateMachine.getNewEncapsulationType() != null) {
                flow.setEncapsulationType(stateMachine.getOriginalEncapsulationType());
            }

            if (stateMachine.getOldPrimaryForwardPath() != null && stateMachine.getOldPrimaryReversePath() != null) {
                FlowPath oldForward = getFlowPath(stateMachine.getOldPrimaryForwardPath());
                if (oldForward.getStatus() != FlowPathStatus.ACTIVE) {
                    flowPathRepository.updateStatus(oldForward.getPathId(),
                            stateMachine.getOldPrimaryForwardPathStatus());
                }

                FlowPath oldReverse = getFlowPath(stateMachine.getOldPrimaryReversePath());
                if (oldReverse.getStatus() != FlowPathStatus.ACTIVE) {
                    flowPathRepository.updateStatus(oldReverse.getPathId(),
                            stateMachine.getOldPrimaryReversePathStatus());
                }

                log.debug("Swapping back the primary paths {}/{} with {}/{}",
                        flow.getForwardPath().getPathId(), flow.getReversePath().getPathId(),
                        oldForward.getPathId(), oldReverse.getPathId());

                flow.setForwardPath(oldForward.getPathId());
                flow.setReversePath(oldReverse.getPathId());

                saveHistory(stateMachine, flow.getFlowId(), oldForward.getPathId(), oldReverse.getPathId());
            }

            if (stateMachine.getOldProtectedForwardPath() != null
                    && stateMachine.getOldProtectedReversePath() != null) {
                FlowPath oldForward = getFlowPath(stateMachine.getOldProtectedForwardPath());
                if (oldForward.getStatus() != FlowPathStatus.ACTIVE) {
                    flowPathRepository.updateStatus(oldForward.getPathId(),
                            stateMachine.getOldProtectedForwardPathStatus());
                }

                FlowPath oldReverse = getFlowPath(stateMachine.getOldProtectedReversePath());
                if (oldReverse.getStatus() != FlowPathStatus.ACTIVE) {
                    flowPathRepository.updateStatus(oldReverse.getPathId(),
                            stateMachine.getOldProtectedReversePathStatus());
                }

                log.debug("Swapping back the protected paths {}/{} with {}/{}",
                        flow.getProtectedForwardPath().getPathId(), flow.getProtectedReversePath().getPathId(),
                        oldForward.getPathId(), oldReverse.getPathId());

                flow.setProtectedForwardPath(oldForward.getPathId());
                flow.setProtectedReversePath(oldReverse.getPathId());

                saveHistory(stateMachine, flow.getFlowId(), oldForward.getPathId(), oldReverse.getPathId());
            }

            flowRepository.createOrUpdate(flow);
        });
    }

    private void saveHistory(FlowRerouteFsm stateMachine, String flowId, PathId forwardPath, PathId reversePath) {
        FlowHistoryHolder historyHolder = FlowHistoryHolder.builder()
                .taskId(stateMachine.getCommandContext().getCorrelationId())
                .flowHistoryData(FlowHistoryData.builder()
                        .action("Flow was updated with new paths")
                        .time(Instant.now())
                        .description(format("Flow %s was updated with paths %s/%s", flowId,
                                forwardPath, reversePath))
                        .flowId(flowId)
                        .build())
                .build();
        stateMachine.getCarrier().sendHistoryUpdate(historyHolder);
    }
}
