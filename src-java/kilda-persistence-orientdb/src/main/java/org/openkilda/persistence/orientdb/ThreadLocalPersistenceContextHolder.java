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

import com.syncleus.ferma.DelegatingFramedGraph;
import com.syncleus.ferma.tx.Tx;
import lombok.extern.slf4j.Slf4j;
import org.apache.tinkerpop.gremlin.orientdb.OrientGraph;

import java.io.IOException;

/**
 * Thread-local implementation of {@link PersistenceContextManager}. Keeps persistence context bound to a thread.
 */
@Slf4j
public final class ThreadLocalPersistenceContextHolder implements PersistenceContextManager {
    public static final ThreadLocalPersistenceContextHolder INSTANCE = new ThreadLocalPersistenceContextHolder();

    private final ThreadLocal<Boolean> initFlag = ThreadLocal.withInitial(() -> false);
    private final ThreadLocal<DelegatingFramedGraph> graphs = ThreadLocal.withInitial(() -> null);

    ThreadLocalPersistenceContextHolder() {
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
        if (isContextInitialized()) {
            log.trace("Closing the persistence context");
            initFlag.remove();
        }

        if (isTxOpen()) {
            log.trace("Closing the transaction: {}", Tx.getActive());
            throw new PersistenceException("Closing the persistence context with active transaction " + Tx.getActive());
        }

        DelegatingFramedGraph currentGraph = getCurrentGraph();
        if (currentGraph != null) {
            // Commit an implicit transaction to release graph resources.
            try {
                log.trace("Committing a transaction on the graph: {}", currentGraph);
                ((OrientGraph) currentGraph.getBaseGraph()).commit();
            } catch (Exception e) {
                log.error("Failed to commit a transaction", e);
            }

            try {
                log.trace("Closing the framed graph: {}", currentGraph);
                currentGraph.close();
            } catch (IOException e) {
                throw new PersistenceException("Failed to close graph", e);
            } finally {
                removeCurrentGraph();
            }
        }
    }

    @Override
    public boolean isTxOpen() {
        Tx activeTx = Tx.getActive();
        return activeTx != null && activeTx.isOpen();
    }

    /**
     * Gets a graph bound to the current thread.
     */
    public DelegatingFramedGraph getCurrentGraph() {
        return graphs.get();
    }

    /**
     * Bounds a graph to the current thread.
     */
    public void setCurrentGraph(DelegatingFramedGraph graph) {
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
