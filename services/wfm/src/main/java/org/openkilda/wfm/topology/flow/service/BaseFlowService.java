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
import org.openkilda.model.FlowStatus;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowPairRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.share.flow.resources.transitvlan.TransitVlanEncapsulation;
import org.openkilda.wfm.topology.flow.model.FlowPathPairWithEncapsulation;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Optional;

@Slf4j
public class BaseFlowService {
    protected TransactionManager transactionManager;
    private FlowPairRepository flowPairRepository;

    public BaseFlowService(PersistenceManager persistenceManager) {
        transactionManager = persistenceManager.getTransactionManager();
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        flowPairRepository = repositoryFactory.createFlowPairRepository();
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

    protected Optional<FlowPathPairWithEncapsulation> getFlowPathPairWithEncapsulation(String flowId) {
        return flowPairRepository.findById(flowId)
                .map(flowPair -> FlowPathPairWithEncapsulation.builder()
                        .flow(flowPair.getFlowEntity())
                        .forwardPath(flowPair.getForward().getFlowPath())
                        .reversePath(flowPair.getReverse().getFlowPath())
                        //TODO: hard-coded encapsulation will be removed in Flow H&S
                        .forwardEncapsulation(TransitVlanEncapsulation.builder()
                                .transitVlan(flowPair.getForwardTransitVlanEntity())
                                .build())
                        .reverseEncapsulation(TransitVlanEncapsulation.builder()
                                .transitVlan(flowPair.getReverseTransitVlanEntity())
                                .build())
                        .build());
    }

    /**
     * Updates the status of a flow(s).
     *
     * @param flowId a flow ID used to locate the flow(s).
     * @param status the status to set.
     */
    public void updateFlowStatus(String flowId, FlowStatus status) {
        transactionManager.doInTransaction(() ->
                flowPairRepository.findById(flowId)
                        .ifPresent(flow -> {
                            flow.setStatus(status);
                            flowPairRepository.createOrUpdate(flow);
                        }));
    }
}
