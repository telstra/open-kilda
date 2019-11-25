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

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableMap;

import org.openkilda.floodlight.api.request.factory.FlowSegmentRequestFactory;
import org.openkilda.floodlight.api.response.SpeakerFlowSegmentResponse;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathId;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResources;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Slf4j
public abstract class FlowInstallingFsm<T extends NbTrackableFsm<T, S, E, C>, S, E, C>
        extends NbTrackableFsm<T, S, E, C> {

    private FlowEncapsulationType newEncapsulationType;
    private FlowStatus newFlowStatus;
    protected FlowResources newPrimaryResources;
    protected FlowResources newProtectedResources;
    protected PathId newPrimaryForwardPath;
    protected PathId newPrimaryReversePath;
    protected PathId newProtectedForwardPath;
    protected PathId newProtectedReversePath;

    @NonNull
    protected Set<UUID> pendingCommands = emptySet();
    protected final Map<UUID, Integer> retriedCommands = new HashMap<>();
    protected final Map<UUID, FlowErrorResponse> failedCommands = new HashMap<>();
    protected final Map<UUID, SpeakerFlowSegmentResponse> failedValidationResponses = new HashMap<>();

    @NonNull
    protected Map<UUID, FlowSegmentRequestFactory> ingressCommands = emptyMap();
    @NonNull
    protected Map<UUID, FlowSegmentRequestFactory> nonIngressCommands = emptyMap();
    @NonNull
    protected Map<UUID, FlowSegmentRequestFactory> removeCommands = emptyMap();

    public FlowInstallingFsm(CommandContext commandContext, String flowId) {
        super(commandContext, flowId);
    }

    public abstract void fireNoPathFound(String errorReason);

    public boolean hasNewPrimaryPaths() {
        return newPrimaryForwardPath != null && newPrimaryReversePath != null;
    }

    public boolean hasNewPrimaryResources() {
        return newPrimaryResources != null;
    }

    public void resetNewPrimaryPathsAndResources() {
        newPrimaryResources = null;
        newPrimaryForwardPath = null;
        newPrimaryReversePath = null;
    }

    public boolean hasNewProtectedPaths() {
        return newProtectedForwardPath != null && newProtectedReversePath != null;
    }

    public boolean hasNewProtectedResources() {
        return newProtectedResources != null;
    }

    public void resetNewProtectedPathsAndResources() {
        newProtectedResources = null;
        newProtectedForwardPath = null;
        newProtectedReversePath = null;
    }

    public void setIngressCommands(@NonNull Map<UUID, FlowSegmentRequestFactory> ingressCommands) {
        this.ingressCommands = unmodifiableMap(ingressCommands);
    }

    public void setNonIngressCommands(@NonNull Map<UUID, FlowSegmentRequestFactory> nonIngressCommands) {
        this.nonIngressCommands = unmodifiableMap(nonIngressCommands);
    }

    public void setRemoveCommands(@NonNull Map<UUID, FlowSegmentRequestFactory> removeCommands) {
        this.removeCommands = unmodifiableMap(removeCommands);
    }

    public FlowSegmentRequestFactory getInstallCommand(UUID commandId) {
        FlowSegmentRequestFactory requestFactory = nonIngressCommands.get(commandId);
        if (requestFactory == null) {
            requestFactory = ingressCommands.get(commandId);
        }
        return requestFactory;
    }

    public void setPendingCommands(@NonNull Set<UUID> pendingCommands) {
        this.pendingCommands = new HashSet<>(pendingCommands);
    }

    public boolean hasPendingCommands() {
        return !pendingCommands.isEmpty();
    }

    public boolean isPendingCommand(UUID commandId) {
        return pendingCommands.contains(commandId);
    }

    public void removePendingCommand(UUID commandId) {
        pendingCommands.remove(commandId);
    }

    public void resetPendingCommands() {
        pendingCommands.clear();
    }

    public boolean hasFailedCommands() {
        return !failedCommands.isEmpty();
    }

    public void addFailedCommand(UUID commandId, FlowErrorResponse errorResponse) {
        failedCommands.put(commandId, errorResponse);
    }

    public void setCommandRetries(UUID commandId, int retryAttempts) {
        retriedCommands.put(commandId, retryAttempts);
    }

    public int getCommandRetries(UUID commandId) {
        return retriedCommands.getOrDefault(commandId, 0);
    }

    public void resetFailedCommandsAndRetries() {
        retriedCommands.clear();
        failedCommands.clear();
    }

    public boolean hasFailedValidationResponses() {
        return !failedValidationResponses.isEmpty();
    }

    public void addFailedValidationResponse(UUID commandId, SpeakerFlowSegmentResponse validationResponse) {
        failedValidationResponses.put(commandId, validationResponse);
    }
}
