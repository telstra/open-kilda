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

import static java.lang.String.format;

import org.openkilda.floodlight.flow.request.InstallFlowRule;
import org.openkilda.floodlight.flow.request.InstallIngressRule;
import org.openkilda.floodlight.flow.request.InstallTransitRule;
import org.openkilda.floodlight.flow.request.RemoveRule;
import org.openkilda.floodlight.flow.response.FlowErrorResponse;
import org.openkilda.floodlight.flow.response.FlowResponse;
import org.openkilda.model.Cookie;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.PathId;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResources;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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

    protected Collection<FlowResources> oldResources;
    protected PathId oldPrimaryForwardPath;
    protected FlowPathStatus oldPrimaryForwardPathStatus;
    protected PathId oldPrimaryReversePath;
    protected FlowPathStatus oldPrimaryReversePathStatus;
    protected PathId oldProtectedForwardPath;
    protected FlowPathStatus oldProtectedForwardPathStatus;
    protected PathId oldProtectedReversePath;
    protected FlowPathStatus oldProtectedReversePathStatus;

    protected Set<UUID> pendingCommands = Collections.emptySet();
    protected Map<UUID, Integer> retriedCommands = new HashMap<>();
    protected Map<UUID, FlowErrorResponse> failedCommands = new HashMap<>();
    protected Map<UUID, FlowResponse> failedValidationResponses = new HashMap<>();

    protected Map<UUID, InstallIngressRule> ingressCommands = new HashMap<>();
    protected Map<UUID, InstallTransitRule> nonIngressCommands = new HashMap<>();
    protected Map<UUID, RemoveRule> removeCommands = new HashMap<>();

    protected String errorReason;

    public FlowPathSwappingFsm(CommandContext commandContext, String flowId) {
        super(commandContext);
        this.flowId = flowId;
    }

    public void addOldResources(FlowResources resources) {
        if (oldResources == null) {
            oldResources = new ArrayList<>();
        }
        oldResources.add(resources);
    }

    public InstallFlowRule getInstallCommand(UUID commandId) {
        if (nonIngressCommands.containsKey(commandId)) {
            return nonIngressCommands.get(commandId);
        } else if (ingressCommands.containsKey(commandId)) {
            return ingressCommands.get(commandId);
        }
        return null;
    }

    public Cookie getCookieForCommand(UUID commandId) {
        Cookie cookie;
        if (nonIngressCommands.containsKey(commandId)) {
            InstallTransitRule installRule = nonIngressCommands.get(commandId);
            cookie = installRule.getCookie();
        } else if (ingressCommands.containsKey(commandId)) {
            InstallIngressRule installRule = ingressCommands.get(commandId);
            cookie = installRule.getCookie();
        } else if (removeCommands.containsKey(commandId)) {
            RemoveRule removeRule = removeCommands.get(commandId);
            cookie = removeRule.getCookie();
        } else {
            throw new IllegalStateException(format("Failed to find install/remove rule command with id %s", commandId));
        }
        return cookie;
    }
}
