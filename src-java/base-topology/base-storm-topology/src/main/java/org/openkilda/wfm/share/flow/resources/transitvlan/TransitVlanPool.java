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

import org.openkilda.model.PathId;
import org.openkilda.model.TransitVlan;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.TransitVlanRepository;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.persistence.tx.TransactionRequired;
import org.openkilda.wfm.share.flow.resources.EncapsulationResourcesProvider;
import org.openkilda.wfm.share.flow.resources.ResourceNotAvailableException;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Optional;
import java.util.Random;

/**
 * The resource pool is responsible for transit vlan de-/allocation.
 */
@Slf4j
public class TransitVlanPool implements EncapsulationResourcesProvider<TransitVlanEncapsulation> {
    private final TransactionManager transactionManager;
    private final TransitVlanRepository transitVlanRepository;

    private final int minTransitVlan;
    private final int maxTransitVlan;
    private final int poolSize;

    private int nextVlan = 0;

    public TransitVlanPool(PersistenceManager persistenceManager, int minTransitVlan, int maxTransitVlan,
                           int poolSize) {
        transactionManager = persistenceManager.getTransactionManager();
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        transitVlanRepository = repositoryFactory.createTransitVlanRepository();

        this.minTransitVlan = minTransitVlan;
        this.maxTransitVlan = maxTransitVlan;
        this.poolSize = poolSize;
    }

    /**
     * Allocates a vlan for the flow path.
     */
    @Override
    public TransitVlanEncapsulation allocate(String flowId, PathId pathId, PathId oppositePathId) {
        return get(oppositePathId, null)
                .orElseGet(() -> allocate(flowId, pathId));
    }

    @TransactionRequired
    private TransitVlanEncapsulation allocate(String flowId, PathId pathId) {
        if (nextVlan > 0) {
            if (nextVlan <= maxTransitVlan && !transitVlanRepository.exists(nextVlan)) {
                return addVlan(flowId, pathId, nextVlan++);
            } else {
                nextVlan = 0;
            }
        }
        // The pool requires (re-)initialization.
        if (nextVlan == 0) {
            long numOfPools = (maxTransitVlan - minTransitVlan) / poolSize;
            if (numOfPools > 1) {
                long poolToTake = Math.abs(new Random().nextInt()) % numOfPools;
                Optional<Integer> availableVlan = transitVlanRepository.findFirstUnassignedVlan(
                        minTransitVlan + (int) poolToTake * poolSize,
                        minTransitVlan + (int) (poolToTake + 1) * poolSize - 1);
                if (availableVlan.isPresent()) {
                    nextVlan = availableVlan.get();
                    return addVlan(flowId, pathId, nextVlan++);
                }
            }
            // The pool requires full scan.
            nextVlan = -1;
        }
        if (nextVlan == -1) {
            Optional<Integer> availableVlan = transitVlanRepository.findFirstUnassignedVlan(minTransitVlan,
                    minTransitVlan);
            if (availableVlan.isPresent()) {
                nextVlan = availableVlan.get();
                return addVlan(flowId, pathId, nextVlan++);
            }
        }
        throw new ResourceNotAvailableException("No vlan available");
    }

    private TransitVlanEncapsulation addVlan(String flowId, PathId pathId, int vlan) {
        TransitVlan transitVlanEntity = TransitVlan.builder()
                .vlan(vlan)
                .flowId(flowId)
                .pathId(pathId)
                .build();
        transitVlanRepository.add(transitVlanEntity);

        return TransitVlanEncapsulation.builder()
                .transitVlan(new TransitVlan(transitVlanEntity))
                .build();
    }

    /**
     * Deallocates a vlan of the path.
     */
    @Override
    public void deallocate(PathId pathId) {
        transactionManager.doInTransaction(() ->
                transitVlanRepository.findByPathId(pathId, null)
                        .forEach(transitVlanRepository::remove));
    }

    /**
     * Get allocated transit vlan(s) of the flow path.
     */
    @Override
    public Optional<TransitVlanEncapsulation> get(PathId pathId, PathId oppositePathId) {
        Collection<TransitVlan> transitVlans = transitVlanRepository.findByPathId(pathId, oppositePathId);
        return transitVlans.stream()
                .findAny()
                .map(transitVlan -> TransitVlanEncapsulation.builder()
                        .transitVlan(new TransitVlan(transitVlan))
                        .build());
    }
}
