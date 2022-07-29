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

package org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.actions;

import static java.lang.String.format;

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.FlowMirrorPath;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowMirrorPathRepository;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.exceptions.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.BaseFlowPathRemovalAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeallocateFlowMirrorPathResourcesAction
        extends BaseFlowPathRemovalAction<FlowMirrorPointDeleteFsm, State, Event, FlowMirrorPointDeleteContext> {
    private final FlowResourcesManager resourcesManager;
    private final FlowMirrorPathRepository flowMirrorPathRepository;

    public DeallocateFlowMirrorPathResourcesAction(PersistenceManager persistenceManager,
                                                   FlowResourcesManager resourcesManager) {
        super(persistenceManager);
        this.resourcesManager = resourcesManager;
        this.flowMirrorPathRepository = persistenceManager.getRepositoryFactory().createFlowMirrorPathRepository();
    }

    @Override
    protected void perform(State from, State to, Event event, FlowMirrorPointDeleteContext context,
                           FlowMirrorPointDeleteFsm stateMachine) {
        PathId mirrorPathId = stateMachine.getMirrorPathId();

        FlowMirrorPath flowMirrorPath = flowMirrorPathRepository.findById(mirrorPathId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Flow mirror point %s not found", mirrorPathId)));

        FlowMirrorPoints flowMirrorPoints = flowMirrorPath.getFlowMirrorPoints();
        stateMachine.setFlowPathId(flowMirrorPoints.getFlowPathId());
        stateMachine.setMirrorSwitchId(flowMirrorPoints.getMirrorSwitchId());

        resourcesManager.deallocateCookie(flowMirrorPath.getCookie().getFlowEffectiveId());
        flowMirrorPathRepository.remove(stateMachine.getMirrorPathId());

        stateMachine.saveActionToHistory("Flow mirror path resources were deallocated",
                format("The flow resources for mirror path %s were deallocated", stateMachine.getMirrorPathId()));
        stateMachine.setMirrorPathResourcesDeallocated(true);
    }
}
