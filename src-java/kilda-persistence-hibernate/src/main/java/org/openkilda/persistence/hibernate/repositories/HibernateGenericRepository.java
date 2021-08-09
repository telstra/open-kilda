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

package org.openkilda.persistence.hibernate.repositories;

import org.openkilda.model.CompositeDataEntity;
import org.openkilda.persistence.hibernate.entities.EntityBase;
import org.openkilda.persistence.repositories.Repository;
import org.openkilda.persistence.tx.TransactionManager;

import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.util.function.Supplier;

public abstract class HibernateGenericRepository<M extends CompositeDataEntity<V>, V, H extends V>
        implements Repository<M> {
    protected final TransactionManager transactionManager;
    private final Supplier<SessionFactory> factorySupplier;

    public HibernateGenericRepository(TransactionManager transactionManager, Supplier<SessionFactory> factorySupplier) {
        this.transactionManager = transactionManager;
        this.factorySupplier = factorySupplier;
    }

    @Override
    public void add(M model) {
        V view = model.getData();
        if (view instanceof EntityBase) {
            throw new IllegalArgumentException("Entity of class " + model + " already persisted");
        }
        transactionManager.doInTransaction(() -> {
            H entity = makeEntity(view);
            getSession().persist(entity);
            model.setData(entity);
        });
    }

    protected abstract H makeEntity(V view);

    @Override
    public void remove(M model) {
        V view = model.getData();
        if (! (view instanceof EntityBase)) {
            throw new IllegalArgumentException(
                    "Can't make not persistent entity " + model + ", because it is not persisted now");
        }

        @SuppressWarnings("unchecked")
        H hibernateView = (H) view;
        V detachedView = doDetach(model, hibernateView);

        transactionManager.doInTransaction(() -> getSession().remove(hibernateView));
        model.setData(detachedView);
    }

    @Override
    public void detach(M model) {
        V view = model.getData();
        if (view instanceof EntityBase) {
            @SuppressWarnings("unchecked")
            H hibernateView = (H) view;
            model.setData(doDetach(model, hibernateView));
        }
    }

    protected abstract V doDetach(M model, H entity);

    protected Session getSession() {
        return factorySupplier.get().getCurrentSession();
    }
}
