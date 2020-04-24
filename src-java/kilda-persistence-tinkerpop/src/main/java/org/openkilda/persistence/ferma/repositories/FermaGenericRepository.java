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

package org.openkilda.persistence.ferma.repositories;

import org.openkilda.model.CompositeDataEntity;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.ferma.FramedGraphFactory;
import org.openkilda.persistence.repositories.Repository;
import org.openkilda.persistence.tx.TransactionManager;

import com.syncleus.ferma.ElementFrame;
import com.syncleus.ferma.FramedGraph;
import lombok.extern.slf4j.Slf4j;

/**
 * Base repository implementation.
 */
@Slf4j
abstract class FermaGenericRepository<E extends CompositeDataEntity<D>, D, F extends D> implements Repository<E> {
    private final FramedGraphFactory<?> graphFactory;
    protected final TransactionManager transactionManager;

    FermaGenericRepository(FramedGraphFactory<?> graphFactory, TransactionManager transactionManager) {
        this.graphFactory = graphFactory;
        this.transactionManager = transactionManager;
    }

    protected FramedGraph framedGraph() {
        FramedGraph graph = graphFactory.getGraph();
        if (graph == null) {
            throw new PersistenceException("Failed to obtain a framed graph");
        }
        return graph;
    }

    @Override
    public void add(E entity) {
        D data = entity.getData();
        if (data instanceof ElementFrame) {
            throw new IllegalArgumentException("Can't add entity " + entity + " which is already framed graph element");
        }
        transactionManager.doInTransaction(() -> entity.setData(doAdd(data)));
    }

    protected abstract F doAdd(D data);

    @Override
    @SuppressWarnings("unchecked")
    //TODO: @TransactionRequired
    public void remove(E entity) {
        //TODO: replace with @TransactionRequired. Requirement for an outside transaction comes from the case when
        //the entity must be reloaded with the actual version to have the transaction succeeded.
        if (!transactionManager.isTxOpen()) {
            throw new PersistenceException("A transactional method was invoked outside a transaction.");
        }

        D data = entity.getData();
        if (data instanceof ElementFrame) {
            D detachedData = doDetach(entity, (F) data);
            doRemove((F) data);
            entity.setData(detachedData);
        } else {
            throw new IllegalArgumentException("Can't delete object " + entity + " which is not framed graph element");
        }
    }

    protected abstract void doRemove(F frame);

    @Override
    @SuppressWarnings("unchecked")
    public void detach(E entity) {
        D data = entity.getData();
        if (data instanceof ElementFrame) {
            entity.setData(doDetach(entity, (F) data));
        }
    }

    protected abstract D doDetach(E entity, F frame);
}
