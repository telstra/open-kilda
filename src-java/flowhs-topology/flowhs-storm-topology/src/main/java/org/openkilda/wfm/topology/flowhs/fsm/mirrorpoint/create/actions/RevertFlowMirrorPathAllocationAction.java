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

import static com.google.common.collect.Sets.newHashSet;
import static java.lang.String.format;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowMirrorPoints;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowMirrorPathRepository;
import org.openkilda.persistence.repositories.FlowMirrorPointsRepository;
import org.openkilda.rulemanager.DataAdapter;
import org.openkilda.rulemanager.RuleManager;
import org.openkilda.rulemanager.adapter.PersistenceDataAdapter;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.BaseFlowPathRemovalAction;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.mirrorpoint.create.FlowMirrorPointCreateFsm.State;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.Set;

@Slf4j
public class RevertFlowMirrorPathAllocationAction
        extends BaseFlowPathRemovalAction<FlowMirrorPointCreateFsm, State, Event, FlowMirrorPointCreateContext> {
    private final FlowResourcesManager resourcesManager;
    private final FlowMirrorPathRepository flowMirrorPathRepository;
    private final FlowMirrorPointsRepository flowMirrorPointsRepository;
    private final RuleManager ruleManager;

    public RevertFlowMirrorPathAllocationAction(
            PersistenceManager persistenceManager, FlowResourcesManager resourcesManager, RuleManager ruleManager) {
        super(persistenceManager);
        this.resourcesManager = resourcesManager;
        this.flowMirrorPathRepository = persistenceManager.getRepositoryFactory().createFlowMirrorPathRepository();
        this.flowMirrorPointsRepository = persistenceManager.getRepositoryFactory().createFlowMirrorPointsRepository();
        this.ruleManager = ruleManager;
    }

    @Override
    protected void perform(State from, State to, Event event, FlowMirrorPointCreateContext context,
                           FlowMirrorPointCreateFsm stateMachine) {
        Optional<FlowMirrorPoints> mirrorPoints = flowMirrorPointsRepository.findByPathIdAndSwitchId(
                stateMachine.getFlowPathId(), stateMachine.getMirrorSwitchId());
        if (mirrorPoints.isPresent()) {
            // need to build rules before resources deallocation, because these resources will be used during building
            Flow flow = getFlow(stateMachine.getFlowId());
            PathId oppositePathId = flow.getOppositePathId(stateMachine.getFlowPathId()).orElse(null);
            Set<PathId> involvedPaths = newHashSet(stateMachine.getFlowPathId(), oppositePathId);
            DataAdapter dataAdapter = new PersistenceDataAdapter(persistenceManager, involvedPaths,
                    newHashSet(stateMachine.getMirrorSwitchId()), false);
            stateMachine.getRevertCommands().addAll(ruleManager.buildMirrorPointRules(mirrorPoints.get(), dataAdapter));
        } else {
            log.warn("Can't find mirror points for flow path {} and mirror switch {}. May cause excess rules.",
                    stateMachine.getFlowPathId(), stateMachine.getMirrorSwitchId());
        }

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
