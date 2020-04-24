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

package org.openkilda.wfm.share.flow.resources.vxlan;

import org.openkilda.model.Flow;
import org.openkilda.model.PathId;
import org.openkilda.model.Vxlan;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.VxlanRepository;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.persistence.tx.TransactionRequired;
import org.openkilda.wfm.share.flow.resources.EncapsulationResourcesProvider;
import org.openkilda.wfm.share.flow.resources.ResourceNotAvailableException;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Optional;
import java.util.Random;

/**
 * The resource pool is responsible for vxlan de-/allocation.
 */
@Slf4j
public class VxlanPool implements EncapsulationResourcesProvider<VxlanEncapsulation> {
    private final TransactionManager transactionManager;
    private final VxlanRepository vxlanRepository;

    private final int minVxlan;
    private final int maxVxlan;
    private final int poolSize;

    private int nextVxlan = 0;

    public VxlanPool(PersistenceManager persistenceManager, int minVxlan, int maxVxlan, int poolSize) {
        transactionManager = persistenceManager.getTransactionManager();
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        vxlanRepository = repositoryFactory.createVxlanRepository();

        this.minVxlan = minVxlan;
        this.maxVxlan = maxVxlan;
        this.poolSize = poolSize;
    }

    /**
     * Allocates a vxlan for the flow path.
     */
    @Override
    public VxlanEncapsulation allocate(Flow flow, PathId pathId, PathId oppositePathId) {
        return get(oppositePathId, null)
                .orElseGet(() -> allocate(flow, pathId));
    }

    @TransactionRequired
    private VxlanEncapsulation allocate(Flow flow, PathId pathId) {
        if (nextVxlan > 0) {
            if (nextVxlan <= maxVxlan && !vxlanRepository.exists(nextVxlan)) {
                return addVxlan(flow, pathId, nextVxlan++);
            } else {
                nextVxlan = 0;
            }
        }
        // The pool requires (re-)initialization.
        if (nextVxlan == 0) {
            long numOfPools = (maxVxlan - minVxlan) / poolSize;
            if (numOfPools > 1) {
                long poolToTake = Math.abs(new Random().nextInt()) % numOfPools;
                Optional<Integer> availableVxlan = vxlanRepository.findFirstUnassignedVxlan(
                        minVxlan + (int) poolToTake * poolSize,
                        minVxlan + (int) (poolToTake + 1) * poolSize - 1);
                if (availableVxlan.isPresent()) {
                    nextVxlan = availableVxlan.get();
                    return addVxlan(flow, pathId, nextVxlan++);
                }
            }
            // The pool requires full scan.
            nextVxlan = -1;
        }
        if (nextVxlan == -1) {
            Optional<Integer> availableVxlan = vxlanRepository.findFirstUnassignedVxlan(minVxlan,
                    maxVxlan);
            if (availableVxlan.isPresent()) {
                nextVxlan = availableVxlan.get();
                return addVxlan(flow, pathId, nextVxlan++);
            }
        }
        throw new ResourceNotAvailableException("No vxlan available");
    }

    private VxlanEncapsulation addVxlan(Flow flow, PathId pathId, int vxlan) {
        Vxlan vxlanEntity = Vxlan.builder()
                .vni(vxlan)
                .flowId(flow.getFlowId())
                .pathId(pathId)
                .build();
        vxlanRepository.add(vxlanEntity);

        return VxlanEncapsulation.builder()
                .vxlan(new Vxlan(vxlanEntity))
                .build();
    }

    /**
     * Deallocates a vxlan of the path.
     */
    @Override
    public void deallocate(PathId pathId) {
        transactionManager.doInTransaction(() ->
                vxlanRepository.findByPathId(pathId, null)
                        .forEach(vxlanRepository::remove));
    }

    /**
     * Get allocated vxlan(s) of the flow path.
     */
    @Override
    public Optional<VxlanEncapsulation> get(PathId pathId, PathId oppositePathId) {
        Collection<Vxlan> vxlans = vxlanRepository.findByPathId(pathId, oppositePathId);
        return vxlans.stream()
                .findAny()
                .map(vxlan -> VxlanEncapsulation.builder().vxlan(new Vxlan(vxlan)).build());
    }
}
