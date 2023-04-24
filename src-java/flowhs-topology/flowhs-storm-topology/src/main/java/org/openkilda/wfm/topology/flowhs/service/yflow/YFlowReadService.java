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

package org.openkilda.wfm.topology.flowhs.service.yflow;

import static java.util.Collections.emptyList;

import org.openkilda.messaging.command.yflow.SubFlowDto;
import org.openkilda.messaging.command.yflow.SubFlowsResponse;
import org.openkilda.messaging.command.yflow.YFlowPathsResponse;
import org.openkilda.messaging.command.yflow.YFlowResponse;
import org.openkilda.messaging.model.FlowPathDto;
import org.openkilda.messaging.model.FlowPathDto.FlowProtectedPathDto;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathSegment;
import org.openkilda.model.YFlow;
import org.openkilda.model.YFlow.SharedEndpoint;
import org.openkilda.model.YSubFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.HaFlowRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.share.mappers.FlowPathMapper;
import org.openkilda.wfm.share.service.IntersectionComputer;
import org.openkilda.wfm.topology.flowhs.mapper.YFlowMapper;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.RetryPolicy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class YFlowReadService {
    private final YFlowRepository yFlowRepository;
    private final FlowRepository flowRepository;
    private final HaFlowRepository haFlowRepository;
    private final FlowPathRepository flowPathRepository;
    private final TransactionManager transactionManager;
    private final int readOperationRetriesLimit;
    private final Duration readOperationRetryDelay;

    public YFlowReadService(@NonNull PersistenceManager persistenceManager,
                            int readOperationRetriesLimit, @NonNull Duration readOperationRetryDelay) {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        yFlowRepository = repositoryFactory.createYFlowRepository();
        flowRepository = repositoryFactory.createFlowRepository();
        haFlowRepository = repositoryFactory.createHaFlowRepository();
        flowPathRepository = repositoryFactory.createFlowPathRepository();
        transactionManager = persistenceManager.getTransactionManager();
        this.readOperationRetriesLimit = readOperationRetriesLimit;
        this.readOperationRetryDelay = readOperationRetryDelay;
    }

    private <T> RetryPolicy<T> getReadOperationRetryPolicy() {
        return new RetryPolicy<T>()
                .handle(PersistenceException.class)
                .withDelay(readOperationRetryDelay)
                .withMaxRetries(readOperationRetriesLimit)
                .onRetry(e -> log.debug("Failure in transaction. Retrying #{}...", e.getAttemptCount(),
                        e.getLastFailure()))
                .onRetriesExceeded(e -> log.error("Failure in transaction. No more retries", e.getFailure()));
    }

    /**
     * Fetches all y-flows.
     */
    public List<YFlowResponse> getAllYFlows() {
        Collection<YFlow> yFlows = transactionManager.doInTransaction(getReadOperationRetryPolicy(),
                yFlowRepository::findAll);
        return yFlows.stream()
                .map(flow -> YFlowMapper.INSTANCE.toYFlowDto(flow, flowRepository, haFlowRepository))
                .map(YFlowResponse::new)
                .collect(Collectors.toList());
    }

    /**
     * Gets y-flow by id.
     */
    public YFlowResponse getYFlow(@NonNull String yFlowId) throws FlowNotFoundException {
        return transactionManager.doInTransaction(getReadOperationRetryPolicy(), () ->
                        yFlowRepository.findById(yFlowId))
                .map(flow -> YFlowMapper.INSTANCE.toYFlowDto(flow, flowRepository, haFlowRepository))
                .map(YFlowResponse::new)
                .orElseThrow(() -> new FlowNotFoundException(yFlowId));
    }

    /**
     * Gets y-flow paths.
     */
    public YFlowPathsResponse getYFlowPaths(@NonNull String yFlowId) throws FlowNotFoundException {
        return transactionManager.doInTransaction(getReadOperationRetryPolicy(), () -> {
            YFlow yFlow = yFlowRepository.findById(yFlowId)
                    .orElseThrow(() -> new FlowNotFoundException(yFlowId));
            Set<FlowPath> mainForwardPaths = new HashSet<>();
            Set<FlowPath> mainReversePaths = new HashSet<>();
            Set<FlowPath> protectedForwardPaths = new HashSet<>();
            Set<FlowPath> protectedReversePaths = new HashSet<>();
            List<FlowPathDto> subFlowPathDtos = new ArrayList<>();
            Map<String, List<FlowPathDto>> diverseWithFlows = new HashMap<>();

            for (YSubFlow subFlow : yFlow.getSubFlows()) {
                Flow flow = subFlow.getFlow();
                if (!flow.isOneSwitchFlow()) {
                    mainForwardPaths.add(flow.getForwardPath());
                    mainReversePaths.add(flow.getReversePath());
                }
                FlowPathDto.FlowPathDtoBuilder pathDtoBuilder = buildFlowPathDto(flow);
                FlowProtectedPathDto.FlowProtectedPathDtoBuilder protectedDtoBuilder = null;
                if (flow.isAllocateProtectedPath()) {
                    protectedDtoBuilder = buildFlowProtectedPathDto(flow);
                    if (flow.getProtectedForwardPath() != null && !flow.isOneSwitchFlow()) {
                        protectedForwardPaths.add(flow.getProtectedForwardPath());
                    }
                    if (flow.getProtectedReversePath() != null && !flow.isOneSwitchFlow()) {
                        protectedReversePaths.add(flow.getProtectedReversePath());
                    }
                }

                String diverseGroupId = flow.getDiverseGroupId();
                if (diverseGroupId != null) {
                    Collection<FlowPath> flowPathsInDiverseGroup = flowPathRepository.findByFlowGroupId(diverseGroupId);
                    IntersectionComputer primaryIntersectionComputer = new IntersectionComputer(
                            flow.getFlowId(), flow.getForwardPathId(), flow.getReversePathId(),
                            flowPathsInDiverseGroup);
                    pathDtoBuilder.segmentsStats(primaryIntersectionComputer.getOverlappingStats());

                    Collection<Flow> flowsInDiverseGroup = flowRepository.findByDiverseGroupId(diverseGroupId).stream()
                            .filter(f -> !flow.getFlowId().equals(f.getFlowId()))
                            .collect(Collectors.toList());

                    List<FlowPathDto> groupFlowsWithOverlappingStats = new ArrayList<>();
                    flowsInDiverseGroup.stream()
                            .map(diverseFlow -> buildGroupPathFlowDto(diverseFlow, true, primaryIntersectionComputer))
                            .forEach(groupFlowsWithOverlappingStats::add);

                    if (protectedDtoBuilder != null) {
                        IntersectionComputer protectedIntersectionComputer = new IntersectionComputer(
                                flow.getFlowId(), flow.getProtectedForwardPathId(), flow.getProtectedReversePathId(),
                                flowPathsInDiverseGroup);
                        protectedDtoBuilder.segmentsStats(
                                protectedIntersectionComputer.getOverlappingStats());

                        flowsInDiverseGroup.stream()
                                .map(diverseFlow ->
                                        buildGroupPathFlowDto(diverseFlow, false, protectedIntersectionComputer))
                                .forEach(groupFlowsWithOverlappingStats::add);
                    }

                    diverseWithFlows.put(flow.getFlowId(), groupFlowsWithOverlappingStats);
                }

                if (protectedDtoBuilder != null) {
                    pathDtoBuilder.protectedPath(protectedDtoBuilder.build());
                }
                subFlowPathDtos.add(pathDtoBuilder.build());
            }

            SharedEndpoint yFlowSharedEndpoint = yFlow.getSharedEndpoint();
            FlowEndpoint sharedEndpoint =
                    new FlowEndpoint(yFlowSharedEndpoint.getSwitchId(), yFlowSharedEndpoint.getPortNumber());
            List<PathSegment> sharedForwardPathSegments = mainForwardPaths.size() >= 2
                    ? IntersectionComputer.calculatePathIntersectionFromSource(mainForwardPaths) : emptyList();
            List<PathSegment> sharedReversePathSegments = mainReversePaths.size() >= 2
                    ? IntersectionComputer.calculatePathIntersectionFromDest(mainReversePaths) : emptyList();
            FlowPathDto sharedPath = FlowPathDto.builder()
                    .forwardPath(
                            FlowPathMapper.INSTANCE.mapToPathNodes(sharedEndpoint, sharedForwardPathSegments, null))
                    .reversePath(
                            FlowPathMapper.INSTANCE.mapToPathNodes(null, sharedReversePathSegments, sharedEndpoint))
                    .build();

            if (protectedForwardPaths.size() >= 2 || protectedReversePaths.size() >= 2) {
                FlowProtectedPathDto.FlowProtectedPathDtoBuilder protectedDtoBuilder =
                        FlowProtectedPathDto.builder();
                // At least 2 paths required to calculate Y-point.
                if (protectedForwardPaths.size() >= 2) {
                    List<PathSegment> pathSegments =
                            IntersectionComputer.calculatePathIntersectionFromSource(protectedForwardPaths);
                    protectedDtoBuilder.forwardPath(
                            FlowPathMapper.INSTANCE.mapToPathNodes(sharedEndpoint, pathSegments, null));
                }
                if (protectedReversePaths.size() >= 2) {
                    List<PathSegment> pathSegments =
                            IntersectionComputer.calculatePathIntersectionFromDest(protectedReversePaths);
                    protectedDtoBuilder.reversePath(
                            FlowPathMapper.INSTANCE.mapToPathNodes(null, pathSegments, sharedEndpoint));
                }
                sharedPath.setProtectedPath(protectedDtoBuilder.build());
            }

            return new YFlowPathsResponse(sharedPath, subFlowPathDtos, diverseWithFlows);
        });
    }

    private FlowPathDto.FlowPathDtoBuilder buildFlowPathDto(Flow flow) {
        return FlowPathDto.builder()
                .id(flow.getFlowId())
                .forwardPath(FlowPathMapper.INSTANCE.mapToPathNodes(flow, flow.getForwardPath()))
                .reversePath(FlowPathMapper.INSTANCE.mapToPathNodes(flow, flow.getReversePath()));
    }

    private FlowProtectedPathDto.FlowProtectedPathDtoBuilder buildFlowProtectedPathDto(Flow flow) {
        FlowProtectedPathDto.FlowProtectedPathDtoBuilder protectedDtoBuilder =
                FlowProtectedPathDto.builder();
        if (flow.getProtectedForwardPath() != null) {
            protectedDtoBuilder.forwardPath(
                    FlowPathMapper.INSTANCE.mapToPathNodes(flow, flow.getProtectedForwardPath()));

        }
        if (flow.getProtectedReversePath() != null) {
            protectedDtoBuilder.reversePath(
                    FlowPathMapper.INSTANCE.mapToPathNodes(flow, flow.getProtectedReversePath()));
        }
        return protectedDtoBuilder;
    }

    private FlowPathDto buildGroupPathFlowDto(Flow flow, boolean primaryPathCorrespondStat,
                                              IntersectionComputer intersectionComputer) {
        FlowPathDto.FlowPathDtoBuilder builder = buildFlowPathDto(flow)
                .primaryPathCorrespondStat(primaryPathCorrespondStat)
                .segmentsStats(
                        intersectionComputer.getOverlappingStats(flow.getForwardPathId(),
                                flow.getReversePathId()));
        if (flow.isAllocateProtectedPath()) {
            FlowProtectedPathDto.FlowProtectedPathDtoBuilder protectedPathBuilder = buildFlowProtectedPathDto(flow)
                    .segmentsStats(
                            intersectionComputer.getOverlappingStats(
                                    flow.getProtectedForwardPathId(), flow.getProtectedReversePathId()));
            builder.protectedPath(protectedPathBuilder.build());
        }
        return builder.build();
    }

    /**
     * Gets y-flow sub-flows.
     */
    public SubFlowsResponse getYFlowSubFlows(@NonNull String yFlowId) throws FlowNotFoundException {
        return transactionManager.doInTransaction(getReadOperationRetryPolicy(), () -> {
            List<SubFlowDto> subFlows = yFlowRepository.findById(yFlowId)
                    .orElseThrow(() -> new FlowNotFoundException(yFlowId))
                    .getSubFlows().stream()
                    .map(YFlowMapper.INSTANCE::toSubFlowDto)
                    .collect(Collectors.toList());
            return new SubFlowsResponse(subFlows);
        });
    }
}
