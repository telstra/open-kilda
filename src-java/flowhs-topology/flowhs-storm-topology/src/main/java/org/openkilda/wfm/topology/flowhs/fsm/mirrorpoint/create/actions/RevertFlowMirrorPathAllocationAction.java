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

package org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.actions;

import static java.lang.String.format;

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowMirrorPathRepository;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.BaseFlowPathRemovalAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RevertFlowMirrorPathAllocationAction
        extends BaseFlowPathRemovalAction<FlowMirrorPointCreateFsm, State, Event, FlowMirrorPointCreateContext> {
    private final FlowResourcesManager resourcesManager;
    private final FlowMirrorPathRepository flowMirrorPathRepository;

    public RevertFlowMirrorPathAllocationAction(PersistenceManager persistenceManager,
                                                FlowResourcesManager resourcesManager) {
        super(persistenceManager);
        this.resourcesManager = resourcesManager;
        this.flowMirrorPathRepository = persistenceManager.getRepositoryFactory().createFlowMirrorPathRepository();
    }

    @Override
    protected void perform(State from, State to, Event event, FlowMirrorPointCreateContext context,
                           FlowMirrorPointCreateFsm stateMachine) {
        resourcesManager.deallocateCookie(stateMachine.getUnmaskedCookie());
        flowMirrorPathRepository.remove(stateMachine.getMirrorPathId());

        stateMachine.saveActionToHistory("Flow mirror path resources were deallocated",
                format("The flow resources for mirror path %s were deallocated", stateMachine.getMirrorPathId()));
        stateMachine.setMirrorPathId(null);

        if (!stateMachine.isRulesInstalled()) {
            log.debug("No need to re-install rules");
            stateMachine.fire(Event.SKIP_INSTALLING_RULES);
        }
    }
}
