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

package org.openkilda.wfm.topology.flowhs.fsm.update.actions;

import static java.lang.String.format;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.PathId;
import org.openkilda.persistence.FetchStrategy;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RevertPathsSwapAction extends FlowProcessingAction<FlowUpdateFsm, State, Event, FlowUpdateContext> {
    public RevertPathsSwapAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(State from, State to, Event event, FlowUpdateContext context, FlowUpdateFsm stateMachine) {
        persistenceManager.getTransactionManager().doInTransaction(() -> {
            final Flow flow = getFlow(stateMachine.getFlowId(), FetchStrategy.DIRECT_RELATIONS);
            final Flow originalFlow = stateMachine.getOriginalFlow();

            FlowPath forwardPath = originalFlow.getForwardPath();
            FlowPath reversePath = originalFlow.getReversePath();
            if (forwardPath == null || reversePath == null) {
                throw new IllegalStateException(String.format(
                        "Original flow have no at least one of primary paths (forward %s and reverse %s)",
                        forwardPath == null ? "is missing" : "present",
                        reversePath == null ? "is missing" : "present"));
            }

            restoreOriginalPathStatus(forwardPath);
            restoreOriginalPathStatus(reversePath);

            log.debug("Swapping back the primary paths {}/{} with {}/{}",
                    flow.getForwardPathId(), flow.getReversePathId(),
                    forwardPath.getPathId(), reversePath.getPathId());

            flow.setForwardPath(forwardPath.getPathId());
            flow.setReversePath(reversePath.getPathId());

            saveHistory(stateMachine, flow.getFlowId(), forwardPath.getPathId(), reversePath.getPathId());

            FlowPath protectedForwardPath = originalFlow.getProtectedForwardPath();
            FlowPath protectedReversePath = originalFlow.getProtectedReversePath();
            if (protectedForwardPath != null && protectedReversePath != null) {
                restoreOriginalPathStatus(protectedForwardPath);
                restoreOriginalPathStatus(protectedReversePath);

                log.debug("Swapping back the protected paths {}/{} with {}/{}",
                        flow.getProtectedForwardPathId(), flow.getProtectedReversePathId(),
                        forwardPath.getPathId(), reversePath.getPathId());

                flow.setProtectedForwardPath(protectedForwardPath.getPathId());
                flow.setProtectedReversePath(protectedReversePath.getPathId());

                saveHistory(
                        stateMachine, flow.getFlowId(),
                        protectedForwardPath.getPathId(), protectedReversePath.getPathId());
            }

            flowRepository.createOrUpdate(flow);
        });
    }

    private void restoreOriginalPathStatus(FlowPath originalPath) {
        FlowPath path = getFlowPath(originalPath.getPathId());
        if (path.getStatus() != FlowPathStatus.ACTIVE) {
            flowPathRepository.updateStatus(path.getPathId(), originalPath.getStatus());
        }
    }

    private void saveHistory(FlowUpdateFsm stateMachine, String flowId, PathId forwardPath, PathId reversePath) {
        stateMachine.saveActionToHistory("Flow was reverted to old paths",
                format("The flow %s was updated with paths %s / %s", flowId, forwardPath, reversePath));
    }
}
