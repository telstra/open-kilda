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
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowCookieRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.persistence.tx.TransactionRequired;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.Random;

/**
 * The resource pool is responsible for cookie de-/allocation.
 */
@Slf4j
public class CookiePool {
    private final TransactionManager transactionManager;
    private final FlowCookieRepository flowCookieRepository;

    private final long minCookie;
    private final long maxCookie;
    private final int poolSize;

    private long nextCookie = 0;

    public CookiePool(PersistenceManager persistenceManager, long minCookie, long maxCookie, int poolSize) {
        transactionManager = persistenceManager.getTransactionManager();
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        flowCookieRepository = repositoryFactory.createFlowCookieRepository();

        this.minCookie = minCookie;
        this.maxCookie = maxCookie;
        this.poolSize = poolSize;
    }

    /**
     * Allocates a cookie for the flow.
     *
     * @return unmasked allocated cookie.
     */
    @TransactionRequired
    public long allocate(String flowId) {
        if (nextCookie > 0) {
            if (nextCookie <= maxCookie && !flowCookieRepository.exists(nextCookie)) {
                addCookie(flowId, nextCookie);
                return nextCookie++;
            } else {
                nextCookie = 0;
            }
        }
        // The pool requires (re-)initialization.
        if (nextCookie == 0) {
            long numOfPools = (maxCookie - minCookie) / poolSize;
            if (numOfPools > 1) {
                long poolToTake = Math.abs(new Random().nextInt()) % numOfPools;
                Optional<Long> availableCookie = flowCookieRepository.findFirstUnassignedCookie(
                        minCookie + poolToTake * poolSize,
                        minCookie + (poolToTake + 1) * poolSize - 1);
                if (availableCookie.isPresent()) {
                    nextCookie = availableCookie.get();
                    addCookie(flowId, nextCookie);
                    return nextCookie++;
                }
            }
            // The pool requires full scan.
            nextCookie = -1;
        }
        if (nextCookie == -1) {
            Optional<Long> availableCookie = flowCookieRepository.findFirstUnassignedCookie(minCookie, maxCookie);
            if (availableCookie.isPresent()) {
                nextCookie = availableCookie.get();
                addCookie(flowId, nextCookie);
                return nextCookie++;
            }
        }
        throw new ResourceNotAvailableException("No cookie available");
    }

    private void addCookie(String flowId, long cookie) {
        FlowCookie flowCookie = FlowCookie.builder()
                .unmaskedCookie(cookie)
                .flowId(flowId)
                .build();
        flowCookieRepository.add(flowCookie);
    }

    /**
     * Deallocates a cookie.
     */
    public void deallocate(long unmaskedCookie) {
        transactionManager.doInTransaction(() ->
                flowCookieRepository.findByCookie(unmaskedCookie)
                        .ifPresent(flowCookieRepository::remove)
        );
    }
}
