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

import org.openkilda.model.PathId;
import org.openkilda.model.Vxlan;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.VxlanRepository;

import com.google.common.collect.ImmutableMap;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Neo4j OGM implementation of {@link VxlanRepository}.
 */
public class Neo4jVxlanRepository extends Neo4jGenericRepository<Vxlan> implements VxlanRepository {
    static final String PATH_ID_PROPERTY_NAME = "path_id";

    public Neo4jVxlanRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
    }

    @Override
    public Collection<Vxlan> findByPathId(PathId pathId, PathId oppositePathId) {
        Filter pathIdFilter = new Filter(PATH_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, pathId);
        Collection<Vxlan> result = loadAll(pathIdFilter);
        if (result.isEmpty() && oppositePathId != null) {
            pathIdFilter = new Filter(PATH_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, oppositePathId);
            result = loadAll(pathIdFilter);
        }
        return result;
    }

    @Override
    public Optional<Integer> findUnassignedVxlan(int defaultVni) {
        Map<String, Object> parameters = ImmutableMap.of(
                "default_vni", defaultVni);

        // The query returns the default_vni if it's not used in any vxlan,
        // otherwise locates a gap between / after the values used in vxlan entities.

        String query = "UNWIND [$default_vni] AS vni "
                + "OPTIONAL MATCH (n:vxlan) "
                + "WHERE vni = n.vni "
                + "WITH vni, n "
                + "WHERE n IS NULL "
                + "RETURN vni "
                + "UNION ALL "
                + "MATCH (n1:vxlan) "
                + "WHERE n1.vni >= $default_vni "
                + "OPTIONAL MATCH (n2:vxlan) "
                + "WHERE (n1.vni + 1) = n2.vni "
                + "WITH n1, n2 "
                + "WHERE n2 IS NULL "
                + "RETURN n1.vni + 1 AS vni "
                + "ORDER BY vni "
                + "LIMIT 1";

        Iterator<Integer> results = getSession().query(Integer.class, query, parameters).iterator();
        return results.hasNext() ? Optional.of(results.next()) : Optional.empty();
    }

    @Override
    protected Class<Vxlan> getEntityType() {
        return Vxlan.class;
    }
}
