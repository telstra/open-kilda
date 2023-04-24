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

package org.openkilda.wfm.topology.flowhs.service.haflow;

import static java.util.Collections.emptyList;

import org.openkilda.adapter.FlowSideAdapter;
import org.openkilda.messaging.command.haflow.HaFlowPathsResponse;
import org.openkilda.messaging.command.haflow.HaFlowResponse;
import org.openkilda.messaging.model.FlowPathDto;
import org.openkilda.messaging.model.FlowPathDto.FlowPathDtoBuilder;
import org.openkilda.messaging.model.FlowPathDto.FlowProtectedPathDto;
import org.openkilda.messaging.model.FlowPathDto.FlowProtectedPathDto.FlowProtectedPathDtoBuilder;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.FlowPath;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.PathSegment;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.HaFlowRepository;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.share.mappers.FlowPathMapper;
import org.openkilda.wfm.share.service.IntersectionComputer;
import org.openkilda.wfm.topology.flowhs.mapper.HaFlowMapper;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.RetryPolicy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class HaFlowReadService {
    private final HaFlowRepository haFlowRepository;
    private final FlowRepository flowRepository;
    private final TransactionManager transactionManager;
    private final int readOperationRetriesLimit;
    private final Duration readOperationRetryDelay;

    public HaFlowReadService(@NonNull PersistenceManager persistenceManager,
                             int readOperationRetriesLimit, @NonNull Duration readOperationRetryDelay) {
        haFlowRepository = persistenceManager.getRepositoryFactory().createHaFlowRepository();
        flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
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
     * Fetches all HA-flows.
     */
    public List<HaFlowResponse> getAllHaFlows() {
        return transactionManager.doInTransaction(getReadOperationRetryPolicy(), haFlowRepository::findAll)
                .stream()
                .map(haFlow -> HaFlowMapper.INSTANCE.toHaFlowDto(haFlow, flowRepository, haFlowRepository))
                .map(HaFlowResponse::new)
                .collect(Collectors.toList());
    }

    /**
     * Gets HA-flow by ID.
     */
    public HaFlowResponse getHaFlow(@NonNull String haFlowId) throws FlowNotFoundException {
        return transactionManager.doInTransaction(getReadOperationRetryPolicy(), () ->
                        haFlowRepository.findById(haFlowId))
                .map(haFlow -> HaFlowMapper.INSTANCE.toHaFlowDto(haFlow, flowRepository, haFlowRepository))
                .map(HaFlowResponse::new)
                .orElseThrow(() -> new FlowNotFoundException(haFlowId));
    }

    /**
     * Gets HA-flow paths by ID.
     */
    public HaFlowPathsResponse getHaFlowPaths(@NonNull String haFlowId) throws FlowNotFoundException {
        return transactionManager.doInTransaction(getReadOperationRetryPolicy(), () -> {
            HaFlow haFlow = haFlowRepository.findById(haFlowId).orElseThrow(() -> new FlowNotFoundException(haFlowId));

            List<FlowPathDto> subFlowPathDtos = new ArrayList<>();
            Map<String, FlowPath> forwardPaths = new HashMap<>();
            Map<String, FlowPath> protectedForwardPaths = new HashMap<>();
            Map<String, FlowPath> reversePaths = new HashMap<>();
            Map<String, FlowPath> protectedReversePaths = new HashMap<>();

            for (FlowPath flowPath : haFlow.getForwardPath().getSubPaths()) {
                forwardPaths.put(flowPath.getHaSubFlowId(), flowPath);
            }

            for (FlowPath flowPath : haFlow.getReversePath().getSubPaths()) {
                reversePaths.put(flowPath.getHaSubFlowId(), flowPath);
            }

            if (haFlow.isAllocateProtectedPath()) {
                for (FlowPath flowPath : haFlow.getProtectedForwardPath().getSubPaths()) {
                    protectedForwardPaths.put(flowPath.getHaSubFlowId(), flowPath);
                }
                for (FlowPath flowPath : haFlow.getProtectedReversePath().getSubPaths()) {
                    protectedReversePaths.put(flowPath.getHaSubFlowId(), flowPath);
                }
            }

            for (HaSubFlow haSubFlow : haFlow.getHaSubFlows()) {
                String haSubFlowId = haSubFlow.getHaSubFlowId();

                FlowPathDtoBuilder flowPathDtoBuilder = buildFlowPathDto(haFlow, forwardPaths.get(haSubFlowId),
                        reversePaths.get(haSubFlowId));
                if (protectedForwardPaths.get(haSubFlowId) != null || protectedReversePaths.get(haSubFlowId) != null) {
                    flowPathDtoBuilder.protectedPath(buildProtectedPathDto(haFlow,
                            protectedForwardPaths.get(haSubFlowId), protectedReversePaths.get(haSubFlowId)));
                }
                subFlowPathDtos.add(flowPathDtoBuilder.build());
            }

            FlowEndpoint haFlowSharedEndpoint = haFlow.getSharedEndpoint();
            FlowEndpoint sharedEndpoint =
                    new FlowEndpoint(haFlowSharedEndpoint.getSwitchId(), haFlowSharedEndpoint.getPortNumber());
            List<PathSegment> sharedForwardPathSegments = arePathsCrossed(forwardPaths.values())
                    ? IntersectionComputer.calculatePathIntersectionFromSource(forwardPaths.values()) : emptyList();
            List<PathSegment> sharedReversePathSegments = arePathsCrossed(reversePaths.values())
                    ? IntersectionComputer.calculatePathIntersectionFromDest(reversePaths.values()) : emptyList();

            FlowPathDto sharedPath = FlowPathDto.builder()
                    .forwardPath(
                            FlowPathMapper.INSTANCE.mapToPathNodes(sharedEndpoint, sharedForwardPathSegments,
                                    null))
                    .reversePath(
                            FlowPathMapper.INSTANCE.mapToPathNodes(null, sharedReversePathSegments,
                                    sharedEndpoint))
                    .build();

            if (arePathsCrossed(protectedForwardPaths.values()) || arePathsCrossed(protectedReversePaths.values())) {
                FlowProtectedPathDto.FlowProtectedPathDtoBuilder protectedDtoBuilder =
                        FlowProtectedPathDto.builder();
                // At least 2 paths required to calculate Y-point.
                if (arePathsCrossed(protectedForwardPaths.values())) {
                    List<PathSegment> pathSegments =
                            IntersectionComputer.calculatePathIntersectionFromSource(protectedForwardPaths.values());
                    protectedDtoBuilder.forwardPath(
                            FlowPathMapper.INSTANCE.mapToPathNodes(sharedEndpoint, pathSegments, null));
                }
                if (arePathsCrossed(protectedReversePaths.values())) {
                    List<PathSegment> pathSegments =
                            IntersectionComputer.calculatePathIntersectionFromDest(protectedReversePaths.values());
                    protectedDtoBuilder.reversePath(
                            FlowPathMapper.INSTANCE.mapToPathNodes(null, pathSegments, sharedEndpoint));
                }
                sharedPath.setProtectedPath(protectedDtoBuilder.build());
            }

            return new HaFlowPathsResponse(sharedPath, subFlowPathDtos, Collections.emptyMap());
        });
    }

    private FlowPathDtoBuilder buildFlowPathDto(HaFlow haFlow, FlowPath forwardPath, FlowPath reversePath) {
        return FlowPathDto.builder()
                .id(forwardPath.getHaSubFlowId())
                .forwardPath(FlowPathMapper.INSTANCE.mapToPathNodes(forwardPath, getInPort(haFlow, forwardPath),
                        getOutPort(haFlow, forwardPath)))
                .reversePath(FlowPathMapper.INSTANCE.mapToPathNodes(reversePath, getInPort(haFlow, reversePath),
                        getOutPort(haFlow, reversePath)));
    }

    private FlowProtectedPathDto buildProtectedPathDto(HaFlow haFlow, FlowPath forwardPath, FlowPath reversePath) {
        FlowProtectedPathDtoBuilder builder = FlowProtectedPathDto.builder();
        if (forwardPath != null) {
            builder.forwardPath(FlowPathMapper.INSTANCE.mapToPathNodes(forwardPath, getInPort(haFlow, forwardPath),
                    getOutPort(haFlow, forwardPath)));
        }
        if (reversePath != null) {
            builder.reversePath(FlowPathMapper.INSTANCE.mapToPathNodes(reversePath, getInPort(haFlow, reversePath),
                    getOutPort(haFlow, reversePath)));
        }
        return builder.build();
    }

    private int getInPort(HaFlow haFlow, FlowPath flowPath) {
        return FlowSideAdapter.makeIngressAdapter(haFlow, flowPath).getEndpoint().getPortNumber();
    }

    private int getOutPort(HaFlow haFlow, FlowPath flowPath) {
        return FlowSideAdapter.makeEgressAdapter(haFlow, flowPath).getEndpoint().getPortNumber();
    }

    private boolean arePathsCrossed(Collection<FlowPath> paths) {
        return paths.size() >= 2 && paths.stream().noneMatch(FlowPath::isOneSwitchPath);
    }
}
