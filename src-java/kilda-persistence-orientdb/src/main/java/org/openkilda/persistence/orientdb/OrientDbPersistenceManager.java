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

import org.openkilda.persistence.NetworkConfig;
import org.openkilda.persistence.PersistenceConfig;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.orientdb.repositories.OrientDbRepositoryFactory;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.tx.TransactionManager;

import lombok.extern.slf4j.Slf4j;

/**
 * OrientDB implementation of {@link PersistenceManager}.
 */
@Slf4j
public class OrientDbPersistenceManager implements PersistenceManager {
    private final PersistenceConfig persistenceConfig;
    private final OrientDbConfig config;
    private final NetworkConfig networkConfig;

    private transient volatile OrientDbGraphFactory graphFactory;

    public OrientDbPersistenceManager(PersistenceConfig persistenceConfig,
                                      OrientDbConfig config, NetworkConfig networkConfig) {
        this.persistenceConfig = persistenceConfig;
        this.config = config;
        this.networkConfig = networkConfig;
    }

    @Override
    public TransactionManager getTransactionManager() {
        return new OrientDbTransactionManager(getGraphFactory(), persistenceConfig.getTransactionRetriesLimit(),
                persistenceConfig.getTransactionRetriesMaxDelay());
    }

    @Override
    public RepositoryFactory getRepositoryFactory() {
        return new OrientDbRepositoryFactory(getGraphFactory(), getTransactionManager(), networkConfig);
    }

    private OrientDbGraphFactory getGraphFactory() {
        if (graphFactory == null) {
            synchronized (this) {
                if (graphFactory == null) {
                    log.debug("Creating an instance of OrientDbGraphFactory for {}", config);
                    graphFactory = new OrientDbGraphFactory(config);
                }
            }
        }
        return graphFactory;
    }
}
