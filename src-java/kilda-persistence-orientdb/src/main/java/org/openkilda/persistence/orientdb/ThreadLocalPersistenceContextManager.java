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

import org.openkilda.persistence.context.PersistenceContextManager;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.tx.TransactionManager;

import com.syncleus.ferma.DelegatingFramedGraph;
import lombok.extern.slf4j.Slf4j;
import org.apache.tinkerpop.gremlin.orientdb.OrientGraph;

import java.io.IOException;

/**
 * Thread-local implementation of {@link PersistenceContextManager}. Keeps persistence context bound to a thread.
 */
@Slf4j
public final class ThreadLocalPersistenceContextManager implements PersistenceContextManager {
    private static final ThreadLocal<Boolean> initFlag = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<DelegatingFramedGraph<OrientGraph>> graphs = ThreadLocal.withInitial(() -> null);

    private final TransactionManager transactionManager;

    public ThreadLocalPersistenceContextManager(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    public void initContext() {
        // Close the current context if it's open.
        closeContext();

        log.trace("Initializing persistence context");
        initFlag.set(true);
    }

    @Override
    public boolean isContextInitialized() {
        return initFlag.get();
    }

    @Override
    public void closeContext() {
        String threadName = Thread.currentThread().getName();
        if (isContextInitialized()) {
            log.trace("Closing the persistence context in {}", threadName);
            initFlag.remove();
        }

        if (isTxOpen()) {
            throw new PersistenceException(String.format(
                    "Closing the persistence context with active transaction in %s", threadName));
        }

        DelegatingFramedGraph<OrientGraph> currentGraph = getCurrentGraph();
        if (currentGraph != null) {
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
            } finally {
                removeCurrentGraph();
            }
        }
    }

    @Override
    public boolean isTxOpen() {
        return transactionManager.isTxOpen();
    }

    /**
     * Gets a graph bound to the current thread.
     */
    public DelegatingFramedGraph<OrientGraph> getCurrentGraph() {
        return graphs.get();
    }

    /**
     * Bounds a graph to the current thread.
     */
    public void setCurrentGraph(DelegatingFramedGraph<OrientGraph> graph) {
        log.trace("Set the framed graph as current: {}", graph);
        graphs.set(graph);
    }

    /**
     * Un-bounds a graph from the current thread.
     */
    private void removeCurrentGraph() {
        log.trace("Remove(un-bound) the current framed graph");
        graphs.remove();
    }
}
