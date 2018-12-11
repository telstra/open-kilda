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

package org.openkilda.persistence.repositories.impl;

import static java.lang.String.format;

import org.openkilda.model.Switch;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.Repository;

import com.google.common.collect.ImmutableMap;
import org.neo4j.ogm.session.Session;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;

/**
 * Base Neo4J OGM implementation of {@link Repository}.
 */
abstract class Neo4jGenericRepository<T> implements Repository<T> {
    static final int DEPTH_LOAD_ENTITY = 1;
    static final int DEPTH_CREATE_UPDATE_ENTITY = 0;

    private final Neo4jSessionFactory sessionFactory;
    protected final TransactionManager transactionManager;

    Neo4jGenericRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        this.sessionFactory = sessionFactory;
        this.transactionManager = transactionManager;
    }

    Session getSession() {
        return sessionFactory.getSession();
    }

    @Override
    public Collection<T> findAll() {
        return getSession().loadAll(getEntityType(), DEPTH_LOAD_ENTITY);
    }

    @Override
    public void delete(T entity) {
        Session session = getSession();
        if (session.resolveGraphIdFor(entity) == null) {
            throw new PersistenceException("Required GraphId wasn't set: " + entity.toString());
        }
        session.delete(entity);
    }

    @Override
    public void createOrUpdate(T entity) {
        getSession().save(entity, DEPTH_CREATE_UPDATE_ENTITY);
    }

    abstract Class<T> getEntityType();

    protected <V> V requireManagedEntity(V entity) {
        Session session = getSession();
        if (session.resolveGraphIdFor(entity) == null) {
            throw new PersistenceException(
                    format("Entity %s is not managed by Neo4j OGM (forget to reload or save?): ", entity));
        }

        return entity;
    }

    protected void lockSwitch(Switch sw) {
        Map<String, Object> parameters = ImmutableMap.of("name", sw.getSwitchId().toString());
        Long updatedEntityId = getSession().queryForObject(Long.class,
                "MATCH (sw:switch {name: $name}) "
                        + "SET sw.tx_override_workaround='dummy' "
                        + "RETURN id(sw)", parameters);
        if (updatedEntityId == null) {
            throw new PersistenceException(format("Switch not found to be locked: %s", sw.getSwitchId()));
        }
    }

    protected void lockSwitches(Switch... switches) {
        // Lock switches in ascending order of switchId.
        Arrays.stream(switches)
                .sorted(Comparator.comparing(Switch::getSwitchId))
                .forEach(this::lockSwitch);
    }
}
