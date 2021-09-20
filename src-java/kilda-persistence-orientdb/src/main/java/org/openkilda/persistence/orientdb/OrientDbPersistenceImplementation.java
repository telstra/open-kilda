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

package org.openkilda.persistence.orientdb;

import org.openkilda.config.provider.ConfigurationProvider;
import org.openkilda.persistence.NetworkConfig;
import org.openkilda.persistence.PersistenceImplementationType;
import org.openkilda.persistence.context.PersistenceContext;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.ferma.FermaPersistentImplementation;
import org.openkilda.persistence.orientdb.repositories.OrientDbRepositoryFactory;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.tx.ImplementationTransactionAdapter;

import com.syncleus.ferma.DelegatingFramedGraph;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.tinkerpop.gremlin.orientdb.OrientGraph;

import java.io.IOException;

@Slf4j
public class OrientDbPersistenceImplementation implements FermaPersistentImplementation {
    protected final NetworkConfig networkConfig;

    @Getter
    private final PersistenceImplementationType type;

    @Getter
    private final OrientDbGraphFactory graphFactory;

    public OrientDbPersistenceImplementation(
            ConfigurationProvider configurationProvider, PersistenceImplementationType type) {
        networkConfig = configurationProvider.getConfiguration(NetworkConfig.class);
        this.type = type;

        graphFactory = new OrientDbGraphFactory(
                configurationProvider.getConfiguration(OrientDbConfig.class));
    }

    @Override
    public RepositoryFactory getRepositoryFactory() {
        return new OrientDbRepositoryFactory(this, networkConfig);
    }

    @Override
    public ImplementationTransactionAdapter<?> newTransactionAdapter() {
        return new OrientDbTransactionAdapter(this);
    }

    @Override
    public OrientDbContextExtension getContextExtension(PersistenceContext context) {
        return context.getExtensionCreateIfMissing(
                OrientDbContextExtension.class, () -> new OrientDbContextExtension(type, graphFactory));
    }

    @Override
    public void onContextClose(PersistenceContext context) {
        OrientDbContextExtension contextExtension = getContextExtension(context);
        DelegatingFramedGraph<OrientGraph> currentGraph = contextExtension.removeGraph();
        if (currentGraph != null) {
            String threadName = Thread.currentThread().getName();
            // Commit an implicit transaction to release graph resources.
            try {
                log.trace("Committing a transaction on the graph: {} in {}", currentGraph, threadName);
                currentGraph.getBaseGraph().commit();
            } catch (Exception e) {
                log.error("Failed to commit a transaction in {}", threadName, e);
            }

            try {
                log.trace("Closing the framed graph: {} in {}", currentGraph, threadName);
                currentGraph.close();
            } catch (IOException e) {
                throw new PersistenceException(String.format("Failed to close graph in %s", threadName), e);
            }
        }
    }
}
