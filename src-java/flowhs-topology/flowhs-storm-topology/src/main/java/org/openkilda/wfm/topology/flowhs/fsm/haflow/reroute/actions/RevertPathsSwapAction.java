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
import static org.openkilda.wfm.topology.flowhs.utils.HaFlowUtils.getForwardPathId;
import static org.openkilda.wfm.topology.flowhs.utils.HaFlowUtils.getForwardSubPathIds;
import static org.openkilda.wfm.topology.flowhs.utils.HaFlowUtils.getReversePathId;
import static org.openkilda.wfm.topology.flowhs.utils.HaFlowUtils.getReverseSubPathIds;

import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.HaFlow;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.HaFlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.reroute.HaFlowRerouteFsm.State;
import org.openkilda.wfm.topology.flowhs.service.history.FlowHistoryService;
import org.openkilda.wfm.topology.flowhs.service.history.HaFlowHistory;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;

@Slf4j
public class RevertPathsSwapAction extends
        HaFlowProcessingWithHistorySupportAction<HaFlowRerouteFsm, State, Event, HaFlowRerouteContext> {
    public static final String NO_PATH = "no path";

    public RevertPathsSwapAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
    }

    @Override
    protected void perform(
            State from, State to, Event event, HaFlowRerouteContext context, HaFlowRerouteFsm stateMachine) {
        transactionManager.doInTransaction(() -> {
            HaFlow haFlow = getHaFlow(stateMachine.getHaFlowId());
            if (stateMachine.getNewEncapsulationType() != null) {
                haFlow.setEncapsulationType(stateMachine.getOriginalHaFlow().getEncapsulationType());
            }

            updatePrimaryPaths(stateMachine, haFlow);
            updateProtectedPaths(stateMachine, haFlow);
        });
    }

    private void updatePrimaryPaths(HaFlowRerouteFsm stateMachine, HaFlow haFlow) {
        if (stateMachine.getNewPrimaryPathIds() == null) {
            return;
        }

        if (getForwardPathId(stateMachine.getNewPrimaryPathIds()) != null) {
            PathId oldPrimaryForwardPathId = getForwardPathId(stateMachine.getOldPrimaryPathIds());
            updatePathStatus(stateMachine, oldPrimaryForwardPathId);
            updateSubPathStatuses(stateMachine, getForwardSubPathIds(stateMachine.getOldPrimaryPathIds()));

            log.debug("Swapping back the primary forward path {} with {}",
                    haFlow.getForwardPathId(), oldPrimaryForwardPathId != null ? oldPrimaryForwardPathId : NO_PATH);
            haFlow.setForwardPathId(oldPrimaryForwardPathId);
            saveHistory(stateMachine, oldPrimaryForwardPathId);
        }

        if (getReversePathId(stateMachine.getNewPrimaryPathIds()) != null) {
            PathId oldPrimaryReversePathId = getReversePathId(stateMachine.getOldPrimaryPathIds());
            updatePathStatus(stateMachine, oldPrimaryReversePathId);
            updateSubPathStatuses(stateMachine, getReverseSubPathIds(stateMachine.getOldPrimaryPathIds()));

            log.debug("Swapping back the primary reverse path {} with {}",
                    haFlow.getReversePathId(), oldPrimaryReversePathId != null ? oldPrimaryReversePathId : NO_PATH);

            haFlow.setReversePathId(oldPrimaryReversePathId);
            saveHistory(stateMachine, oldPrimaryReversePathId);
        }
    }

    private void updateProtectedPaths(HaFlowRerouteFsm stateMachine, HaFlow haFlow) {
        if (stateMachine.getNewProtectedPathIds() == null) {
            return;
        }

        if (getForwardPathId(stateMachine.getNewProtectedPathIds()) != null) {
            PathId oldForwardPathId = getForwardPathId(stateMachine.getOldProtectedPathIds());
            updatePathStatus(stateMachine, oldForwardPathId);
            updateSubPathStatuses(stateMachine, getForwardSubPathIds(stateMachine.getOldProtectedPathIds()));

            log.debug("Swapping back the protected forward path {} with {}",
                    haFlow.getProtectedForwardPathId(), oldForwardPathId != null ? oldForwardPathId : NO_PATH);

            haFlow.setProtectedForwardPathId(oldForwardPathId);
            saveHistory(stateMachine, oldForwardPathId);
        }

        if (getReversePathId(stateMachine.getNewProtectedPathIds()) != null) {
            PathId oldReversePathId = getReversePathId(stateMachine.getOldProtectedPathIds());
            updatePathStatus(stateMachine, oldReversePathId);
            updateSubPathStatuses(stateMachine, getReverseSubPathIds(stateMachine.getOldProtectedPathIds()));

            log.debug("Swapping back the protected reverse path {} with {}",
                    haFlow.getProtectedReversePathId(), oldReversePathId != null ? oldReversePathId : NO_PATH);
            haFlow.setProtectedReversePathId(oldReversePathId);
            saveHistory(stateMachine, oldReversePathId);
        }
    }

    private void updatePathStatus(HaFlowRerouteFsm stateMachine, PathId oldPathId) {
        if (oldPathId != null) {
            haFlowPathRepository.findById(oldPathId)
                    .ifPresent(path -> {
                        if (path.getStatus() != FlowPathStatus.ACTIVE) {
                            path.setStatus(stateMachine.getOldPathStatus(oldPathId));
                        }
                    });
        }
    }

    private void updateSubPathStatuses(HaFlowRerouteFsm stateMachine, Collection<PathId> oldSubPathIds) {
        for (PathId oldSubPathId : oldSubPathIds) {
            flowPathRepository.findById(oldSubPathId)
                    .ifPresent(path -> {
                        if (path.getStatus() != FlowPathStatus.ACTIVE) {
                            path.setStatus(stateMachine.getOldPathStatus(oldSubPathId));
                        }
                    });
        }
    }

    private void saveHistory(HaFlowRerouteFsm stateMachine, PathId pathId) {
        String pathName = pathId == null ? NO_PATH : format("the path %s", pathId);

        FlowHistoryService.using(stateMachine.getCarrier()).save(HaFlowHistory
                .of(stateMachine.getCommandContext().getCorrelationId())
                .withAction("Ha-flow has been reverted to old paths")
                .withDescription(format("The HA-flow %s has been updated with %s",
                        stateMachine.getHaFlowId(), pathName))
                .withHaFlowId(stateMachine.getHaFlowId()));
    }
}
