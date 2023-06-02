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

import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.HaFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;

@Slf4j
public class OnNoPathFoundAction extends
        FlowProcessingWithHistorySupportAction<HaFlowRerouteFsm, State, Event, HaFlowRerouteContext> {
    private final FlowOperationsDashboardLogger dashboardLogger;
    private final boolean primaryPathNotFound;

    public OnNoPathFoundAction(PersistenceManager persistenceManager, FlowOperationsDashboardLogger dashboardLogger,
                               boolean primaryPathNotFound) {
        super(persistenceManager);
        this.dashboardLogger = dashboardLogger;
        this.primaryPathNotFound = primaryPathNotFound;
    }

    @Override
    protected void perform(
            State from, State to, Event event, HaFlowRerouteContext context, HaFlowRerouteFsm stateMachine) {
        String haFlowId = stateMachine.getHaFlowId();
        log.debug("Updating the HA-flow status of {} after 'no path found' event", haFlowId);

        FlowStatus flowStatus = transactionManager.doInTransaction(() -> {
            stateMachine.setOriginalFlowStatus(null);

            HaFlow haFlow = getHaFlow(haFlowId);
            if (primaryPathNotFound && stateMachine.isReroutePrimary() && stateMachine.getNewPrimaryPathIds() == null) {
                if (haFlow.getForwardPathId() == null && haFlow.getReversePathId() == null) {
                    log.debug("Skip marking HA-flow path statuses as inactive: HA-flow {} doesn't have main paths",
                            haFlowId);
                } else {
                    log.debug("Set the HA-flow path status of {}/{} to inactive",
                            haFlow.getForwardPathId(), haFlow.getReversePathId());
                    if (haFlow.getForwardPathId() != null) {
                        haFlow.getForwardPath().setStatus(FlowPathStatus.INACTIVE);
                        setInactiveStatusForSubPaths(haFlow.getForwardPath().getSubPaths());
                    }
                    if (haFlow.getReversePathId() != null) {
                        haFlow.getReversePath().setStatus(FlowPathStatus.INACTIVE);
                        setInactiveStatusForSubPaths(haFlow.getReversePath().getSubPaths());
                    }
                }
            }

            if (stateMachine.isRerouteProtected() && stateMachine.getNewProtectedPathIds() == null) {
                if (haFlow.getProtectedForwardPathId() == null && haFlow.getProtectedReversePathId() == null) {
                    log.debug("Skip marking HA-flow path statuses as inactive: HA-flow {} doesn't have a protected "
                                    + "paths", haFlowId);
                } else {
                    log.debug("Set the HA-flow path status of {}/{} to inactive",
                            haFlow.getProtectedForwardPathId(), haFlow.getProtectedReversePathId());
                    if (haFlow.getProtectedForwardPathId() != null) {
                        haFlow.getProtectedForwardPath().setStatus(FlowPathStatus.INACTIVE);
                        setInactiveStatusForSubPaths(haFlow.getProtectedForwardPath().getSubPaths());
                    }
                    if (haFlow.getProtectedReversePathId() != null) {
                        haFlow.getProtectedReversePath().setStatus(FlowPathStatus.INACTIVE);
                        setInactiveStatusForSubPaths(haFlow.getProtectedReversePath().getSubPaths());
                    }
                }
            }

            FlowStatus newFlowStatus = haFlow.computeStatus();
            if (newFlowStatus != FlowStatus.DOWN && newFlowStatus != FlowStatus.DEGRADED) {
                log.error("Computed unexpected status {} of HA-flow {} after 'no path found' event",
                        newFlowStatus, haFlow);
                newFlowStatus = FlowStatus.DOWN;
            }

            log.debug("Setting the HA-flow status of {} to {}", haFlowId, newFlowStatus);
            dashboardLogger.onFlowStatusUpdate(haFlowId, newFlowStatus);
            haFlow.setStatus(newFlowStatus);
            haFlow.setStatusInfo(stateMachine.getErrorReason());
            haFlow.recalculateHaSubFlowStatuses();
            stateMachine.setNewHaFlowStatus(newFlowStatus);
            return newFlowStatus;
        });

        stateMachine.saveActionToHistory(String.format("The HA-flow status was set to %s", flowStatus));
    }

    private void setInactiveStatusForSubPaths(Collection<FlowPath> subPaths) {
        if (subPaths != null) {
            for (FlowPath subPath : subPaths) {
                if (subPath != null) {
                    subPath.setStatus(FlowPathStatus.INACTIVE);
                }
            }
        }
    }
}
