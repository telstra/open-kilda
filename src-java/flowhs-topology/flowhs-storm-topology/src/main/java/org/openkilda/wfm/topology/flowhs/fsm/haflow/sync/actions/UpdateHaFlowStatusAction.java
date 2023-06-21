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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.sync.actions;

import static java.lang.String.format;
import static org.openkilda.wfm.topology.flowhs.utils.HaFlowUtils.buildCrossingPaths;

import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.messaging.command.haflow.HaFlowSyncResponse;
import org.openkilda.messaging.command.haflow.HaFlowSyncResponse.HaFlowSyncResponseBuilder;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.logger.FlowOperationsDashboardLogger;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.haflow.HaFlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.sync.HaFlowSyncContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.sync.HaFlowSyncFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.sync.HaFlowSyncFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.sync.HaFlowSyncFsm.State;
import org.openkilda.wfm.topology.flowhs.model.CrossingPaths;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
public class UpdateHaFlowStatusAction extends
        HaFlowProcessingWithHistorySupportAction<HaFlowSyncFsm, State, Event, HaFlowSyncContext> {
    private final FlowOperationsDashboardLogger dashboardLogger;

    public UpdateHaFlowStatusAction(
            PersistenceManager persistenceManager, FlowOperationsDashboardLogger dashboardLogger) {
        super(persistenceManager);
        this.dashboardLogger = dashboardLogger;
    }

    @Override
    protected void perform(
            State from, State to, Event event, HaFlowSyncContext context, HaFlowSyncFsm stateMachine) {

        Set<PathId> unsyncedPathIds = collectPathIdOfUnsuccessfulCommands(stateMachine);
        HaFlow haflow = transactionManager.doInTransaction(() -> {
            HaFlow haFlow = getHaFlow(stateMachine.getHaFlowId());

            for (HaFlowPath haFlowPath : haFlow.getUsedPaths()) {
                List<FlowPathStatus> subPathStatuses = new ArrayList<>();
                for (FlowPath subPath : haFlowPath.getSubPaths()) {
                    FlowPathStatus subPathStatus = computeSubPathStatus(unsyncedPathIds, subPath.getPathId());
                    subPath.setStatus(subPathStatus);
                    subPathStatuses.add(subPathStatus);

                }
                haFlowPath.setStatus(computeHaFlowStatus(unsyncedPathIds, haFlowPath.getHaPathId(), subPathStatuses));
            }

            haFlow.recalculateHaSubFlowStatuses();
            haFlow.setStatus(haFlow.computeStatus());
            return haFlow;
        });

        dashboardLogger.onHaFlowStatusUpdate(stateMachine.getHaFlowId(), haflow.getStatus());
        stateMachine.saveActionToHistory(format("The ha-flow status was set to %s", haflow.getStatus()));
        sendResponse(haflow, stateMachine);
    }

    private static FlowPathStatus computeHaFlowStatus(
            Set<PathId> unsyncedPathIds, PathId pathId, List<FlowPathStatus> subPathStatuses) {
        if (unsyncedPathIds.contains(pathId) || subPathStatuses.size() < 2) {
            return FlowPathStatus.INACTIVE;
        } else {
            return subPathStatuses.stream()
                    .max(FlowPathStatus::compareTo)
                    .orElse(FlowPathStatus.INACTIVE);
        }
    }

    private Set<PathId> collectPathIdOfUnsuccessfulCommands(HaFlowSyncFsm stateMachine) {
        Set<PathId> pathIds = new HashSet<>();
        for (UUID failedCommendUuid : stateMachine.getFailedCommands().keySet()) {
            pathIds.addAll(stateMachine.getRequestToPathIdsMap().get(failedCommendUuid));
        }
        for (UUID nonCompletedCommandUuid : stateMachine.getPendingCommands().keySet()) {
            pathIds.addAll(stateMachine.getRequestToPathIdsMap().get(nonCompletedCommandUuid));
        }
        return pathIds;
    }

    private FlowPathStatus computeSubPathStatus(Set<PathId> unsyncedPathIds, PathId pathId) {
        if (unsyncedPathIds.contains(pathId)) {
            return FlowPathStatus.INACTIVE;
        } else {
            // TODO add checking of latency SLA when https://github.com/telstra/open-kilda/issues/5223 will be completed
            return FlowPathStatus.ACTIVE;
        }
    }

    private void sendResponse(HaFlow haFlow, HaFlowSyncFsm stateMachine) {
        HaFlowSyncResponse payload = buildSyncResponse(haFlow, stateMachine);
        stateMachine.getCarrier().sendNorthboundResponse(
                new InfoMessage(payload, System.currentTimeMillis(),
                        stateMachine.getCommandContext().getCorrelationId()));
    }

    private static HaFlowSyncResponse buildSyncResponse(HaFlow haFlow, HaFlowSyncFsm stateMachine) {
        HaFlowSyncResponseBuilder builder = HaFlowSyncResponse.builder();
        if (haFlow.getPrimaryPaths() != null) {
            CrossingPaths primaryPaths = buildCrossingPaths(haFlow.getForwardPath(), CrossingPaths.buildEmpty());
            builder.sharedPath(primaryPaths.getSharedPath());
            builder.subFlowPaths(primaryPaths.getSubFlowPaths());
        }
        if (haFlow.getProtectedForwardPath() != null) {
            CrossingPaths protectedPaths = buildCrossingPaths(
                    haFlow.getProtectedForwardPath(), CrossingPaths.buildEmpty());
            builder.protectedSharedPath(protectedPaths.getSharedPath());
            builder.protectedSubFlowPaths(protectedPaths.getSubFlowPaths());
        }
        boolean synced = stateMachine.getPendingCommands().isEmpty() && stateMachine.getFailedCommands().isEmpty();
        builder.synced(synced);
        if (!synced) {
            builder.error(stateMachine.getErrorReason());
            Set<SwitchId> unsyncedSwitches = new HashSet<>(stateMachine.getPendingCommands().values());
            for (SpeakerCommandResponse failedCommand : stateMachine.getFailedCommands().values()) {
                unsyncedSwitches.add(failedCommand.getSwitchId());
            }
            builder.unsyncedSwitches(unsyncedSwitches);
        }
        return builder.build();
    }
}
