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

import org.openkilda.model.FlowPair;
import org.openkilda.model.PathId;
import org.openkilda.model.TransitVlan;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowPairRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.TransitVlanRepository;
import org.openkilda.wfm.share.flow.resources.transitvlan.TransitVlanEncapsulation;
import org.openkilda.wfm.topology.flow.model.FlowPathsWithEncapsulation;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class BaseFlowService {
    protected final TransactionManager transactionManager;
    protected final FlowRepository flowRepository;
    protected final FlowPairRepository flowPairRepository;
    private final TransitVlanRepository transitVlanRepository;

    public BaseFlowService(PersistenceManager persistenceManager) {
        transactionManager = persistenceManager.getTransactionManager();
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        flowRepository = repositoryFactory.createFlowRepository();
        flowPairRepository = repositoryFactory.createFlowPairRepository();
        transitVlanRepository = repositoryFactory.createTransitVlanRepository();
    }

    public boolean doesFlowExist(String flowId) {
        return flowRepository.exists(flowId);
    }

    public Optional<FlowPair> getFlowPair(String flowId) {
        return flowPairRepository.findById(flowId);
    }

    /**
     * Fetches all flow pairs.
     * <p/>
     * IMPORTANT: the method doesn't complete with flow paths and transit vlans!
     */
    public Collection<FlowPair> getFlows() {
        return flowRepository.findAll().stream()
                .map(flow -> new FlowPair(flow, null, null))
                .collect(Collectors.toList());
    }

    protected TransitVlan findTransitVlan(PathId pathId) {
        return transitVlanRepository.findByPathId(pathId).stream()
                .findAny().orElse(null);
    }

    protected Optional<FlowPathsWithEncapsulation> getFlowPathPairWithEncapsulation(String flowId) {
        return flowRepository.findById(flowId)
                .map(flow ->
                    FlowPathsWithEncapsulation.builder()
                            .flow(flow)
                            .forwardPath(flow.getForwardPath())
                            .reversePath(flow.getReversePath())
                            //TODO: hard-coded encapsulation will be removed in Flow H&S
                            .forwardEncapsulation(TransitVlanEncapsulation.builder()
                                    .transitVlan(findTransitVlan(flow.getForwardPathId()))
                                    .build())
                            .reverseEncapsulation(TransitVlanEncapsulation.builder()
                                    .transitVlan(findTransitVlan(flow.getReversePathId()))
                                    .build())
                            .protectedForwardPath(flow.getProtectedForwardPath())
                            .protectedReversePath(flow.getProtectedReversePath())
                            .protectedForwardEncapsulation(TransitVlanEncapsulation.builder()
                                    .transitVlan(findTransitVlan(flow.getProtectedForwardPathId()))
                                    .build())
                            .protectedReverseEncapsulation(TransitVlanEncapsulation.builder()
                                    .transitVlan(findTransitVlan(flow.getProtectedReversePathId()))
                                    .build())
                            .build()
                );
    }
}
