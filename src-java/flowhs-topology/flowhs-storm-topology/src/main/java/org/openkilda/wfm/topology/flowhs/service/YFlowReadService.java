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

package org.openkilda.wfm.topology.flowhs.service;

import org.openkilda.messaging.command.yflow.SubFlowDto;
import org.openkilda.messaging.command.yflow.SubFlowPathDto;
import org.openkilda.messaging.command.yflow.SubFlowsResponse;
import org.openkilda.messaging.command.yflow.YFlowPathsResponse;
import org.openkilda.messaging.command.yflow.YFlowResponse;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPath;
import org.openkilda.model.PathSegment;
import org.openkilda.model.YFlow;
import org.openkilda.model.YSubFlow;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.repositories.YFlowRepository;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.wfm.error.FlowNotFoundException;
import org.openkilda.wfm.share.mappers.FlowPathMapper;
import org.openkilda.wfm.share.service.IntersectionComputer;
import org.openkilda.wfm.topology.flowhs.mapper.YFlowMapper;

import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.RetryPolicy;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class YFlowReadService {
    private final YFlowRepository yFlowRepository;
    private final TransactionManager transactionManager;
    private final int readOperationRetriesLimit;
    private final Duration readOperationRetryDelay;

    public YFlowReadService(PersistenceManager persistenceManager,
                            int readOperationRetriesLimit, Duration readOperationRetryDelay) {
        this.yFlowRepository = persistenceManager.getRepositoryFactory().createYFlowRepository();
        this.transactionManager = persistenceManager.getTransactionManager();
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
                .map(YFlowMapper.INSTANCE::toYFlowDto)
                .map(YFlowResponse::new)
                .collect(Collectors.toList());
    }

    /**
     * Gets y-flow by id.
     */
    public YFlowResponse getYFlow(String yFlowId) throws FlowNotFoundException {
        return transactionManager.doInTransaction(getReadOperationRetryPolicy(), () ->
                        yFlowRepository.findById(yFlowId))
                .map(YFlowMapper.INSTANCE::toYFlowDto)
                .map(YFlowResponse::new)
                .orElseThrow(() -> new FlowNotFoundException(yFlowId));
    }

    /**
     * Gets y-flow paths.
     */
    public YFlowPathsResponse getYFlowPaths(String yFlowId) throws FlowNotFoundException {
        return transactionManager.doInTransaction(getReadOperationRetryPolicy(), () -> {
            Set<YSubFlow> subFlows = yFlowRepository.findById(yFlowId)
                    .orElseThrow(() -> new FlowNotFoundException(yFlowId))
                    .getSubFlows();
            Set<FlowPath> mainPaths = new HashSet<>();
            Set<FlowPath> protectedPaths = new HashSet<>();
            for (YSubFlow subFlow : subFlows) {
                Flow flow = subFlow.getFlow();
                mainPaths.add(flow.getForwardPath());
                if (flow.isAllocateProtectedPath()) {
                    protectedPaths.add(flow.getProtectedForwardPath());
                }
            }

            List<PathSegment> sharedPathSegments = IntersectionComputer.calculatePathIntersectionFromSource(mainPaths);
            PathInfoData sharedPath = FlowPathMapper.INSTANCE.map(sharedPathSegments);

            PathInfoData sharedProtectedPath;
            if (protectedPaths.isEmpty()) {
                sharedProtectedPath = new PathInfoData();
            } else {
                List<PathSegment> pathSegments =
                        IntersectionComputer.calculatePathIntersectionFromSource(protectedPaths);
                sharedProtectedPath = FlowPathMapper.INSTANCE.map(pathSegments);
            }

            List<SubFlowPathDto> subFlowPathDtos = mainPaths.stream()
                    .map(flowPath -> new SubFlowPathDto(flowPath.getFlowId(), FlowPathMapper.INSTANCE.map(flowPath)))
                    .collect(Collectors.toList());
            List<SubFlowPathDto> subFlowProtectedPathDtos = protectedPaths.stream()
                    .map(flowPath -> new SubFlowPathDto(flowPath.getFlowId(), FlowPathMapper.INSTANCE.map(flowPath)))
                    .collect(Collectors.toList());
            return new YFlowPathsResponse(sharedPath, subFlowPathDtos, sharedProtectedPath, subFlowProtectedPathDtos);
        });
    }

    /**
     * Gets y-flow sub-flows.
     */
    public SubFlowsResponse getYFlowSubFlows(String yFlowId) throws FlowNotFoundException {
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
