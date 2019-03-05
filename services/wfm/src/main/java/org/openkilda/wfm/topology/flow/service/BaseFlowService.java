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

package org.openkilda.wfm.topology.flow.service;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowPair;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathId;
import org.openkilda.model.TransitVlan;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowPairRepository;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.TransitVlanRepository;
import org.openkilda.wfm.share.flow.resources.transitvlan.TransitVlanEncapsulation;
import org.openkilda.wfm.topology.flow.model.FlowPathsWithEncapsulation;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Slf4j
public class BaseFlowService {
    protected TransactionManager transactionManager;
    private FlowPairRepository flowPairRepository;
    private FlowRepository flowRepository;
    private FlowPathRepository flowPathRepository;
    private TransitVlanRepository transitVlanRepository;

    public BaseFlowService(PersistenceManager persistenceManager) {
        transactionManager = persistenceManager.getTransactionManager();
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        flowPairRepository = repositoryFactory.createFlowPairRepository();
        flowRepository = repositoryFactory.createFlowRepository();
        flowPathRepository = repositoryFactory.createFlowPathRepository();
        transitVlanRepository = repositoryFactory.createTransitVlanRepository();
    }

    public boolean doesFlowExist(String flowId) {
        return flowPairRepository.exists(flowId);
    }

    public Optional<FlowPair> getFlowPair(String flowId) {
        return flowPairRepository.findById(flowId);
    }

    public Collection<FlowPair> getFlows() {
        return flowPairRepository.findAll();
    }

    protected Optional<FlowPathsWithEncapsulation> getFlowPathPairWithEncapsulation(String flowId) {
        return flowPairRepository.findById(flowId)
                .map(flowPair -> {
                    Flow flow = flowPair.getFlowEntity();
                    return FlowPathsWithEncapsulation.builder()
                            .flow(flow)
                            .forwardPath(flow.getForwardPath())
                            .reversePath(flow.getReversePath())
                            //TODO: hard-coded encapsulation will be removed in Flow H&S
                            .forwardEncapsulation(TransitVlanEncapsulation.builder()
                                    .transitVlan(flowPair.getForwardTransitVlanEntity())
                                .build())
                            .reverseEncapsulation(TransitVlanEncapsulation.builder()
                                    .transitVlan(flowPair.getReverseTransitVlanEntity())
                                    .build())
                            .protectedForwardPath(flow.getProtectedForwardPath())
                            .protectedReversePath(flow.getProtectedReversePath())
                            .protectedForwardEncapsulation(TransitVlanEncapsulation.builder()
                                    .transitVlan(findTransitVlan(flow.getProtectedForwardPathId()))
                                    .build())
                            .protectedReverseEncapsulation(TransitVlanEncapsulation.builder()
                                    .transitVlan(findTransitVlan(flow.getProtectedReversePathId()))
                                    .build())
                            .build();
                });
    }

    protected TransitVlan findTransitVlan(PathId pathId) {
        return transitVlanRepository.findByPathId(pathId).stream()
                .findAny().orElse(null);
    }

    /**
     * Updates the status of a flow(s).
     *
     * @param flowId a flow ID used to locate the flow(s).
     * @param status the status to set.
     */
    public void updateFlowStatus(String flowId, FlowStatus status, Set<PathId> pathIdSet) {
        transactionManager.doInTransaction(() -> {
            flowRepository.findById(flowId)
                    .ifPresent(flow -> {
                        flow.setStatus(status);
                        flowRepository.createOrUpdate(flow);
                    });

            Stream<FlowPath> pathsStream = flowPathRepository.findByFlowId(flowId).stream();
            if (!pathIdSet.isEmpty()) {
                pathsStream = pathsStream.filter(path -> pathIdSet.contains(path.getPathId()));
            }
            pathsStream.forEach(path -> {
                path.setStatusLikeFlow(status);
                flowPathRepository.createOrUpdate(path);
            });
        });
    }
}
