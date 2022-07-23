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

package org.openkilda.wfm.topology.flowhs.fsm.pathswap.actions;

import static java.lang.String.format;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapContext;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.pathswap.FlowPathSwapFsm.State;
import org.openkilda.wfm.topology.flowhs.model.FlowPathPair;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RevertPathsSwapAction extends
        FlowProcessingWithHistorySupportAction<FlowPathSwapFsm, State, Event, FlowPathSwapContext> {
    public RevertPathsSwapAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(State from, State to, Event event, FlowPathSwapContext context,
                           FlowPathSwapFsm stateMachine) {
        FlowPathPair pathPair = transactionManager.doInTransaction(() -> {
            String flowId = stateMachine.getFlowId();
            Flow flow = getFlow(flowId);

            log.debug("Reverting primary and protected paths swap for flow {}", flowId);

            FlowPath oldPrimaryForward = flow.getForwardPath();
            FlowPath oldPrimaryReverse = flow.getReversePath();
            PathId newPrimaryForward = flow.getProtectedForwardPathId();
            PathId newPrimaryReverse = flow.getProtectedReversePathId();

            setMirrorPointsToNewPath(oldPrimaryForward.getPathId(), newPrimaryForward);
            setMirrorPointsToNewPath(oldPrimaryReverse.getPathId(), newPrimaryReverse);

            flow.setForwardPathId(newPrimaryForward);
            flow.setReversePathId(newPrimaryReverse);
            flow.setProtectedForwardPathId(oldPrimaryForward.getPathId());
            flow.setProtectedReversePathId(oldPrimaryReverse.getPathId());
            return new FlowPathPair(oldPrimaryForward, oldPrimaryReverse);
        });

        saveHistory(stateMachine, stateMachine.getFlowId(), pathPair.getForwardPathId(), pathPair.getReversePathId());
    }

    private void saveHistory(FlowPathSwapFsm stateMachine, String flowId, PathId forwardPath, PathId reversePath) {
        stateMachine.saveActionToHistory("Flow was reverted to old paths",
                format("The flow %s was updated with paths %s / %s", flowId, forwardPath, reversePath));
    }
}
