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

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathId;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.share.flow.resources.FlowResources;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;

@Getter
@Setter
@Slf4j
public abstract class FlowPathSwappingFsm<T extends FlowInstallingFsm<T, S, E, C>, S, E, C>
        extends FlowInstallingFsm<T, S, E, C> {

    private FlowStatus originalFlowStatus;
    protected final Collection<FlowResources> oldResources = new ArrayList<>();
    protected PathId oldPrimaryForwardPath;
    protected FlowPathStatus oldPrimaryForwardPathStatus;
    protected PathId oldPrimaryReversePath;
    protected FlowPathStatus oldPrimaryReversePathStatus;
    protected PathId oldProtectedForwardPath;
    protected FlowPathStatus oldProtectedForwardPathStatus;
    protected PathId oldProtectedReversePath;
    protected FlowPathStatus oldProtectedReversePathStatus;

    public FlowPathSwappingFsm(CommandContext commandContext, String flowId) {
        super(commandContext, flowId);
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

    public abstract FlowEncapsulationType getOriginalEncapsulationType();
}
