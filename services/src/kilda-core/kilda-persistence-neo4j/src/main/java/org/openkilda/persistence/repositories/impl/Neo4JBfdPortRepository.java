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

import org.openkilda.model.BfdPort;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.BfdPortRepository;
import org.openkilda.persistence.repositories.SwitchRepository;

import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.cypher.Filters;

import java.util.Collection;
import java.util.Optional;

/**
 * Neo4J OGM implementation of {@link SwitchRepository}.
 */
public class Neo4JBfdPortRepository extends Neo4jGenericRepository<BfdPort> implements BfdPortRepository {


    public Neo4JBfdPortRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
    }

    @Override
    Class<BfdPort> getEntityType() {
        return BfdPort.class;
    }

    @Override
    public boolean exists(SwitchId switchId, Integer port) {
        return getSession().count(getEntityType(), getFilters(switchId, port)) > 0;
    }

    @Override
    public Optional<BfdPort> findBySwitchIdAndPort(SwitchId switchId, Integer port) {
        Collection<BfdPort> ports = getSession().loadAll(getEntityType(), getFilters(switchId, port),
                DEPTH_LOAD_ENTITY);
        if (ports.size() > 1) {
            throw new PersistenceException(format("Found more that 1 BfdPort entity by switch: %s port: %d",
                    switchId, port));
        }
        return ports.isEmpty() ? Optional.empty() : Optional.of(ports.iterator().next());
    }

    private Filters getFilters(SwitchId switchId, Integer port) {
        Filters filters = new Filters();
        filters.and(new Filter(BfdPort.SWITCH_PROPERTY_NAME, ComparisonOperator.EQUALS, switchId));
        filters.and(new Filter(BfdPort.PORT_PROPERTY_NAME, ComparisonOperator.EQUALS, port));
        return filters;
    }
}
