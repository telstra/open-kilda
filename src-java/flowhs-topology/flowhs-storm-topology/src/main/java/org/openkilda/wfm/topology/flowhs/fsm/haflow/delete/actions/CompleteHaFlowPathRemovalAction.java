/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.actions;

import org.openkilda.model.FlowPath;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlowPath;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.HaFlowPathRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HaFlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.HaFlowDeleteContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.HaFlowDeleteFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.HaFlowDeleteFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.delete.HaFlowDeleteFsm.State;
import org.openkilda.wfm.topology.flowhs.model.Segment;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class CompleteHaFlowPathRemovalAction extends
        HaFlowProcessingWithHistorySupportAction<HaFlowDeleteFsm, State, Event, HaFlowDeleteContext> {
    private final IslRepository islRepository;
    private final FlowPathRepository flowPathRepository;
    private final HaFlowPathRepository haFlowPathRepository;

    public CompleteHaFlowPathRemovalAction(PersistenceManager persistenceManager) {
        super(persistenceManager);
        this.islRepository = persistenceManager.getRepositoryFactory().createIslRepository();
        this.flowPathRepository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
        this.haFlowPathRepository = persistenceManager.getRepositoryFactory().createHaFlowPathRepository();
    }

    @Override
    protected void perform(
            State from, State to, Event event, HaFlowDeleteContext context, HaFlowDeleteFsm stateMachine) {
        List<PathSegment> removedSegments = new ArrayList<>();

        Set<PathId> pathIds = transactionManager.doInTransaction(() -> {
            HaFlow haFlow = getHaFlow(stateMachine.getHaFlowId());
            for (HaFlowPath haFlowPath : haFlow.getPaths()) {
                for (FlowPath subPath : haFlowPath.getSubPaths()) {
                    // Flow path cascade remove will remove segments too
                    removedSegments.addAll(subPath.getSegments());
                    flowPathRepository.remove(subPath);
                }
            }
            return haFlow.getPathIds();
        });

        for (PathId haFlowPathId : pathIds) {
            haFlowPathRepository.remove(haFlowPathId);
        }
        updateIslsForSegments(removedSegments);
        //TODO save info about removed paths into history
    }

    private void updateIslsForSegments(List<PathSegment> pathSegments) {
        Set<Segment> uniqueSegments = pathSegments.stream().map(Segment::new).collect(Collectors.toSet());
        uniqueSegments.forEach(segment ->
                transactionManager.doInTransaction(() ->
                        islRepository.updateAvailableBandwidth(
                                segment.getSrcSwitchId(), segment.getSrcPort(),
                                segment.getDstSwitchId(), segment.getDstPort())));
    }
}
