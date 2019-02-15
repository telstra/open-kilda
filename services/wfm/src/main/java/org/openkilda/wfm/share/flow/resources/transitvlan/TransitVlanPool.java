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

package org.openkilda.wfm.share.flow.resources.transitvlan;

import org.openkilda.model.Flow;
import org.openkilda.model.PathId;
import org.openkilda.model.TransitVlan;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.TransitVlanRepository;
import org.openkilda.wfm.share.flow.resources.EncapsulationResourcesProvider;
import org.openkilda.wfm.share.flow.resources.ResourceNotAvailableException;

import lombok.extern.slf4j.Slf4j;

/**
 * The resource pool is responsible for transit vlan de-/allocation.
 */
@Slf4j
public class TransitVlanPool implements EncapsulationResourcesProvider<TransitVlanResources> {
    private final TransactionManager transactionManager;
    private final TransitVlanRepository transitVlanRepository;

    private final int minTransitVlan;
    private final int maxTransitVlan;

    public TransitVlanPool(PersistenceManager persistenceManager, int minTransitVlan, int maxTransitVlan) {
        transactionManager = persistenceManager.getTransactionManager();
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        transitVlanRepository = repositoryFactory.createTransitVlanRepository();

        this.minTransitVlan = minTransitVlan;
        this.maxTransitVlan = maxTransitVlan;
    }

    /**
     * Allocates vlan.
     */
    @Override
    public TransitVlanResources allocate(Flow flow, PathId forwardPathId, PathId reversePathId) {
        return transactionManager.doInTransaction(() ->
                TransitVlanResources.builder()
                        .forwardTransitVlan(claimTransitVlan(flow.getFlowId(), forwardPathId))
                        .reverseTransitVlan(claimTransitVlan(flow.getFlowId(), reversePathId))
                        .build());
    }

    private TransitVlan claimTransitVlan(String flowId, PathId pathId) {
        int availableVlan = transitVlanRepository.findAvailableVlan().orElse(minTransitVlan);
        if (availableVlan >= maxTransitVlan) {
            throw new ResourceNotAvailableException("No vlan available");
        }

        TransitVlan transitVlan = TransitVlan.builder()
                .vlan(availableVlan)
                .flowId(flowId)
                .pathId(pathId)
                .build();
        transitVlanRepository.createOrUpdate(transitVlan);

        return transitVlan;
    }

    /**
     * Deallocates vlan.
     */
    @Override
    public void deallocate(PathId forwardPathId, PathId reversePathId) {
        transactionManager.doInTransaction(() -> {
            transitVlanRepository.findByPathId(forwardPathId)
                    .ifPresent(transitVlanRepository::delete);
            transitVlanRepository.findByPathId(reversePathId)
                    .ifPresent(transitVlanRepository::delete);
        });
    }
}
