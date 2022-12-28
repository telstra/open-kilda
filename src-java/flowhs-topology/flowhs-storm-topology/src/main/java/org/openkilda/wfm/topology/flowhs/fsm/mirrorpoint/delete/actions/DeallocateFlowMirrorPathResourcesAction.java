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

import static com.google.common.collect.Sets.newHashSet;
import static java.lang.String.format;

import org.openkilda.messaging.error.ErrorType;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowMirror;
import org.openkilda.model.FlowMirrorPath;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowMirrorPathRepository;
import org.openkilda.persistence.repositories.FlowMirrorRepository;
import org.openkilda.rulemanager.DataAdapter;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.adapter.PersistenceDataAdapter;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.BaseFlowPathRemovalAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.delete.FlowMirrorPointDeleteFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.Set;

@Slf4j
public class DeallocateFlowMirrorPathResourcesAction
        extends BaseFlowPathRemovalAction<FlowMirrorPointDeleteFsm, State, Event, FlowMirrorPointDeleteContext> {
    private final FlowResourcesManager resourcesManager;
    private final FlowMirrorPathRepository flowMirrorPathRepository;
    private final FlowMirrorRepository flowMirrorRepository;
    private final RuleManager ruleManager;

    public DeallocateFlowMirrorPathResourcesAction(
            PersistenceManager persistenceManager, FlowResourcesManager resourcesManager, RuleManager ruleManager) {
        super(persistenceManager);
        this.resourcesManager = resourcesManager;
        this.flowMirrorPathRepository = persistenceManager.getRepositoryFactory().createFlowMirrorPathRepository();
        this.flowMirrorRepository = persistenceManager.getRepositoryFactory().createFlowMirrorRepository();
        this.ruleManager = ruleManager;
    }

    @Override
    protected void perform(State from, State to, Event event, FlowMirrorPointDeleteContext context,
                           FlowMirrorPointDeleteFsm stateMachine) {
        String flowMirrorId = stateMachine.getFlowMirrorId();

        FlowMirror flowMirror = flowMirrorRepository.findById(flowMirrorId)
                .orElseThrow(() -> new FlowProcessingException(ErrorType.NOT_FOUND,
                        format("Flow mirror point %s not found", flowMirrorId)));

        FlowMirrorPoints flowMirrorPoints = flowMirror.getFlowMirrorPoints();
        stateMachine.setFlowPathId(flowMirrorPoints.getFlowPathId());
        stateMachine.setMirrorSwitchId(flowMirrorPoints.getMirrorSwitchId());

        // need to build rules before resources deallocation, because these resources will be used during building
        Flow flow = getFlow(stateMachine.getFlowId());
        PathId oppositePathId = flow.getOppositePathId(stateMachine.getFlowPathId()).orElse(null);
        Set<PathId> involvedPaths = newHashSet(stateMachine.getFlowPathId(), oppositePathId);
        DataAdapter dataAdapter = new PersistenceDataAdapter(persistenceManager, involvedPaths, newHashSet(),
                newHashSet(stateMachine.getMirrorSwitchId()), false);
        stateMachine.getMirrorPointSpeakerData().addAll(ruleManager.buildMirrorPointRules(
                flowMirrorPoints, dataAdapter));

        Optional<FlowMirrorPath> flowMirrorPath = flowMirror.getPath(flowMirror.getForwardPathId());
        if (flowMirrorPath.isPresent()) {
            resourcesManager.deallocateCookie(flowMirrorPath.get().getCookie().getFlowEffectiveId());
            flowMirrorPathRepository.remove(flowMirrorPath.get());
        } else {
            log.warn("Flow forward mirror path {} not found.", flowMirror.getForwardPathId());
        }
        flowMirrorRepository.remove(stateMachine.getFlowMirrorId());

        stateMachine.saveActionToHistory("Flow mirror path resources were deallocated",
                format("The flow resources for mirror path %s were deallocated", stateMachine.getFlowMirrorId()));
        stateMachine.setMirrorPathResourcesDeallocated(true);
    }
}
