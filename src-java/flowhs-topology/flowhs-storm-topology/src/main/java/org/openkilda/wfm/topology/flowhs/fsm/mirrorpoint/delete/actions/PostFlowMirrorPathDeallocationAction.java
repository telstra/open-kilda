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

import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowMirrorPointsRepository;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm.State;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PostFlowMirrorPathDeallocationAction
        extends FlowProcessingAction<FlowMirrorPointDeleteFsm, State, Event, FlowMirrorPointDeleteContext> {
    private final FlowResourcesManager resourcesManager;
    private final FlowMirrorPointsRepository flowMirrorPointsRepository;

    public PostFlowMirrorPathDeallocationAction(PersistenceManager persistenceManager,
                                                FlowResourcesManager resourcesManager) {
        super(persistenceManager);
        this.resourcesManager = resourcesManager;
        this.flowMirrorPointsRepository = persistenceManager.getRepositoryFactory().createFlowMirrorPointsRepository();
    }

    @Override
    protected void perform(State from, State to, Event event, FlowMirrorPointDeleteContext context,
                           FlowMirrorPointDeleteFsm stateMachine) {
        PathId flowPathId = stateMachine.getFlowPathId();
        SwitchId mirrorSwitchId = stateMachine.getMirrorSwitchId();

        boolean mirrorPointsWereDeallocated = transactionManager.doInTransaction(() -> {

            FlowMirrorPoints flowMirrorPoints = flowMirrorPointsRepository
                    .findByPathIdAndSwitchId(flowPathId, mirrorSwitchId)
                    .orElse(null);
            if (flowMirrorPoints != null && flowMirrorPoints.getMirrorPaths().isEmpty()) {
                flowMirrorPointsRepository.remove(flowMirrorPoints);
                resourcesManager.deallocateMirrorGroup(flowPathId, mirrorSwitchId);
                return true;
            }
            return false;
        });

        if (mirrorPointsWereDeallocated) {
            stateMachine.saveActionToHistory("Flow mirror group was deallocated",
                    format("The flow mirror group for flow path %s and switch id %s was deallocated",
                            flowPathId, mirrorSwitchId));
        }
    }
}
