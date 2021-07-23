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
import org.openkilda.persistence.PersistenceImplementationType;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.context.PersistenceContext;
import org.openkilda.persistence.ferma.FermaPersistentImplementation;
import org.openkilda.persistence.inmemory.repositories.InMemoryRepositoryFactory;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.tx.ImplementationTransactionAdapter;

import lombok.extern.slf4j.Slf4j;

/**
 * In-memory implementation of {@link PersistenceManager}.
 * Built on top of Tinkerpop / Ferma implementation.
 */
@Slf4j
public class InMemoryGraphPersistenceImplementation implements FermaPersistentImplementation {
    private final NetworkConfig networkConfig;

    private static final InMemoryFramedGraphFactory graphFactory = new InMemoryFramedGraphFactory();

    public InMemoryGraphPersistenceImplementation(ConfigurationProvider configurationProvider) {
        networkConfig = configurationProvider.getConfiguration(NetworkConfig.class);
    }

    @Override
    public RepositoryFactory getRepositoryFactory() {
        return new InMemoryRepositoryFactory(this, networkConfig);
    }

    @Override
    public ImplementationTransactionAdapter<?> newTransactionAdapter() {
        return new InMemoryTransactionAdapter(this);
    }

    @Override
    public InMemoryGraphContextExtension getContextExtension(PersistenceContext context) {
        return context.getExtensionCreateIfMissing(
                InMemoryGraphContextExtension.class, () -> new InMemoryGraphContextExtension(
                        PersistenceImplementationType.IN_MEMORY_GRAPH, graphFactory));
    }

    @Override
    public void onContextClose(PersistenceContext context) {
        // nothing to do here
    }

    @Override
    public PersistenceImplementationType getType() {
        return PersistenceImplementationType.IN_MEMORY_GRAPH;
    }

    /**
     * Purge in-memory graph data.
     */
    public void purgeData() {
        graphFactory.purge();
    }
}
