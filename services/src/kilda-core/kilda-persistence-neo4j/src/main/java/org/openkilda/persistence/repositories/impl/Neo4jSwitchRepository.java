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
import static java.util.Collections.singleton;

import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.FetchStrategy;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.SwitchRepository;

import com.google.common.collect.ImmutableMap;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.session.Neo4jSession;
import org.neo4j.ogm.session.Session;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;

/**
 * Neo4j OGM implementation of {@link SwitchRepository}.
 */
public class Neo4jSwitchRepository extends Neo4jGenericRepository<Switch> implements SwitchRepository {
    static final String SWITCH_NAME_PROPERTY_NAME = "name";

    public Neo4jSwitchRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
    }

    @Override
    public boolean exists(SwitchId switchId) {
        Filter switchNameFilter = new Filter(SWITCH_NAME_PROPERTY_NAME, ComparisonOperator.EQUALS, switchId);

        return getSession().count(getEntityType(), singleton(switchNameFilter)) > 0;
    }

    @Override
    public Optional<Switch> findById(SwitchId switchId) {
        return findById(getSession(), switchId, getDefaultFetchStrategy());
    }

    private Optional<Switch> findById(Session session, SwitchId switchId, FetchStrategy fetchStrategy) {
        Filter switchNameFilter = new Filter(SWITCH_NAME_PROPERTY_NAME, ComparisonOperator.EQUALS, switchId);

        Collection<Switch> switches = loadAll(switchNameFilter, fetchStrategy);
        if (switches.size() > 1) {
            throw new PersistenceException(format("Found more that 1 Switch entity by %s as name", switchId));
        }
        return switches.isEmpty() ? Optional.empty() : Optional.of(switches.iterator().next());
    }

    @Override
    public Switch reload(Switch entity) {
        Session session = getSession();
        Long graphId = session.resolveGraphIdFor(entity);
        if (graphId != null) {
            Object sessionEntity = ((Neo4jSession) session).context().getNodeEntity(graphId);
            if (sessionEntity instanceof Switch) {
                // no need to reload if attached.
                return (Switch) sessionEntity;
            } else if (sessionEntity != null) {
                throw new PersistenceException(format("Expected a Switch entity, but found %s.", sessionEntity));
            }
        }

        return findById(session, entity.getSwitchId(), FetchStrategy.NO_RELATIONS)
                .orElseThrow(() -> new PersistenceException(format("Switch not found: %s", entity.getSwitchId())));
    }

    @Override
    public void forceDelete(SwitchId switchId) {
        transactionManager.doInTransaction(() -> {
            Session session = getSession();
            session.query("MATCH (:switch {name: $name})-[]-(n:flow_meter) DETACH DELETE n",
                    ImmutableMap.of("name", switchId.toString()));

            session.query("MATCH (sw:switch {name: $name}) DETACH DELETE sw",
                    ImmutableMap.of("name", switchId.toString()));
        });
    }

    @Override
    public void lockSwitches(Switch... switches) {
        super.lockSwitches(Arrays.stream(switches).map(Switch::getSwitchId));
    }

    @Override
    protected Class<Switch> getEntityType() {
        return Switch.class;
    }
}
