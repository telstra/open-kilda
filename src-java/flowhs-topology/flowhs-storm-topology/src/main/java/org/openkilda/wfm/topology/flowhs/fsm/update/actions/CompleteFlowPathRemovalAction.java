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

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flow.model.FlowPathPair;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.BaseFlowPathRemovalAction;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateContext;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.update.FlowUpdateFsm.State;
import org.openkilda.wfm.topology.flowhs.mapper.RequestedFlowMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CompleteFlowPathRemovalAction extends
        BaseFlowPathRemovalAction<FlowUpdateFsm, State, Event, FlowUpdateContext> {

    public CompleteFlowPathRemovalAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(State from, State to, Event event, FlowUpdateContext context, FlowUpdateFsm stateMachine) {
        Flow originalFlow = RequestedFlowMapper.INSTANCE.toFlow(stateMachine.getOriginalFlow());
        removeOldPrimaryFlowPaths(originalFlow, stateMachine);
        removeOldProtectedFlowPaths(originalFlow, stateMachine);

        Flow flow = getFlow(stateMachine.getFlowId());
        removeRejectedFlowPaths(flow, stateMachine);
    }

    private void removeOldPrimaryFlowPaths(Flow originalFlow, FlowUpdateFsm stateMachine) {
        PathId oldPrimaryForwardPathId = stateMachine.getOldPrimaryForwardPath();
        PathId oldPrimaryReversePathId = stateMachine.getOldPrimaryReversePath();

        if (oldPrimaryForwardPathId != null || oldPrimaryReversePathId != null) {
            FlowPath oldPrimaryForward = flowPathRepository.remove(oldPrimaryForwardPathId).orElse(null);
            FlowPath oldPrimaryReverse = flowPathRepository.remove(oldPrimaryReversePathId).orElse(null);

            FlowPathPair removedPaths = null;
            if (oldPrimaryForward != null) {
                if (oldPrimaryReverse != null) {
                    log.debug("Removed the flow paths {} / {}", oldPrimaryForward, oldPrimaryReverse);
                    removedPaths = new FlowPathPair(oldPrimaryForward, oldPrimaryReverse);
                    updateIslsForFlowPath(removedPaths.getForward(), removedPaths.getReverse());
                } else {
                    log.debug("Removed the flow path {} (no reverse pair)", oldPrimaryForward);
                    // TODO: History dumps require paired paths, fix it to support any (without opposite one).
                    removedPaths = new FlowPathPair(oldPrimaryForward, oldPrimaryForward);
                    updateIslsForFlowPath(removedPaths.getForward());
                }
            } else if (oldPrimaryReverse != null) {
                log.debug("Removed the flow path {} (no forward pair)", oldPrimaryReverse);
                // TODO: History dumps require paired paths, fix it to support any (without opposite one).
                removedPaths = new FlowPathPair(oldPrimaryReverse, oldPrimaryReverse);
                updateIslsForFlowPath(removedPaths.getReverse());
            }
            if (removedPaths != null) {
                saveRemovalActionWithDumpToHistory(stateMachine, originalFlow, removedPaths);
            }
        }
    }

    private void removeOldProtectedFlowPaths(Flow originalFlow, FlowUpdateFsm stateMachine) {
        PathId oldProtectedForwardPathId = stateMachine.getOldProtectedForwardPath();
        PathId oldProtectedReversePathId = stateMachine.getOldProtectedReversePath();

        if (oldProtectedForwardPathId != null || oldProtectedReversePathId != null) {
            FlowPath oldProtectedForward = flowPathRepository.remove(oldProtectedForwardPathId).orElse(null);
            FlowPath oldProtectedReverse = flowPathRepository.remove(oldProtectedReversePathId).orElse(null);

            FlowPathPair removedPaths = null;
            if (oldProtectedForward != null) {
                if (oldProtectedReverse != null) {
                    log.debug("Removed the flow paths {} / {}", oldProtectedForward, oldProtectedReverse);
                    removedPaths = new FlowPathPair(oldProtectedForward, oldProtectedReverse);
                    updateIslsForFlowPath(removedPaths.getForward(), removedPaths.getReverse());
                } else {
                    log.debug("Removed the flow path {} (no reverse pair)", oldProtectedForward);
                    // TODO: History dumps require paired paths, fix it to support any (without opposite one).
                    removedPaths = new FlowPathPair(oldProtectedForward, oldProtectedForward);
                    updateIslsForFlowPath(removedPaths.getForward());
                }
            } else if (oldProtectedReverse != null) {
                log.debug("Removed the flow path {} (no forward pair)", oldProtectedReverse);
                // TODO: History dumps require paired paths, fix it to support any (without opposite one).
                removedPaths = new FlowPathPair(oldProtectedReverse, oldProtectedReverse);
                updateIslsForFlowPath(removedPaths.getReverse());
            }
            if (removedPaths != null) {
                saveRemovalActionWithDumpToHistory(stateMachine, originalFlow, removedPaths);
            }
        }
    }

    private void removeRejectedFlowPaths(Flow flow, FlowUpdateFsm stateMachine) {
        stateMachine.getRejectedPaths().stream()
                .forEach(pathId ->
                        flowPathRepository.remove(pathId)
                                .ifPresent(flowPath -> {
                                    updateIslsForFlowPath(flowPath);
                                    saveRemovalActionWithDumpToHistory(stateMachine, flow, flowPath);
                                }));
    }
}
