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
import org.openkilda.persistence.PersistenceImplementation;
import org.openkilda.persistence.PersistenceImplementationType;
import org.openkilda.persistence.context.PersistenceContext;
import org.openkilda.persistence.repositories.RepositoryFactory;

import lombok.Getter;

public class HibernatePersistenceImplementation implements PersistenceImplementation {
    @Getter
    private final PersistenceImplementationType type;

    @Getter
    private final HibernateSessionFactorySupplier sessionFactorySupplier;

    public HibernatePersistenceImplementation(
            ConfigurationProvider configurationProvider, PersistenceImplementationType type) {
        this.type = type;

        sessionFactorySupplier = new HibernateSessionFactorySupplier(
                configurationProvider.getConfiguration(HibernateConfig.class));
    }

    @Override
    public RepositoryFactory getRepositoryFactory() {
        return new HibernateRepositoryFactory(this);
    }

    @Override
    public HibernateTransactionAdapter newTransactionAdapter() {
        return new HibernateTransactionAdapter(this);
    }

    @Override
    public HibernateContextExtension getContextExtension(PersistenceContext context) {
        return context.getExtensionCreateIfMissing(
                HibernateContextExtension.class, () -> new HibernateContextExtension(type, sessionFactorySupplier));
    }

    @Override
    public void onContextClose(PersistenceContext context) {
        // nothing to do here
    }
}
