/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.actions;

import static java.lang.String.format;
import static org.openkilda.wfm.topology.flowhs.utils.HaFlowUtils.getPathId;

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.EncapsulationResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.HaFlowResources;
import org.openkilda.wfm.share.metrics.TimedExecution;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.utils.HaFlowUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SwapFlowPathsAction extends
        FlowProcessingWithHistorySupportAction<HaFlowRerouteFsm, State, Event, HaFlowRerouteContext> {
    private final FlowResourcesManager resourcesManager;

    public SwapFlowPathsAction(PersistenceManager persistenceManager, FlowResourcesManager resourcesManager) {
        super(persistenceManager);
        this.resourcesManager = resourcesManager;
    }

    @TimedExecution("fsm.swap_ha_flow_paths")
    @Override
    protected void perform(
            State from, State to, Event event, HaFlowRerouteContext context, HaFlowRerouteFsm stateMachine) {
        swapPrimaryPaths(stateMachine);
        swapProtectedPaths(stateMachine);

        if (stateMachine.getNewEncapsulationType() != null) {
            transactionManager.doInTransaction(() -> {
                HaFlow haFlow = getHaFlow(stateMachine.getHaFlowId());
                haFlow.setEncapsulationType(stateMachine.getNewEncapsulationType());
            });
        }
    }

    private void swapPrimaryPaths(HaFlowRerouteFsm stateMachine) {
        if (stateMachine.getNewPrimaryPathIds() == null) {
            return;
        }
        PathId newForwardPathId = stateMachine.getNewPrimaryPathIds().getForward().getHaPathId();
        PathId newReversePathId = stateMachine.getNewPrimaryPathIds().getReverse().getHaPathId();
        if (newForwardPathId != null && newReversePathId != null) {
            transactionManager.doInTransaction(() -> {
                HaFlow haFlow = getHaFlow(stateMachine.getHaFlowId());

                HaFlowPath oldForward = haFlow.getForwardPath();
                if (oldForward != null) {
                    stateMachine.savePathsAndSetInProgressStatuses(oldForward);
                }

                HaFlowPath oldReverse = haFlow.getReversePath();
                if (oldReverse != null) {
                    stateMachine.savePathsAndSetInProgressStatuses(oldReverse);
                }

                if (oldForward != null || oldReverse != null) {
                    FlowEncapsulationType oldFlowEncapsulationType =
                            stateMachine.getOriginalHaFlow().getEncapsulationType();
                    HaFlowResources oldResources = buildHaResources(
                            oldForward != null ? oldForward : oldReverse,
                            oldReverse != null ? oldReverse : oldForward,
                            oldFlowEncapsulationType);
                    stateMachine.getOldResources().add(oldResources);
                }

                haFlow.setForwardPathId(newForwardPathId);
                haFlow.setReversePathId(newReversePathId);

                log.debug("Swapping the primary paths {}/{} with {}/{}",
                        getPathId(oldForward), getPathId(oldReverse), newForwardPathId, newReversePathId);
            });
            saveHistory(stateMachine, stateMachine.getFlowId(), newForwardPathId, newReversePathId);
        }
    }

    private void swapProtectedPaths(HaFlowRerouteFsm stateMachine) {
        if (stateMachine.getNewProtectedPathIds() == null) {
            return;
        }
        PathId newForwardPathId = stateMachine.getNewProtectedPathIds().getForward().getHaPathId();
        PathId newReversePathId = stateMachine.getNewProtectedPathIds().getReverse().getHaPathId();
        if (newForwardPathId != null && newReversePathId != null) {
            transactionManager.doInTransaction(() -> {
                HaFlow haFlow = getHaFlow(stateMachine.getFlowId());
                HaFlowPath oldForward = haFlow.getProtectedForwardPath();
                if (oldForward != null) {
                    stateMachine.savePathsAndSetInProgressStatuses(oldForward);
                }

                HaFlowPath oldReverse = haFlow.getProtectedReversePath();
                if (oldReverse != null) {
                    stateMachine.savePathsAndSetInProgressStatuses(oldReverse);
                }

                if (oldForward != null || oldReverse != null) {
                    FlowEncapsulationType oldFlowEncapsulationType =
                            stateMachine.getOriginalHaFlow().getEncapsulationType();
                    HaFlowResources oldProtectedResources = buildHaResources(
                            oldForward != null ? oldForward : oldReverse,
                            oldReverse != null ? oldReverse : oldForward,
                            oldFlowEncapsulationType);
                    stateMachine.getOldResources().add(oldProtectedResources);
                }

                haFlow.setProtectedForwardPathId(newForwardPathId);
                haFlow.setProtectedReversePathId(newReversePathId);

                log.debug("Swapping the protected paths {}/{} with {}/{}",
                        getPathId(oldForward), getPathId(oldReverse), newForwardPathId, newReversePathId);
            });
            saveHistory(stateMachine, stateMachine.getFlowId(), newForwardPathId, newReversePathId);
        }
    }

    private HaFlowResources buildHaResources(
            HaFlowPath forwardPath, HaFlowPath reversePath, FlowEncapsulationType encapsulationType) {
        EncapsulationResources encapsulationResources = resourcesManager.getEncapsulationResources(
                forwardPath.getHaPathId(), reversePath.getHaPathId(), encapsulationType).orElse(null);
        return HaFlowUtils.buildHaResources(forwardPath, reversePath, encapsulationResources);
    }


    private void saveHistory(HaFlowRerouteFsm stateMachine, String flowId, PathId forwardPath, PathId reversePath) {
        stateMachine.saveActionToHistory("HA-flow was updated with new paths",
                format("The HA-flow %s was updated with paths %s / %s", flowId, forwardPath, reversePath));
    }
}
