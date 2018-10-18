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

import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.impl.Neo4jRepositoryFactory;

import org.neo4j.ogm.config.Configuration.Builder;
import org.neo4j.ogm.session.SessionFactory;

/**
 * Neo4J OGM implementation of {@link PersistenceManager}.
 */
public class Neo4jPersistenceManager implements PersistenceManager {
    private final Neo4jConfig config;

    private transient volatile Neo4jTransactionManager neo4jTransactionManager;

    public Neo4jPersistenceManager(Neo4jConfig config) {
        this.config = config;
    }

    @Override
    public TransactionManager getTransactionManager() {
        return getNeo4jTransactionManager();
    }

    @Override
    public RepositoryFactory getRepositoryFactory() {
        return new Neo4jRepositoryFactory(getNeo4jTransactionManager(), getTransactionManager());
    }

    private Neo4jTransactionManager getNeo4jTransactionManager() {
        if (neo4jTransactionManager == null) {
            synchronized (this) {
                if (neo4jTransactionManager == null) {
                    Builder configBuilder = new Builder()
                            .uri(config.getUri())
                            .credentials(config.getLogin(), config.getPassword());
                    if (config.getConnectionPoolSize() > 0) {
                        configBuilder.connectionPoolSize(config.getConnectionPoolSize());
                    }

                    if (config.getIndexesAuto() != null) {
                        configBuilder.autoIndex(config.getIndexesAuto());
                    }

                    SessionFactory sessionFactory =
                            new SessionFactory(configBuilder.build(), "org.openkilda.model");
                    sessionFactory.metaData().registerConversionCallback(
                            new SimpleConversionCallback("org.openkilda.persistence.converters"));

                    neo4jTransactionManager = new Neo4jTransactionManager(sessionFactory);
                }
            }
        }

        return neo4jTransactionManager;
    }
}
