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

package org.openkilda.wfm.topology.flowhs.fsm.haflow.create.actions;

import static java.lang.String.format;

import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.HaFlowPathRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.share.flow.resources.HaFlowResources;
import org.openkilda.wfm.topology.flowhs.fsm.common.actions.HaFlowProcessingWithHistorySupportAction;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.haflow.create.HaFlowCreateFsm.State;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class ResourcesDeallocationAction extends
        HaFlowProcessingWithHistorySupportAction<HaFlowCreateFsm, State, Event, HaFlowCreateContext> {
    private final FlowResourcesManager resourcesManager;
    private final IslRepository islRepository;
    private final HaFlowPathRepository haFlowPathRepository;
    private final FlowPathRepository flowPathRepository;

    public ResourcesDeallocationAction(FlowResourcesManager resourcesManager, PersistenceManager persistenceManager) {
        super(persistenceManager);

        this.resourcesManager = resourcesManager;
        this.islRepository = persistenceManager.getRepositoryFactory().createIslRepository();
        this.haFlowPathRepository = persistenceManager.getRepositoryFactory().createHaFlowPathRepository();
        this.flowPathRepository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
    }

    @Override
    protected void perform(
            State from, State to, Event event, HaFlowCreateContext context, HaFlowCreateFsm stateMachine) {
        if (!haFlowRepository.exists(stateMachine.getHaFlowId())) {
            stateMachine.saveActionToHistory("Skip resources deallocation",
                    format("Skip resources deallocation. HA-flow %s has already been deleted.",
                            stateMachine.getHaFlowId()));
            return;
        }

        List<PathSegment> removedSegments = new ArrayList<>();
        for (HaFlowResources resources : stateMachine.getHaFlowResources()) {
            Stream.concat(resources.getForward().getSubPathIds().values().stream(),
                            resources.getReverse().getSubPathIds().values().stream())
                    .forEach(subPathId ->
                            flowPathRepository.remove(subPathId)
                                    .ifPresent(path -> removedSegments.addAll(path.getSegments())));
            Stream.of(resources.getForward().getPathId(), resources.getReverse().getPathId())
                    .forEach(haFlowPathRepository::remove);

            transactionManager.doInTransaction(() ->
                    resourcesManager.deallocatePathResources(
                            resources, stateMachine.getYPointMap().get(resources.getForward().getPathId())));
        }

        updateIslsForSegments(removedSegments);

        if (!stateMachine.isPathsBeenAllocated()) {
            haFlowRepository.remove(stateMachine.getHaFlowId());
        }

        stateMachine.saveActionToHistory("The resources have been deallocated");
    }

    private void updateIslsForSegments(List<PathSegment> pathSegments) {
        Set<Segment> uniqueSegments = pathSegments.stream().map(Segment::new).collect(Collectors.toSet());
        uniqueSegments.forEach(segment ->
                transactionManager.doInTransaction(() ->
                        islRepository.updateAvailableBandwidth(
                                segment.getSrcSwitchId(), segment.getSrcPort(),
                                segment.getDstSwitchId(), segment.getDstPort())));
    }

    @Value
    private static class Segment {
        public Segment(PathSegment pathSegment) {
            this.srcSwitchId = pathSegment.getSrcSwitchId();
            this.srcPort = pathSegment.getSrcPort();
            this.dstSwitchId = pathSegment.getDestSwitchId();
            this.dstPort = pathSegment.getDestPort();
        }

        SwitchId srcSwitchId;
        int srcPort;
        SwitchId dstSwitchId;
        int dstPort;
    }
}
