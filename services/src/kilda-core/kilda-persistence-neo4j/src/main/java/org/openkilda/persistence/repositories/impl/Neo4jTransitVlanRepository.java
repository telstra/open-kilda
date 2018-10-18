/* Copyright 2019 Telstra Open Source
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

import com.google.common.collect.ImmutableMap;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Neo4J OGM implementation of {@link FlowMeterRepository}.
 */
public class Neo4jTransitVlanRepository extends Neo4jGenericRepository<TransitVlan> implements TransitVlanRepository {
    static final String PATH_ID_PROPERTY_NAME = "path_id";

    public Neo4jTransitVlanRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
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
    public Optional<Integer> findUnassignedTransitVlan(int defaultVlan) {
        Map<String, Object> parameters = ImmutableMap.of(
                "default_vlan", defaultVlan);

        // The query returns the default_vlan if it's not used in any transit_vlan,
        // otherwise locates a gap between / after the values used in transit_vlan entities.

        String query = "UNWIND [$default_vlan] AS vlan "
                + "OPTIONAL MATCH (n:transit_vlan) "
                + "WHERE vlan = n.vlan "
                + "WITH vlan, n "
                + "WHERE n IS NULL "
                + "RETURN vlan "
                + "UNION ALL "
                + "MATCH (n1:transit_vlan) "
                + "OPTIONAL MATCH (n2:transit_vlan) "
                + "WHERE (n1.vlan + 1) = n2.vlan "
                + "WITH n1, n2 "
                + "WHERE n2 IS NULL "
                + "RETURN n1.vlan + 1 AS vlan "
                + "LIMIT 1";

        Iterator<Integer> results = getSession().query(Integer.class, query, parameters).iterator();
        return results.hasNext() ? Optional.of(results.next()) : Optional.empty();
    }

    @Override
    protected Class<TransitVlan> getEntityType() {
        return TransitVlan.class;
    }
}
