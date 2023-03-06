/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.delete.actions;

import static java.lang.String.format;

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.share.flow.resources.FlowResources;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.delete.FlowDeleteFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;

@Slf4j
public class DeallocateResourcesAction extends
        FlowProcessingWithHistorySupportAction<FlowDeleteFsm, State, Event, FlowDeleteContext> {
    private final FlowResourcesManager resourcesManager;

    public DeallocateResourcesAction(PersistenceManager persistenceManager,
                                     FlowResourcesManager resourcesManager) {
        super(persistenceManager);
        this.resourcesManager = resourcesManager;
    }

    @Override
    public void perform(State from, State to, Event event, FlowDeleteContext context, FlowDeleteFsm stateMachine) {
        stateMachine.getFlowMirrorPathResources().forEach(mirrorPathResources -> {
            mirrorPathResources.getUnmaskedCookies().forEach(resourcesManager::deallocateCookie);
            resourcesManager.deallocateMirrorGroup(mirrorPathResources.getFlowPathId(),
                    mirrorPathResources.getMirrorSwitchId());
        });

        Collection<FlowResources> flowResources = stateMachine.getFlowResources();
        flowResources.forEach(resources -> {
            transactionManager.doInTransaction(() ->
                    resourcesManager.deallocatePathResources(resources));
            stateMachine.saveActionToHistory("Flow resources were deallocated",
                    format("The flow resources for %s / %s were deallocated",
                            resources.getForward().getPathId(), resources.getReverse().getPathId()));
        });
    }
}
