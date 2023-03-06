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

package org.openkilda.wfm.topology.flowhs.fsm.common;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Getter
@Setter
@Slf4j
public abstract class FlowPathSwappingFsm<T extends AbstractStateMachine<T, S, E, C>, S, E, C,
        R extends NorthboundResponseCarrier & HistoryUpdateCarrier, L extends FlowProcessingEventListener>
        extends FlowProcessingWithHistorySupportFsm<T, S, E, C, R, L> {
    protected final String flowId;
    protected String sharedBandwidthGroupId;

    protected FlowResources newPrimaryResources;
    protected FlowResources newProtectedResources;
    protected PathId newPrimaryForwardPath;
    protected PathId newPrimaryReversePath;
    protected PathId newProtectedForwardPath;
    protected PathId newProtectedReversePath;
    protected boolean backUpPrimaryPathComputationWayUsed;
    protected boolean backUpProtectedPathComputationWayUsed;

    protected RequestedFlow originalFlow;
    protected final Collection<FlowResources> oldResources = new ArrayList<>();
    protected PathId oldPrimaryForwardPath;
    protected PathId oldPrimaryReversePath;
    protected PathId oldProtectedForwardPath;
    protected PathId oldProtectedReversePath;
    protected final Map<PathId, FlowPathStatus> oldPathStatuses = new HashMap<>();

    protected final Collection<PathId> rejectedPaths = new ArrayList<>();
    protected final Collection<FlowResources> rejectedResources = new ArrayList<>();

    protected final Map<UUID, SwitchId> pendingCommands = new HashMap<>();
    protected final Map<UUID, Integer> retriedCommands = new HashMap<>();
    protected final Map<UUID, SpeakerResponse> failedCommands = new HashMap<>();
    protected final Map<UUID, SpeakerResponse> failedValidationResponses = new HashMap<>();

    protected final Map<UUID, FlowSegmentRequestFactory> ingressCommands = new HashMap<>();
    protected final Map<UUID, FlowSegmentRequestFactory> nonIngressCommands = new HashMap<>();
    protected final Map<UUID, FlowSegmentRequestFactory> removeCommands = new HashMap<>();

    protected boolean periodicPingsEnabled;

    protected FlowPathSwappingFsm(@NonNull E nextEvent, @NonNull E errorEvent,
                                  @NonNull CommandContext commandContext, @NonNull R carrier, @NonNull String flowId) {
        this(nextEvent, errorEvent, commandContext, carrier, flowId, emptyList());
    }

    protected FlowPathSwappingFsm(@NonNull E nextEvent, @NonNull E errorEvent,
                                  @NonNull CommandContext commandContext, @NonNull R carrier, @NonNull String flowId,
                                  @NonNull Collection<L> eventListeners) {
        super(nextEvent, errorEvent, commandContext, carrier, eventListeners);
        this.flowId = flowId;
    }

    public FlowSegmentRequestFactory getInstallCommand(UUID commandId) {
        FlowSegmentRequestFactory requestFactory = nonIngressCommands.get(commandId);
        if (requestFactory == null) {
            requestFactory = ingressCommands.get(commandId);
        }
        return requestFactory;
    }

    public FlowSegmentRequestFactory getRemoveCommand(UUID commandId) {
        return removeCommands.get(commandId);
    }

    public abstract void fireNoPathFound(String errorReason);

    public void clearPendingCommands() {
        pendingCommands.clear();
    }

    public Optional<SwitchId> getPendingCommand(UUID key) {
        return Optional.ofNullable(pendingCommands.get(key));
    }

    public boolean hasPendingCommand(UUID key) {
        return pendingCommands.containsKey(key);
    }

    public void addPendingCommand(UUID key, SwitchId switchId) {
        pendingCommands.put(key, switchId);
    }

    public Optional<SwitchId> removePendingCommand(UUID key) {
        return Optional.ofNullable(pendingCommands.remove(key));
    }

    public void clearRetriedCommands() {
        retriedCommands.clear();
    }

    public int doRetryForCommand(UUID key) {
        int attempt = retriedCommands.getOrDefault(key, 0) + 1;
        retriedCommands.put(key, attempt);
        return attempt;
    }

    public void clearFailedCommands() {
        failedCommands.clear();
    }

    public void addFailedCommand(UUID key, SpeakerResponse errorResponse) {
        failedCommands.put(key, errorResponse);
    }

    public void clearPendingAndRetriedAndFailedCommands() {
        clearPendingCommands();
        clearRetriedCommands();
        clearFailedCommands();
    }

    public void notifyEventListenersOnError(ErrorType errorType, String errorMessage) {
        notifyEventListeners(listener ->
                listener.onFailed(getFlowId(), errorMessage, errorType));
    }

    public void setOldPathStatuses(Collection<FlowPath> paths) {
        paths.forEach(path -> oldPathStatuses.put(path.getPathId(), path.getStatus()));
    }

    public void setOldPathStatuses(FlowPath path) {
        oldPathStatuses.put(path.getPathId(), path.getStatus());
    }

    public FlowPathStatus getOldPathStatus(PathId pathId) {
        return requireNonNull(oldPathStatuses.get(pathId));
    }

    public FlowPathStatus getOldPrimaryForwardPathStatus() {
        return getOldPathStatus(requireNonNull(oldPrimaryForwardPath));
    }

    public FlowPathStatus getOldPrimaryReversePathStatus() {
        return getOldPathStatus(requireNonNull(oldPrimaryReversePath));
    }

    public FlowPathStatus getOldProtectedForwardPathStatus() {
        return getOldPathStatus(requireNonNull(oldProtectedForwardPath));
    }

    public FlowPathStatus getOldProtectedReversePathStatus() {
        return getOldPathStatus(requireNonNull(oldProtectedReversePath));
    }
}
