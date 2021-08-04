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
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.tinkerpop.gremlin.orientdb.OrientGraph;
import org.apache.tinkerpop.gremlin.orientdb.OrientGraphFactory;

import java.io.Serializable;

/**
 * A factory creates graph instances for interacting with OrientDB.
 */
@Slf4j
public class OrientDbGraphFactory implements FramedGraphFactory<DelegatingFramedGraph<?>>, Serializable {
    private final ThreadLocalPersistenceContextManagerSupplier contextManagerSupplier;
    private final OrientDbConfig config;

    private transient volatile Connect connect;

    OrientDbGraphFactory(
            ThreadLocalPersistenceContextManagerSupplier contextManagerSupplier, @NonNull OrientDbConfig config) {
        this.contextManagerSupplier = contextManagerSupplier;
        this.config = config;
    }

    /**
     * Returns an instance of framed graph which is bound to the current persistence context.
     * Create a new one if there's no such.
     */
    @Override
    public DelegatingFramedGraph<OrientGraph> getGraph() {
        ThreadLocalPersistenceContextManager contextManager = contextManagerSupplier.get();
        if (!contextManager.isContextInitialized()) {
            throw new PersistenceException("Persistence context is not initialized");
        }

        DelegatingFramedGraph<OrientGraph> result = contextManager.getCurrentGraph();
        if (result == null) {
            Connect effectiveConnect = getConnectCreateIfMissing();

            OrientGraphFactory factory = effectiveConnect.getFactory();
            log.debug("Opening a framed graph for {}", factory);

            OrientGraph orientGraph = Failsafe.with(newPoolAcquireRetryPolicy()).get(() -> {
                OrientGraph obtainedGraph = factory.getTx();
                validateGraph(obtainedGraph);
                return obtainedGraph;
            });

            log.debug("OrientGraph instance has been created: {}", orientGraph);
            result = new DelegatingFramedGraph<>(
                    orientGraph, effectiveConnect.getBuilder(), effectiveConnect.getTypeResolver());

            contextManager.setCurrentGraph(result);
        }
        return result;
    }

    private RetryPolicy<OrientGraph> newPoolAcquireRetryPolicy() {
        return new RetryPolicy<OrientGraph>()
                .handle(OException.class)
                .handle(RecoverablePersistenceException.class)
                .withMaxRetries(config.getPoolAcquireAttempts())
                .onRetry(e -> log.debug("Failure in acquiring a graph from the pool. Retrying #{}...",
                        e.getAttemptCount(), e.getLastFailure()))
                .onRetriesExceeded(e -> log.error("Failure in acquiring a graph from the pool. No more retries",
                        e.getFailure()));
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
        return getGraph().getBaseGraph();
    }

    private Connect getConnectCreateIfMissing() {
        if (connect == null) {
            synchronized (this) {
                if (connect == null) {
                    connect = new Connect(config);
                }
            }
        }
        return connect;
    }

    @Value
    private static class Connect {
        OrientGraphFactory factory;
        FrameFactory builder = new AnnotationFrameFactoryWithConverterSupport();
        TypeResolver typeResolver = new UntypedTypeResolver();

        public Connect(OrientDbConfig config) {
            log.debug(
                    "Opening a graph for (url=\"{}\", login=\"{}\", password=*****)",
                    config.getUrl(), config.getUser());

            factory = new OrientGraphFactory(config.getUrl(), config.getUser(), config.getPassword());
            factory.setupPool(config.getPoolSize());
            log.debug("OrientGraphFactory instance has been created: {}", factory);
        }
    }
}

