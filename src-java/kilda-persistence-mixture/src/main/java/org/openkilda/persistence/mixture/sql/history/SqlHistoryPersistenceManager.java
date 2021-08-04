/* Copyright 2021 Telstra Open Source
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

package org.openkilda.persistence.mixture.sql.history;

import org.openkilda.config.provider.ConfigurationProvider;
import org.openkilda.persistence.NetworkConfig;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.context.PersistenceContextManager;
import org.openkilda.persistence.hibernate.HibernatePersistenceManager;
import org.openkilda.persistence.orientdb.OrientDbPersistenceManager;
import org.openkilda.persistence.orientdb.ThreadLocalPersistenceContextManagerSupplier;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.spi.PersistenceManagerSupplier;
import org.openkilda.persistence.tx.TransactionArea;
import org.openkilda.persistence.tx.TransactionLayout;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.persistence.tx.TransactionManagerFactory;
import org.openkilda.persistence.tx.TransactionManagerFactoryImpl;

public class SqlHistoryPersistenceManager implements PersistenceManager {
    protected final NetworkConfig networkConfig;

    private final ThreadLocalPersistenceContextManagerSupplier contextManagerSupplier;

    private final OrientDbPersistenceManager orientImplementation;
    private final HibernatePersistenceManager hibernateImplementation;

    private final TransactionManagerFactory transactionManagerFactory;

    public SqlHistoryPersistenceManager(ConfigurationProvider configurationProvider) {
        networkConfig = configurationProvider.getConfiguration(NetworkConfig.class);

        // break down dependency/init loop
        PersistenceManagerSupplier managerSupplier = new PersistenceManagerSupplier();
        contextManagerSupplier = new ThreadLocalPersistenceContextManagerSupplier(managerSupplier);

        SeparateHistoryTransactionLayout layout = new SeparateHistoryTransactionLayout();
        orientImplementation = new OrientDbPersistenceManager(configurationProvider, contextManagerSupplier, layout);
        hibernateImplementation = new HibernatePersistenceManager(configurationProvider, layout);

        transactionManagerFactory = new TransactionManagerFactoryImpl(this);

        managerSupplier.define(this);
    }

    @Override
    public TransactionManager getTransactionManager() {
        return getTransactionManager(TransactionArea.COMMON);
    }

    @Override
    public TransactionManager getTransactionManager(TransactionArea area) {
        if (area == TransactionArea.HISTORY) {
            return hibernateImplementation.getTransactionManager(area);
        }
        return orientImplementation.getTransactionManager(area);
    }

    @Override
    public RepositoryFactory getRepositoryFactory() {
        return new SqlHistoryRepositoryFactory(
                orientImplementation.getGraphFactory(), networkConfig, transactionManagerFactory,
                hibernateImplementation.getSessionFactorySupplier());
    }

    @Override
    public PersistenceContextManager getPersistenceContextManager() {
        return contextManagerSupplier.get();
    }

    private static class SeparateHistoryTransactionLayout implements TransactionLayout {
        @Override
        public TransactionArea evaluateEffectiveArea(TransactionArea area) {
            if (area == TransactionArea.HISTORY) {
                return TransactionArea.HISTORY;
            }
            return TransactionArea.COMMON;
        }
    }
}
