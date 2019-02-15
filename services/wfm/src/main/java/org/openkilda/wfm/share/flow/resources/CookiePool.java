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

package org.openkilda.wfm.share.flow.resources;

import org.openkilda.model.FlowCookie;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowCookieRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;

import lombok.extern.slf4j.Slf4j;

/**
 * The resource pool is responsible for cookie de-/allocation.
 */
@Slf4j
public class CookiePool {
    private final TransactionManager transactionManager;
    private final FlowCookieRepository flowCookieRepository;

    private final long minCookie;
    private final long maxCookie;

    public CookiePool(PersistenceManager persistenceManager, long minCookie, long maxCookie) {
        transactionManager = persistenceManager.getTransactionManager();
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        flowCookieRepository = repositoryFactory.createFlowCookieRepository();

        this.minCookie = minCookie;
        this.maxCookie = maxCookie;
    }

    /**
     * Allocates cookie.
     *
     * @return unmasked allocated cookie.
     */
    public long allocateCookie(String flowId, PathId forwardPathId, PathId reversePathId) {
        return transactionManager.doInTransaction(() -> {
            long availableCookie = flowCookieRepository.findAvailableUnmaskedCookie().orElse(minCookie);
            if (availableCookie > maxCookie) {
                throw new ResourceNotAvailableException("No cookie available");
            }

            FlowCookie forwardFlowCookie = FlowCookie.builder()
                    .unmaskedCookie(availableCookie)
                    .flowId(flowId)
                    .pathId(forwardPathId)
                    .build();
            flowCookieRepository.createOrUpdate(forwardFlowCookie);

            FlowCookie reverseFlowCookie = FlowCookie.builder()
                    .unmaskedCookie(availableCookie)
                    .flowId(flowId)
                    .pathId(reversePathId)
                    .build();
            flowCookieRepository.createOrUpdate(reverseFlowCookie);

            return availableCookie;
        });
    }

    /**
     * Deallocates cookie.
     */
    public void deallocateCookie(PathId forwardPathId, PathId reversePathId) {
        transactionManager.doInTransaction(() -> {
            flowCookieRepository.findByPathId(forwardPathId)
                    .ifPresent(flowCookieRepository::delete);
            flowCookieRepository.findByPathId(reversePathId)
                    .ifPresent(flowCookieRepository::delete);
        });
    }
}
