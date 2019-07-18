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

package org.openkilda.wfm.topology.flowhs.fsm.create.action;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.exception.FlowProcessingException;
import org.openkilda.wfm.topology.flowhs.fsm.common.action.FlowProcessingAction;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateContext;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.Event;
import org.openkilda.wfm.topology.flowhs.fsm.create.FlowCreateFsm.State;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;

import java.util.List;

@Slf4j
public class ResourcesDeallocationAction extends FlowProcessingAction<FlowCreateFsm, State, Event, FlowCreateContext> {

    private final TransactionManager transactionManager;
    private final FlowResourcesManager resourcesManager;
    private final IslRepository islRepository;

    public ResourcesDeallocationAction(FlowResourcesManager resourcesManager, PersistenceManager persistenceManager) {
        super(persistenceManager);
        this.transactionManager = persistenceManager.getTransactionManager();
        this.resourcesManager = resourcesManager;
        this.islRepository = persistenceManager.getRepositoryFactory().createIslRepository();
    }

    @Override
    protected void perform(State from, State to, Event event, FlowCreateContext context, FlowCreateFsm stateMachine) {
        Flow flow;
        try {
            flow = getFlow(stateMachine.getFlowId());
        } catch (FlowProcessingException e) {
            log.debug("Skip resources deallocation: flow {} has already been deleted", stateMachine.getFlowId());
            return;
        }

        FlowPath forwardPath = getFlowPath(stateMachine.getForwardPathId());
        resourcesManager.deallocatePathResources(forwardPath.getPathId(),
                forwardPath.getCookie().getUnmaskedValue(), flow.getEncapsulationType());
        FlowPath reversePath = getFlowPath(stateMachine.getReversePathId());
        resourcesManager.deallocatePathResources(reversePath.getPathId(),
                reversePath.getCookie().getUnmaskedValue(), flow.getEncapsulationType());

        if (flow.isAllocateProtectedPath()) {
            FlowPath protectedForward = getFlowPath(stateMachine.getProtectedForwardPathId());
            resourcesManager.deallocatePathResources(protectedForward.getPathId(),
                    protectedForward.getCookie().getUnmaskedValue(), flow.getEncapsulationType());

            FlowPath protectedReverse = getFlowPath(stateMachine.getProtectedReversePathId());
            resourcesManager.deallocatePathResources(protectedReverse.getPathId(),
                    protectedReverse.getCookie().getUnmaskedValue(), flow.getEncapsulationType());
        }

        List<PathSegment> segments =
                ListUtils.union(flow.getForwardPath().getSegments(), flow.getReversePath().getSegments());
        transactionManager.doInTransaction(() -> {
            for (PathSegment pathSegment : segments) {
                updateIslAvailableBandwidth(flow.getFlowId(), pathSegment.getSrcSwitch().getSwitchId(),
                        pathSegment.getSrcPort(), pathSegment.getDestSwitch().getSwitchId(), pathSegment.getDestPort());
            }
        });
        log.debug("Flow resources have been deallocated for flow {}", flow.getFlowId());
    }

    private void updateIslAvailableBandwidth(String flowId, SwitchId srcSwitchId, int srcPort,
                                             SwitchId dstSwitchId, int dstPort) {
        long usedBandwidth = flowPathRepository.getUsedBandwidthBetweenEndpoints(
                srcSwitchId, srcPort, dstSwitchId, dstPort);

        islRepository.findByEndpoints(srcSwitchId, srcPort, dstSwitchId, dstPort)
                .ifPresent(isl -> {
                    isl.setAvailableBandwidth(isl.getMaxBandwidth() - usedBandwidth);

                    islRepository.createOrUpdate(isl);
                    log.debug("Released used bandwidth from flow {} on the link {}", flowId, isl);
                });
    }
}
