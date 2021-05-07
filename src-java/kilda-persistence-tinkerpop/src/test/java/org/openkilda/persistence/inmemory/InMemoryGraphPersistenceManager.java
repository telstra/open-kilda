/* Copyright 2020 Telstra Open Source
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

package org.openkilda.persistence.inmemory;

import org.openkilda.config.provider.ConfigurationProvider;
import org.openkilda.persistence.NetworkConfig;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.PersistenceManagerBase;
import org.openkilda.persistence.context.PersistenceContextManager;
import org.openkilda.persistence.inmemory.repositories.InMemoryRepositoryFactory;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.tx.FlatTransactionLayout;
import org.openkilda.persistence.tx.TransactionArea;
import org.openkilda.persistence.tx.TransactionManager;

import lombok.extern.slf4j.Slf4j;

/**
 * In-memory implementation of {@link PersistenceManager}.
 * Built on top of Tinkerpop / Ferma implementation.
 */
@Slf4j
public class InMemoryGraphPersistenceManager extends PersistenceManagerBase {
    private static final InMemoryFramedGraphFactory GRAPH_SUPPLIER = new InMemoryFramedGraphFactory();

    private final NetworkConfig networkConfig;

    public InMemoryGraphPersistenceManager(ConfigurationProvider configurationProvider) {
        super(configurationProvider);
        networkConfig = configurationProvider.getConfiguration(NetworkConfig.class);
    }

    @Override
    public TransactionManager getTransactionManager() {
        return getTransactionManager(TransactionArea.FLAT);
    }

    @Override
    public RepositoryFactory getRepositoryFactory() {
        return new InMemoryRepositoryFactory(GRAPH_SUPPLIER, getTransactionManager(), networkConfig);
    }

    @Override
    public PersistenceContextManager getPersistenceContextManager() {
        return new InMemoryPersistenceContextManager();
    }

    /**
     * Purge in-memory graph data.
     */
    public void purgeData() {
        GRAPH_SUPPLIER.purge();
    }

    @Override
    protected TransactionManager makeTransactionManager(TransactionArea area) {
        return new TransactionManager(new FlatTransactionLayout(), new InMemoryTransactionAdapterFactory(area), 0, 0);
    }

    /**
     * In-memory implementation of {@link PersistenceContextManager}. It ignores context events.
     */
    public static class InMemoryPersistenceContextManager implements PersistenceContextManager {
        @Override
        public void initContext() {
        }

        @Override
        public boolean isContextInitialized() {
            return true;
        }

        @Override
        public void closeContext() {
        }

        @Override
        public boolean isTxOpen() {
            return InMemoryTransactionAdapter.isFakedTxOpen();
        }
    }
}
