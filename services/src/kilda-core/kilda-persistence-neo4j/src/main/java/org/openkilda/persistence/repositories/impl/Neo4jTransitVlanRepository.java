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

import org.openkilda.model.PathId;
import org.openkilda.model.TransitVlan;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowMeterRepository;
import org.openkilda.persistence.repositories.TransitVlanRepository;

import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * Neo4J OGM implementation of {@link FlowMeterRepository}.
 */
public class Neo4jTransitVlanRepository extends Neo4jGenericRepository<TransitVlan> implements TransitVlanRepository {
    static final String VLAN_PROPERTY_NAME = "vlan";
    static final String PATH_ID_PROPERTY_NAME = "path_id";

    public Neo4jTransitVlanRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
    }

    @Override
    public boolean exists(int vlan) {
        Filter vlanFilter = new Filter(VLAN_PROPERTY_NAME, ComparisonOperator.EQUALS, vlan);

        return getSession().count(getEntityType(), Collections.singleton(vlanFilter)) > 0;
    }

    @Override
    public Optional<TransitVlan> findById(int vlan) {
        Filter vlanFilter = new Filter(VLAN_PROPERTY_NAME, ComparisonOperator.EQUALS, vlan);

        Collection<TransitVlan> vlans = loadAll(vlanFilter);
        if (vlans.size() > 1) {
            throw new PersistenceException(format("Found more that 1 Vlan entity by (%d)", vlan));
        }
        return vlans.isEmpty() ? Optional.empty() : Optional.of(vlans.iterator().next());
    }

    @Override
    public Optional<TransitVlan> findByPathId(PathId pathId) {
        Filter pathIdFilter = new Filter(PATH_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, pathId);

        Collection<TransitVlan> vlans = loadAll(pathIdFilter);
        if (vlans.size() > 1) {
            throw new PersistenceException(format("Found more that 1 Vlan entity by (%s)", pathId));
        }
        return vlans.isEmpty() ? Optional.empty() : Optional.of(vlans.iterator().next());
    }

    @Override
    Class<TransitVlan> getEntityType() {
        return TransitVlan.class;
    }
}
