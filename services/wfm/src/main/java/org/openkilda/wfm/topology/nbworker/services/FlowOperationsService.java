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
import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.model.FlowPair;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathSegment;
import org.openkilda.model.SwitchId;
import org.openkilda.model.UnidirectionalFlow;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowPairRepository;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.error.IslNotFoundException;
import org.openkilda.wfm.share.service.IntersectionComputer;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
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
    private FlowPairRepository flowPairRepository;
    private FlowPathRepository flowPathRepository;

    public FlowOperationsService(RepositoryFactory repositoryFactory, TransactionManager transactionManager) {
        this.islRepository = repositoryFactory.createIslRepository();
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
    public Collection<FlowPair> getFlowIdsForLink(SwitchId srcSwitchId, Integer srcPort,
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
        return flowPairRepository.findFlowIdsBySwitch(switchId);
    }

    /**
     * Returns flow path. If flow has group, returns also path for each flow in group.
     *
     * @param flowId the flow to get a path.
     */
    public List<GroupFlowPathPayload> getFlowPath(String flowId) throws FlowNotFoundException {
        FlowPair currentFlow = flowPairRepository.findById(flowId)
                .orElseThrow(() -> new FlowNotFoundException(flowId));

        String groupId = currentFlow.getForward().getGroupId();
        if (groupId == null) {
            return Collections.singletonList(
                    toGroupFlowPathPayloadBuilder(currentFlow).build());
        } else {
            Collection<FlowPair> flowPairsInGroup = flowPairRepository.findByGroupId(groupId);
            IntersectionComputer intersectionComputer =
                    new IntersectionComputer(flowId, flowPathRepository.findByFlowGroupId(groupId));

            // other flows in group
            List<GroupFlowPathPayload> payloads = flowPairsInGroup.stream()
                    .filter(e -> !e.getForward().getFlowId().equals(flowId))
                    .map(e -> this.toGroupFlowPathPayloadBuilder(e)
                            .segmentsStats(intersectionComputer.getOverlappingStats(e.getForward().getFlowId()))
                            .build())
                    .collect(Collectors.toList());
            // current flow
            payloads.add(this.toGroupFlowPathPayloadBuilder(currentFlow)
                    .segmentsStats(intersectionComputer.getOverlappingStats())
                    .build());

            return payloads;
        }
    }

    private GroupFlowPathPayload.GroupFlowPathPayloadBuilder toGroupFlowPathPayloadBuilder(FlowPair flowPair) {
        return GroupFlowPathPayload.builder()
                .id(flowPair.getForward().getFlowId())
                .forwardPath(buildPathFromFlow(flowPair.getForward()))
                .reversePath(buildPathFromFlow(flowPair.getReverse()));
    }

    private List<PathNodePayload> buildPathFromFlow(UnidirectionalFlow flow) {
        List<PathNodePayload> resultList = new ArrayList<>();

        FlowPath flowPath = flow.getFlowPath();
        if (flowPath.getSegments().isEmpty()) {
            resultList.add(
                    new PathNodePayload(flow.getSrcSwitch().getSwitchId(), flow.getSrcPort(), flow.getDestPort()));
        } else {
            List<PathSegment> segments = flowPath.getSegments();

            resultList.add(new PathNodePayload(flow.getSrcSwitch().getSwitchId(), flow.getSrcPort(),
                    segments.get(0).getSrcPort()));

            for (int i = 1; i < segments.size(); i += 2) {
                PathSegment inputNode = segments.get(i - 1);
                PathSegment outputNode = segments.get(i);

                resultList.add(new PathNodePayload(inputNode.getDestSwitch().getSwitchId(), inputNode.getDestPort(),
                        outputNode.getSrcPort()));
            }

            resultList.add(new PathNodePayload(flow.getDestSwitch().getSwitchId(),
                    segments.get(segments.size() - 1).getDestPort(), flow.getDestPort()));
        }

        return resultList;
    }

    /**
     * Update flow.
     *
     * @param flow flow.
     * @return updated flow.
     */
    public UnidirectionalFlow updateFlow(UnidirectionalFlow flow) throws FlowNotFoundException {
        return transactionManager.doInTransaction(() -> {
            Optional<FlowPair> foundFlowPair = flowPairRepository.findById(flow.getFlowId());
            if (!foundFlowPair.isPresent()) {
                return Optional.<UnidirectionalFlow>empty();
            }
            FlowPair currentFlowPair = foundFlowPair.get();

            UnidirectionalFlow forwardFlow = currentFlowPair.getForward();
            UnidirectionalFlow reverseFlow = currentFlowPair.getReverse();

            if (flow.getMaxLatency() != null) {
                forwardFlow.setMaxLatency(flow.getMaxLatency());
                reverseFlow.setMaxLatency(flow.getMaxLatency());
            }
            if (flow.getPriority() != null) {
                forwardFlow.setPriority(flow.getPriority());
                reverseFlow.setPriority(flow.getPriority());
            }

            flowPairRepository.createOrUpdate(currentFlowPair);

            return Optional.of(forwardFlow);

        }).orElseThrow(() -> new FlowNotFoundException(flow.getFlowId()));
    }
}
