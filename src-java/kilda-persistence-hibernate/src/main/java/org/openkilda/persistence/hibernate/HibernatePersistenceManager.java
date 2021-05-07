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

package org.openkilda.persistence.hibernate;

import org.openkilda.config.provider.ConfigurationProvider;
import org.openkilda.persistence.PersistenceConfig;
import org.openkilda.persistence.PersistenceManagerBase;
import org.openkilda.persistence.context.PersistenceContextManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.tx.FlatTransactionLayout;
import org.openkilda.persistence.tx.TransactionArea;
import org.openkilda.persistence.tx.TransactionLayout;
import org.openkilda.persistence.tx.TransactionManager;
import org.openkilda.persistence.tx.TransactionManagerFactory;
import org.openkilda.persistence.tx.TransactionManagerFactoryImpl;

import lombok.Getter;

public class HibernatePersistenceManager extends PersistenceManagerBase {
    private final PersistenceConfig persistenceConfig;
    private final TransactionLayout transactionLayout;

    @Getter
    private final HibernateSessionFactorySupplier sessionFactorySupplier;

    private final TransactionManagerFactory transactionManagerFactory;

    public HibernatePersistenceManager(ConfigurationProvider configurationProvider) {
        this(configurationProvider, new FlatTransactionLayout());
    }

    public HibernatePersistenceManager(
            ConfigurationProvider configurationProvider, TransactionLayout transactionLayout) {
        super(configurationProvider);

        persistenceConfig = configurationProvider.getConfiguration(PersistenceConfig.class);
        this.transactionLayout = transactionLayout;

        transactionManagerFactory = new TransactionManagerFactoryImpl(this);
        sessionFactorySupplier = new HibernateSessionFactorySupplier(
                configurationProvider.getConfiguration(HibernateConfig.class));
    }

    @Override
    public TransactionManager getTransactionManager() {
        return getTransactionManager(TransactionArea.FLAT);
    }

    @Override
    public RepositoryFactory getRepositoryFactory() {
        return new HibernateRepositoryFactory(sessionFactorySupplier, transactionManagerFactory);
    }

    @Override
    public PersistenceContextManager getPersistenceContextManager() {
        throw new IllegalStateException(String.format(
                "%s do not provide substantive persistence layer, it must be used only as part of persistence "
                        + "layers mixture.", getClass().getName()));
    }

    @Override
    protected TransactionManager makeTransactionManager(TransactionArea area) {
        HibernateTransactionAdapterFactory adapterFactory = new HibernateTransactionAdapterFactory(
                area, sessionFactorySupplier);
        return new TransactionManager(
                transactionLayout, adapterFactory,
                persistenceConfig.getTransactionRetriesLimit(), persistenceConfig.getTransactionRetriesMaxDelay());
    }
}
