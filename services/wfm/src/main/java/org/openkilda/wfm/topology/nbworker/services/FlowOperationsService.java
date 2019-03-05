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

import static org.apache.commons.collections4.ListUtils.union;

import org.openkilda.messaging.model.FlowPathDto;
import org.openkilda.messaging.model.FlowPathDto.FlowPathDtoBuilder;
import org.openkilda.messaging.model.FlowPathDto.FlowProtectedPathDto;
import org.openkilda.messaging.payload.flow.PathNodePayload;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPair;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathSegment;
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
        return flowPairRepository.findFlowIdsWithSwitchInPath(switchId);
    }

    /**
     * Returns flow path. If flow has group, returns also path for each flow in group.
     *
     * @param flowId the flow to get a path.
     */
    public List<FlowPathDto> getFlowPath(String flowId) throws FlowNotFoundException {
        Flow flow = flowRepository.findById(flowId)
                .orElseThrow(() -> new FlowNotFoundException(flowId));

        String groupId = flow.getGroupId();
        if (groupId == null) {
            return Collections.singletonList(
                    toFlowPathDtoBuilder(flow)
                            .build());
        } else {
            Collection<Flow> flowsInGroup = flowRepository.findByGroupId(groupId);
            Collection<FlowPath> flowPathsInGroup = flowPathRepository.findByFlowGroupId(groupId);

            IntersectionComputer primaryIntersectionComputer = new IntersectionComputer(
                    flow.getFlowId(), flow.getForwardPathId(), flow.getReversePathId(), flowPathsInGroup);

            // target flow primary path
            FlowPathDtoBuilder targetFlowDtoBuilder = this.toFlowPathDtoBuilder(flow)
                    .segmentsStats(primaryIntersectionComputer.getOverlappingStats());

            // other flows in the the group
            List<FlowPathDto> payloads = flowsInGroup.stream()
                    .filter(e -> !e.getFlowId().equals(flowId))
                    .map(e -> this.mapGroupPathFlowDto(e, true, primaryIntersectionComputer))
                    .collect(Collectors.toList());

            if (flow.isAllocateProtectedPath()) {
                IntersectionComputer protectedIntersectionComputer = new IntersectionComputer(
                        flow.getFlowId(), flow.getProtectedForwardPathId(), flow.getProtectedReversePathId(),
                        flowPathsInGroup);

                // target flow protected path
                targetFlowDtoBuilder.protectedPath(FlowProtectedPathDto.builder()
                        .forwardPath(buildPathFromFlow(flow, flow.getProtectedForwardPath()))
                        .reversePath(buildPathFromFlow(flow, flow.getProtectedReversePath()))
                        .segmentsStats(
                                protectedIntersectionComputer.getOverlappingStats())
                        .build());

                // other flows in the the group
                List<FlowPathDto> protectedPathPayloads = flowsInGroup.stream()
                        .filter(e -> !e.getFlowId().equals(flowId))
                        .map(e -> this.mapGroupPathFlowDto(e, false, protectedIntersectionComputer))
                        .collect(Collectors.toList());
                payloads = union(payloads, protectedPathPayloads);
            }

            payloads.add(targetFlowDtoBuilder.build());

            return payloads;
        }
    }

    private FlowPathDto mapGroupPathFlowDto(Flow flow, boolean primaryPathCorrespondStat,
                                            IntersectionComputer intersectionComputer) {
        FlowPathDtoBuilder builder = this.toFlowPathDtoBuilder(flow)
                .primaryPathCorrespondStat(primaryPathCorrespondStat)
                .segmentsStats(
                        intersectionComputer.getOverlappingStats(flow.getForwardPathId(), flow.getReversePathId()));
        if (flow.isAllocateProtectedPath()) {
            builder.protectedPath(FlowProtectedPathDto.builder()
                    .forwardPath(buildPathFromFlow(flow, flow.getProtectedForwardPath()))
                    .reversePath(buildPathFromFlow(flow, flow.getProtectedReversePath()))
                    .segmentsStats(
                            intersectionComputer.getOverlappingStats(
                                    flow.getProtectedForwardPathId(), flow.getProtectedReversePathId()))
                    .build());
        }
        return builder.build();
    }

    private FlowPathDtoBuilder toFlowPathDtoBuilder(Flow flow) {
        return FlowPathDto.builder()
                .id(flow.getFlowId())
                .forwardPath(buildPathFromFlow(flow, flow.getForwardPath()))
                .reversePath(buildPathFromFlow(flow, flow.getReversePath()));
    }

    private List<PathNodePayload> buildPathFromFlow(Flow flow, FlowPath flowPath) {
        List<PathNodePayload> resultList = new ArrayList<>();

        boolean forward = isForward(flow, flowPath);
        int inPort = forward ? flow.getSrcPort() : flow.getDestPort();
        int outPort = forward ? flow.getDestPort() : flow.getSrcPort();

        if (flowPath.getSegments().isEmpty()) {
            resultList.add(
                    new PathNodePayload(flowPath.getSrcSwitch().getSwitchId(), inPort, outPort));
        } else {
            List<PathSegment> pathSegments = flowPath.getSegments();

            resultList.add(new PathNodePayload(flowPath.getSrcSwitch().getSwitchId(), inPort,
                    pathSegments.get(0).getSrcPort()));

            for (int i = 1; i < pathSegments.size(); i++) {
                PathSegment inputNode = pathSegments.get(i - 1);
                PathSegment outputNode = pathSegments.get(i);

                resultList.add(new PathNodePayload(inputNode.getDestSwitch().getSwitchId(), inputNode.getDestPort(),
                        outputNode.getSrcPort()));
            }

            resultList.add(new PathNodePayload(flowPath.getDestSwitch().getSwitchId(),
                    pathSegments.get(pathSegments.size() - 1).getDestPort(), outPort));
        }

        return resultList;
    }

    private boolean isForward(Flow flow, FlowPath flowPath) {
        return flowPath.getPathId().equals(flow.getForwardPathId())
                || flowPath.getPathId().equals(flow.getProtectedForwardPathId());
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
