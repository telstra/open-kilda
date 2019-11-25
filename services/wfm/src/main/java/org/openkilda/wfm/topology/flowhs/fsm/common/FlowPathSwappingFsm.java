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

import org.openkilda.floodlight.flow.request.InstallFlowRule;
import org.openkilda.floodlight.flow.request.InstallIngressRule;
import org.openkilda.floodlight.flow.request.InstallTransitRule;
import org.openkilda.floodlight.flow.request.RemoveRule;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.floodlight.flow.response.FlowResponse;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.PathId;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResources;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

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
public abstract class FlowPathSwappingFsm<T extends NbTrackableFsm<T, S, E, C>, S, E, C>
        extends NbTrackableFsm<T, S, E, C> {

    protected final String flowId;

    protected FlowResources newPrimaryResources;
    protected FlowResources newProtectedResources;
    protected PathId newPrimaryForwardPath;
    protected PathId newPrimaryReversePath;
    protected PathId newProtectedForwardPath;
    protected PathId newProtectedReversePath;

    protected final Collection<FlowResources> oldResources = new ArrayList<>();
    protected PathId oldPrimaryForwardPath;
    protected FlowPathStatus oldPrimaryForwardPathStatus;
    protected PathId oldPrimaryReversePath;
    protected FlowPathStatus oldPrimaryReversePathStatus;
    protected PathId oldProtectedForwardPath;
    protected FlowPathStatus oldProtectedForwardPathStatus;
    protected PathId oldProtectedReversePath;
    protected FlowPathStatus oldProtectedReversePathStatus;

    protected final Set<UUID> pendingCommands = new HashSet<>();
    protected final Map<UUID, Integer> retriedCommands = new HashMap<>();
    protected final Map<UUID, FlowErrorResponse> failedCommands = new HashMap<>();
    protected final Map<UUID, FlowResponse> failedValidationResponses = new HashMap<>();

    protected final Map<UUID, InstallIngressRule> ingressCommands = new HashMap<>();
    protected final Map<UUID, InstallTransitRule> nonIngressCommands = new HashMap<>();
    protected final Map<UUID, RemoveRule> removeCommands = new HashMap<>();

    protected String errorReason;

    public FlowPathSwappingFsm(CommandContext commandContext, String flowId) {
        super(commandContext);
        this.flowId = flowId;
    }

    public InstallFlowRule getInstallCommand(UUID commandId) {
        if (nonIngressCommands.containsKey(commandId)) {
            return nonIngressCommands.get(commandId);
        } else if (ingressCommands.containsKey(commandId)) {
            return ingressCommands.get(commandId);
        }
        return null;
    }

    public abstract void fireNoPathFound(String errorReason);

    public boolean hasNewPrimaryResources() {
        return newPrimaryResources != null;
    }

    public boolean hasNewPrimaryPaths() {
        return newPrimaryForwardPath != null && newPrimaryReversePath != null;
    }

    public void resetNewPrimaryPathsAndResources() {
        newPrimaryResources = null;
        newPrimaryForwardPath = null;
        newPrimaryReversePath = null;
    }

    public boolean hasNewProtectedResources() {
        return newProtectedResources != null;
    }

    public boolean hasNewProtectedPaths() {
        return newProtectedForwardPath != null && newProtectedReversePath != null;
    }

    public void resetNewProtectedPathsAndResources() {
        newProtectedResources = null;
        newProtectedForwardPath = null;
        newProtectedReversePath = null;
    }

    public boolean hasOldPrimaryForwardPath() {
        return oldPrimaryForwardPath != null;
    }

    public boolean hasOldPrimaryReversePath() {
        return oldPrimaryReversePath != null;
    }

    public boolean hasOldProtectedForwardPath() {
        return oldProtectedForwardPath != null;
    }

    public boolean hasOldProtectedReversePath() {
        return oldProtectedReversePath != null;
    }

    public void resetOldPrimaryPaths() {
        oldPrimaryForwardPath = null;
        oldPrimaryReversePath = null;
    }

    public void resetOldProtectedPaths() {
        oldProtectedForwardPath = null;
        oldProtectedReversePath = null;
    }

    public void addOldResources(FlowResources resources) {
        oldResources.add(resources);
    }

    public void resetOldResources() {
        oldResources.clear();
    }

    public boolean hasPendingCommands() {
        return !pendingCommands.isEmpty();
    }

    public void addToPendingCommands(Collection<UUID> commandIds) {
        pendingCommands.addAll(commandIds);
    }

    public void removePendingCommand(UUID commandId) {
        pendingCommands.remove(commandId);
    }

    public void resetPendingCommands() {
        pendingCommands.clear();
    }

    public void addToIngressCommands(Map<UUID, InstallIngressRule> commands) {
        ingressCommands.putAll(commands);
    }

    public void addToNonIngressCommands(Map<UUID, InstallTransitRule> commands) {
        nonIngressCommands.putAll(commands);
    }

    public void addToRemoveCommands(Map<UUID, RemoveRule> commands) {
        removeCommands.putAll(commands);
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

    public void resetAllCommandRetries() {
        retriedCommands.clear();
    }
}
