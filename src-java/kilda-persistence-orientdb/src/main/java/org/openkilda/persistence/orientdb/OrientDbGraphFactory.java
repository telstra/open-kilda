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

import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.exceptions.RecoverablePersistenceException;
import org.openkilda.persistence.ferma.AnnotationFrameFactoryWithConverterSupport;
import org.openkilda.persistence.ferma.FramedGraphFactory;

import com.orientechnologies.common.exception.OException;
import com.orientechnologies.orient.core.sql.executor.OResultSet;
import com.syncleus.ferma.DelegatingFramedGraph;
import com.syncleus.ferma.framefactories.FrameFactory;
import com.syncleus.ferma.typeresolvers.TypeResolver;
import com.syncleus.ferma.typeresolvers.UntypedTypeResolver;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.tinkerpop.gremlin.orientdb.OrientGraph;
import org.apache.tinkerpop.gremlin.orientdb.OrientGraphFactory;

/**
 * A factory creates graph instances for interacting with OrientDB.
 */
@Slf4j
public class OrientDbGraphFactory implements FramedGraphFactory {
    private final OrientGraphFactory factory;
    private final FrameFactory builder = new AnnotationFrameFactoryWithConverterSupport();
    private final TypeResolver typeResolver = new UntypedTypeResolver();
    private final RetryPolicy<OrientGraph> poolAcquireRetryPolicy;

    OrientDbGraphFactory(@NonNull OrientDbConfig config) {
        log.debug("Opening a graph for {}", config);
        factory = new OrientGraphFactory(config.getUrl(), config.getUser(), config.getPassword());
        factory.setupPool(config.getPoolSize());
        log.debug("OrientGraphFactory instance has been created: {}", factory);

        poolAcquireRetryPolicy = new RetryPolicy<OrientGraph>()
                .handle(OException.class)
                .handle(RecoverablePersistenceException.class)
                .withMaxRetries(config.getPoolAcquireAttempts())
                .onRetry(e -> log.debug("Failure in acquiring a graph from the pool. Retrying #{}...",
                        e.getAttemptCount(), e.getLastFailure()))
                .onRetriesExceeded(e -> log.error("Failure in acquiring a graph from the pool. No more retries",
                        e.getFailure()));
    }

    /**
     * Returns an instance of framed graph which is bound to the current persistence context.
     * Create a new one if there's no such.
     */
    @Override
    public DelegatingFramedGraph<?> getGraph() {
        if (!ThreadLocalPersistenceContextHolder.INSTANCE.isContextInitialized()) {
            throw new PersistenceException("Persistence context is not initialized");
        }

        DelegatingFramedGraph<?> result = ThreadLocalPersistenceContextHolder.INSTANCE.getCurrentGraph();
        if (result == null) {
            log.debug("Opening a framed graph for {}", factory);
            OrientGraph resultOrientGraph = Failsafe.with(poolAcquireRetryPolicy).get(() -> {
                OrientGraph obtainedGraph = factory.getTx();
                validateGraph(obtainedGraph);
                return obtainedGraph;
            });
            log.debug("OrientGraph instance has been created: {}", resultOrientGraph);
            result = new DelegatingFramedGraph<>(resultOrientGraph, builder, typeResolver);
            ThreadLocalPersistenceContextHolder.INSTANCE.setCurrentGraph(result);
        }
        return result;
    }

    private void validateGraph(@NonNull OrientGraph obtainedGraph) {
        if (obtainedGraph.isClosed()) {
            throw new RecoverablePersistenceException("The obtained graph is closed");
        }
        try (OResultSet resultSet = obtainedGraph.executeSql("SELECT 1").getRawResultSet()) {
            if (!resultSet.hasNext()) {
                throw new RecoverablePersistenceException("Failed to execute the test query");
            }
        }
    }

    public OrientGraph getOrientGraph() {
        return (OrientGraph) getGraph().getBaseGraph();
    }
}

