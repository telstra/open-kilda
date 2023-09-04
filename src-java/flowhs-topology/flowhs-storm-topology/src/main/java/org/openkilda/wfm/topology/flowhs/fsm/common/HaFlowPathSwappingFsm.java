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

package org.openkilda.wfm.topology.flowhs.fsm.common;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.openkilda.wfm.topology.flowhs.utils.HaFlowUtils.buildPathIds;

import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.PathId;
import org.openkilda.pce.GetHaPathsResult;
import org.openkilda.pce.HaPath;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.HaFlowResources;
import org.openkilda.wfm.share.flow.resources.HaPathIdsPair;
import org.openkilda.wfm.share.flow.resources.HaPathIdsPair.HaFlowPathIds;
import org.openkilda.wfm.topology.flowhs.fsm.common.context.SpeakerResponseContext;
import org.openkilda.wfm.topology.flowhs.service.FlowProcessingEventListener;
import org.openkilda.wfm.topology.flowhs.service.common.HistoryUpdateCarrier;
import org.openkilda.wfm.topology.flowhs.service.common.NorthboundResponseCarrier;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Slf4j
public abstract class HaFlowPathSwappingFsm<T extends AbstractStateMachine<T, S, E, C>, S, E,
        C extends SpeakerResponseContext, R extends NorthboundResponseCarrier & HistoryUpdateCarrier,
        L extends FlowProcessingEventListener> extends HaFlowProcessingFsm<T, S, E, C, R, L> {
    protected final String haFlowId;

    protected HaFlowResources newPrimaryResources;
    protected HaFlowResources newProtectedResources;
    protected HaPathIdsPair newPrimaryPathIds;
    protected HaPathIdsPair newProtectedPathIds;
    protected boolean backUpProtectedPathComputationWayUsed;

    protected HaFlow originalHaFlow;
    protected final Collection<HaFlowResources> oldResources = new ArrayList<>();
    protected HaPathIdsPair oldPrimaryPathIds;
    protected HaPathIdsPair oldProtectedPathIds;
    protected final Map<PathId, FlowPathStatus> oldPathStatuses = new HashMap<>();

    protected final Set<PathId> rejectedSubPathsIds = new HashSet<>();
    protected final Set<PathId> rejectedHaPathsIds = new HashSet<>();
    protected final Collection<HaFlowResources> rejectedResources = new ArrayList<>();

    protected final Map<UUID, BaseSpeakerCommandsRequest> ingressCommands = new HashMap<>();
    protected final Map<UUID, BaseSpeakerCommandsRequest> nonIngressCommands = new HashMap<>();
    protected final Map<UUID, BaseSpeakerCommandsRequest> removeCommands = new HashMap<>();

    private final Map<PathId, Boolean> backUpComputationWayUsedMap;
    protected boolean periodicPingsEnabled;

    protected HaFlowPathSwappingFsm(
            @NonNull E nextEvent, @NonNull E errorEvent, @NonNull CommandContext commandContext, @NonNull R carrier,
            @NonNull String haFlowId, @NonNull Collection<L> eventListeners) {
        super(nextEvent, errorEvent, commandContext, carrier, haFlowId, eventListeners);
        this.haFlowId = haFlowId;
        this.backUpComputationWayUsedMap = new HashMap<>();
    }

    public BaseSpeakerCommandsRequest getInstallCommand(UUID commandId) {
        BaseSpeakerCommandsRequest command = nonIngressCommands.get(commandId);
        if (command == null) {
            command = ingressCommands.get(commandId);
        }
        return command;
    }

    public abstract void fireNoPathFound(String errorReason);

    public void clearPendingAndRetriedAndFailedCommands() {
        clearPendingCommands();
        clearRetriedCommands();
        clearFailedCommands();
    }

    public void notifyEventListenersOnError(ErrorType errorType, String errorMessage) {
        notifyEventListeners(listener ->
                listener.onFailed(getFlowId(), errorMessage, errorType));
    }

    public void setOldPathStatuses(HaFlowPath haPath) {
        oldPathStatuses.put(haPath.getHaPathId(), haPath.getStatus());
        if (haPath.getSubPaths() != null) {
            for (FlowPath subPath : haPath.getSubPaths()) {
                oldPathStatuses.put(subPath.getPathId(), subPath.getStatus());
            }
        }
    }

    public FlowPathStatus getOldPathStatus(PathId pathId) {
        return requireNonNull(oldPathStatuses.get(pathId));
    }

    public FlowPathStatus getNewPathStatus(PathId pathId) {
        return getNewPathStatus(pathId, false);
    }

    public FlowPathStatus getNewPathStatus(PathId pathId, boolean isIgnoreBandwidth) {
        Boolean isBackUpPathComputationStrategyUsed = backUpComputationWayUsedMap.get(pathId);
        if (isBackUpPathComputationStrategyUsed == null) {
            throw new IllegalArgumentException(format("Unknown path id %s", pathId));
        }
        return isIgnoreBandwidth || isBackUpPathComputationStrategyUsed
                ? FlowPathStatus.DEGRADED : FlowPathStatus.ACTIVE;
    }

    public void savePathsAndSetInProgressStatuses(HaFlowPath haFlowPath) {
        setOldPathStatuses(haFlowPath);

        haFlowPath.setStatus(FlowPathStatus.IN_PROGRESS);
        if (haFlowPath.getSubPaths() != null) {
            for (FlowPath subPath : haFlowPath.getSubPaths()) {
                subPath.setStatus(FlowPathStatus.IN_PROGRESS);
            }
        }
    }

    public void saveOldPathIds(HaFlow haFlow) {
        setOldPrimaryPathIds(buildPathIds(haFlow.getForwardPath(), haFlow.getReversePath()));
        setOldProtectedPathIds(buildPathIds(haFlow.getProtectedForwardPath(), haFlow.getProtectedReversePath()));
    }

    public void addBackUpComputationStatuses(GetHaPathsResult paths, HaPathIdsPair pathIds) {
        addBackUpComputationStatuses(paths.getForward(), pathIds.getForward());
        addBackUpComputationStatuses(paths.getReverse(), pathIds.getReverse());

    }

    private void addBackUpComputationStatuses(HaPath path, HaFlowPathIds pathIds) {
        backUpComputationWayUsedMap.put(pathIds.getHaPathId(), path.isBackupPath());
        for (String subFlowId : pathIds.getSubPathIds().keySet()) {
            backUpComputationWayUsedMap.put(pathIds.getSubPathId(subFlowId),
                    path.getSubPaths().get(subFlowId).isBackupPath());
        }
    }
}
