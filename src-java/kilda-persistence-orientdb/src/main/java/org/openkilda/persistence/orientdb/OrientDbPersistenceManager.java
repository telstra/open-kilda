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

package org.openkilda.persistence.orientdb;

import org.openkilda.config.provider.ConfigurationProvider;
import org.openkilda.persistence.NetworkConfig;
import org.openkilda.persistence.PersistenceConfig;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.PersistenceManagerBase;
import org.openkilda.persistence.context.PersistenceContextManager;
import org.openkilda.persistence.orientdb.repositories.OrientDbRepositoryFactory;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.spi.PersistenceManagerSupplier;
import org.openkilda.persistence.tx.FlatTransactionLayout;
import org.openkilda.persistence.tx.TransactionArea;
import org.openkilda.persistence.tx.TransactionLayout;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.persistence.tx.TransactionManagerFactoryImpl;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * OrientDB implementation of {@link PersistenceManager}.
 */
@Slf4j
public class OrientDbPersistenceManager extends PersistenceManagerBase {
    protected final PersistenceConfig persistenceConfig;
    protected final NetworkConfig networkConfig;

    private final TransactionLayout transactionLayout;
    private final ThreadLocalPersistenceContextManagerSupplier contextManagerSupplier;

    @Getter
    private final OrientDbGraphFactory graphFactory;

    public OrientDbPersistenceManager(ConfigurationProvider configurationProvider) {
        this(configurationProvider, null, new FlatTransactionLayout());
    }

    public OrientDbPersistenceManager(
            ConfigurationProvider configurationProvider,
            ThreadLocalPersistenceContextManagerSupplier contextManagerSupplier, TransactionLayout transactionLayout) {
        super(configurationProvider);

        persistenceConfig = configurationProvider.getConfiguration(PersistenceConfig.class);
        networkConfig = configurationProvider.getConfiguration(NetworkConfig.class);

        this.transactionLayout = transactionLayout;

        // break dependency / init loop
        PersistenceManagerSupplier managerSupplier = null;
        if (contextManagerSupplier == null) {
            managerSupplier = new PersistenceManagerSupplier();
            contextManagerSupplier = new ThreadLocalPersistenceContextManagerSupplier(managerSupplier);
        }
        this.contextManagerSupplier = contextManagerSupplier;

        graphFactory = new OrientDbGraphFactory(
                this.contextManagerSupplier, configurationProvider.getConfiguration(OrientDbConfig.class));

        if (managerSupplier != null) {
            managerSupplier.define(this);
        }
    }

    @Override
    public TransactionManager getTransactionManager() {
        return getTransactionManager(TransactionArea.FLAT);
    }

    @Override
    public RepositoryFactory getRepositoryFactory() {
        TransactionManagerFactoryImpl transactionManagerFactory = new TransactionManagerFactoryImpl(this);
        return new OrientDbRepositoryFactory(graphFactory, transactionManagerFactory, networkConfig);
    }

    @Override
    public PersistenceContextManager getPersistenceContextManager() {
        return contextManagerSupplier.get();
    }

    @Override
    protected TransactionManager makeTransactionManager(TransactionArea area) {
        OrientDbTransactionAdapterFactory adapterFactory = new OrientDbTransactionAdapterFactory(area, graphFactory);
        return new TransactionManager(
                transactionLayout, adapterFactory,
                persistenceConfig.getTransactionRetriesLimit(), persistenceConfig.getTransactionRetriesMaxDelay());
    }
}
