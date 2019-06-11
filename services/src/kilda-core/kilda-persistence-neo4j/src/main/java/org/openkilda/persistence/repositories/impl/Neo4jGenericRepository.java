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
import static org.openkilda.persistence.repositories.impl.Neo4jSwitchRepository.SWITCH_NAME_PROPERTY_NAME;

import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.ConstraintViolationException;
import org.openkilda.persistence.FetchStrategy;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.RecoverablePersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.Repository;

import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.v1.exceptions.ClientException;
import org.neo4j.driver.v1.exceptions.TransientException;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.cypher.Filters;
import org.neo4j.ogm.cypher.query.SortOrder;
import org.neo4j.ogm.exception.core.MappingException;
import org.neo4j.ogm.session.Session;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Base Neo4j OGM implementation of {@link Repository}.
 * Provides basic implementation of findAll, createOrUpdate and delete methods.
 */
@Slf4j
abstract class Neo4jGenericRepository<T> implements Repository<T> {
    protected static final Filters EMPTY_FILTERS = new Filters();

    private static final String SRC_SWITCH_FIELD = "srcSwitch";
    private static final String DEST_SWITCH_FIELD = "destSwitch";

    private final Neo4jSessionFactory sessionFactory;
    protected final TransactionManager transactionManager;

    Neo4jGenericRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        this.sessionFactory = sessionFactory;
        this.transactionManager = transactionManager;
    }

    @Override
    public Collection<T> findAll() {
        return loadAll(EMPTY_FILTERS, getDefaultFetchStrategy());
    }

    @Override
    public void createOrUpdate(T entity) {
        try {
            getSession().save(entity, getDepthCreateUpdateEntity());
        } catch (ClientException ex) {
            if (ex.code().endsWith("ConstraintValidationFailed")) {
                throw new ConstraintViolationException("Unable to create/update " + getEntityType(), ex);
            } else {
                throw ex;
            }
        } catch (MappingException ex) {
            log.error("OGM mapping exception", ex.getCause());
            throw new PersistenceException("Unable to create/update " + getEntityType(), ex);
        } catch (TransientException ex) {
            throw new RecoverablePersistenceException("Unable to create/update " + getEntityType(), ex);
        }
    }

    @Override
    public void delete(T entity) {
        try {
            getSession().delete(requireManagedEntity(entity));
        } catch (TransientException ex) {
            throw new RecoverablePersistenceException("Unable to delete " + getEntityType(), ex);
        }
    }

    protected abstract Class<T> getEntityType();

    protected FetchStrategy getDefaultFetchStrategy() {
        // the default depth for loading an entity.
        return FetchStrategy.DIRECT_RELATIONS;
    }

    protected int getDepthLoadEntity(FetchStrategy fetchStrategy) {
        switch (fetchStrategy) {
            case DIRECT_RELATIONS:
                return 1;
            case NO_RELATIONS:
                return 0;
            default:
                throw new IllegalArgumentException("Unsupported fetch strategy " + fetchStrategy);
        }
    }

    protected int getDepthCreateUpdateEntity() {
        // the default depth for creating/updating an entity.
        return 0;
    }

    protected Session getSession() {
        return sessionFactory.getSession();
    }

    protected Collection<T> loadAll(Filter filter) {
        return loadAll(filter, getDefaultFetchStrategy());
    }

    protected Collection<T> loadAll(Filter filter, FetchStrategy fetchStrategy) {
        return loadAll(new Filters(filter), fetchStrategy);
    }

    protected Collection<T> loadAll(Filter filter, SortOrder sortOrder, FetchStrategy fetchStrategy) {
        try {
            return getSession().loadAll(getEntityType(), filter, sortOrder, getDepthLoadEntity(fetchStrategy));
        } catch (MappingException ex) {
            log.error("OGM mapping exception", ex.getCause());
            throw new PersistenceException("Unable to load " + getEntityType(), ex);
        } catch (TransientException ex) {
            throw new RecoverablePersistenceException("Unable to load " + getEntityType(), ex);
        }
    }

    protected Collection<T> loadAll(Filters filters) {
        return loadAll(filters, getDefaultFetchStrategy());
    }

    protected Collection<T> loadAll(Filters filters, FetchStrategy fetchStrategy) {
        try {
            return getSession().loadAll(getEntityType(), filters, getDepthLoadEntity(fetchStrategy));
        } catch (MappingException ex) {
            log.error("OGM mapping exception", ex.getCause());
            throw new PersistenceException("Unable to load " + getEntityType(), ex);
        } catch (TransientException ex) {
            throw new RecoverablePersistenceException("Unable to load " + getEntityType(), ex);
        }
    }

    protected Filter createSrcSwitchFilter(SwitchId switchId) {
        Filter srcSwitchFilter = new Filter(SWITCH_NAME_PROPERTY_NAME, ComparisonOperator.EQUALS, switchId.toString());
        srcSwitchFilter.setNestedPath(new Filter.NestedPathSegment(SRC_SWITCH_FIELD, Switch.class));
        return srcSwitchFilter;
    }

    protected Filter createDstSwitchFilter(SwitchId switchId) {
        Filter dstSwitchFilter = new Filter(SWITCH_NAME_PROPERTY_NAME, ComparisonOperator.EQUALS, switchId.toString());
        dstSwitchFilter.setNestedPath(new Filter.NestedPathSegment(DEST_SWITCH_FIELD, Switch.class));
        return dstSwitchFilter;
    }

    protected <V> V requireManagedEntity(V entity) {
        if (getSession().resolveGraphIdFor(entity) == null) {
            throw new PersistenceException(
                    format("Entity %s is not managed by Neo4j OGM (forget to reload or save?): ", entity));
        }

        return entity;
    }

    protected void lockSwitches(SwitchId... switches) {
        lockSwitches(Arrays.stream(switches));
    }

    protected void lockSwitches(Stream<SwitchId> switches) {
        // Lock switches in ascending order of switchId.
        switches.<Map<SwitchId, SwitchId>>collect(TreeMap::new, (m, e) -> m.put(e, e), Map::putAll)
                .values()
                .forEach(this::lockSwitch);
    }

    private void lockSwitch(SwitchId switchId) {
        Map<String, Object> parameters = ImmutableMap.of("name", switchId.toString());
        Long updatedEntityId = getSession().queryForObject(Long.class,
                "MATCH (sw:switch {name: $name}) "
                        + "SET sw.tx_override_workaround='dummy' "
                        + "RETURN id(sw)", parameters);
        if (updatedEntityId == null) {
            throw new PersistenceException(format("Switch not found to be locked: %s", switchId));
        }
    }
}
