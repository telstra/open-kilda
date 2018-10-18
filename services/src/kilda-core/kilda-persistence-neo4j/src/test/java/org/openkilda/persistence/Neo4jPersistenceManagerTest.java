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

package org.openkilda.persistence;

import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class Neo4jPersistenceManagerTest extends Neo4jBasedTest {
    @Test
    public void shouldShareTransactionsWithinTheSamePersistenceManager()
            throws ExecutionException, InterruptedException {
        // given
        TransactionManager txManager1 = CompletableFuture.supplyAsync(persistenceManager::getTransactionManager).get();
        TransactionManager txManager2 = CompletableFuture.supplyAsync(persistenceManager::getTransactionManager).get();

        // when
        txManager1.begin();
        txManager2.commit();

        txManager2.begin();
        txManager1.rollback();

        // then no tx issues
    }
}
