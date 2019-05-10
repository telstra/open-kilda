/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.topology.nbworker.services;

import org.openkilda.messaging.payload.flow.GroupFlowPathPayload;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPair;
import org.openkilda.model.SwitchId;
import org.openkilda.model.UnidirectionalFlow;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowPairRepository;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.error.IslNotFoundException;
import org.openkilda.wfm.share.mappers.FlowPathMapper;
import org.openkilda.wfm.share.service.IntersectionComputer;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class FlowOperationsService {

    private TransactionManager transactionManager;
    private IslRepository islRepository;
    private FlowRepository flowRepository;
    private FlowPairRepository flowPairRepository;
    private FlowPathRepository flowPathRepository;

    public FlowOperationsService(RepositoryFactory repositoryFactory, TransactionManager transactionManager) {
        this.islRepository = repositoryFactory.createIslRepository();
        this.flowRepository = repositoryFactory.createFlowRepository();
        this.flowPairRepository = repositoryFactory.createFlowPairRepository();
        this.flowPathRepository = repositoryFactory.createFlowPathRepository();
        this.transactionManager = transactionManager;
    }

    /**
     * Return all flows for a particular link.
     *
     * @param srcSwitchId source switch id.
     * @param srcPort     source port.
     * @param dstSwitchId destination switch id.
     * @param dstPort     destination port.
     * @return all flows for a particular link.
     * @throws IslNotFoundException if there is no link with these parameters.
     */
    public Collection<FlowPair> getFlowsForLink(SwitchId srcSwitchId, Integer srcPort,
                                                SwitchId dstSwitchId, Integer dstPort)
            throws IslNotFoundException {

        if (!islRepository.findByEndpoints(srcSwitchId, srcPort, dstSwitchId, dstPort).isPresent()) {
            throw new IslNotFoundException(srcSwitchId, srcPort, dstSwitchId, dstPort);
        }

        return flowPairRepository.findWithSegmentInPath(srcSwitchId, srcPort, dstSwitchId, dstPort);
    }

    /**
     * Return flows for a switch.
     *
     * @param switchId switch id.
     * @return all flows for a switch.
     */
    public Set<String> getFlowIdsForSwitch(SwitchId switchId) {
        return flowRepository.findFlowIdsWithSwitchInPath(switchId);
    }

    /**
     * Returns flow path. If flow has group, returns also path for each flow in group.
     *
     * @param flowId the flow to get a path.
     */
    public List<GroupFlowPathPayload> getFlowPath(String flowId) throws FlowNotFoundException {
        Flow currentFlow = flowRepository.findById(flowId)
                .orElseThrow(() -> new FlowNotFoundException(flowId));

        String groupId = currentFlow.getGroupId();
        if (groupId == null) {
            return Collections.singletonList(
                    toGroupFlowPathPayloadBuilder(currentFlow).build());
        } else {
            Collection<Flow> flowsInGroup = flowRepository.findByGroupId(groupId);
            IntersectionComputer intersectionComputer =
                    new IntersectionComputer(flowId, flowPathRepository.findByFlowGroupId(groupId));

            // other flows in group
            List<GroupFlowPathPayload> payloads = flowsInGroup.stream()
                    .filter(e -> !e.getFlowId().equals(flowId))
                    .map(e -> this.toGroupFlowPathPayloadBuilder(e)
                            .segmentsStats(intersectionComputer.getOverlappingStats(e.getFlowId()))
                            .build())
                    .collect(Collectors.toList());
            // current flow
            payloads.add(this.toGroupFlowPathPayloadBuilder(currentFlow)
                    .segmentsStats(intersectionComputer.getOverlappingStats())
                    .build());

            return payloads;
        }
    }

    private GroupFlowPathPayload.GroupFlowPathPayloadBuilder toGroupFlowPathPayloadBuilder(Flow flow) {
        return GroupFlowPathPayload.builder()
                .id(flow.getFlowId())
                .forwardPath(FlowPathMapper.INSTANCE.mapToPathNodes(flow.getForwardPath()))
                .reversePath(FlowPathMapper.INSTANCE.mapToPathNodes(flow.getReversePath()));
    }

    /**
     * Update flow.
     *
     * @param flow flow.
     * @return updated flow.
     */
    public UnidirectionalFlow updateFlow(UnidirectionalFlow flow) throws FlowNotFoundException {
        return transactionManager.doInTransaction(() -> {
            Optional<FlowPair> foundFlow = flowPairRepository.findById(flow.getFlowId());
            if (!foundFlow.isPresent()) {
                return Optional.<UnidirectionalFlow>empty();
            }
            UnidirectionalFlow forwardFlow = foundFlow.get().getForward();
            Flow currentFlow = forwardFlow.getFlow();

            if (flow.getMaxLatency() != null) {
                currentFlow.setMaxLatency(flow.getMaxLatency());
            }
            if (flow.getPriority() != null) {
                currentFlow.setPriority(flow.getPriority());
            }

            flowRepository.createOrUpdate(currentFlow);

            return Optional.of(forwardFlow);

        }).orElseThrow(() -> new FlowNotFoundException(flow.getFlowId()));
    }
}
