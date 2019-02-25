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

import org.openkilda.model.FlowCookie;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowCookieRepository;

import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;

/**
 * Neo4J OGM implementation of {@link FlowCookieRepository}.
 */
public class Neo4jFlowCookieRepository extends Neo4jGenericRepository<FlowCookie> implements FlowCookieRepository {
    static final String PATH_ID_PROPERTY_NAME = "path_id";

    public Neo4jFlowCookieRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
    }

    @Override
    public Optional<FlowCookie> findByPathId(PathId pathId) {
        Filter pathIdFilter = new Filter(PATH_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, pathId);

        Collection<FlowCookie> cookies = loadAll(pathIdFilter);
        if (cookies.size() > 1) {
            throw new PersistenceException(format("Found more that 1 Cookie entity by (%s)", pathId));
        }
        return cookies.isEmpty() ? Optional.empty() : Optional.of(cookies.iterator().next());
    }

    @Override
    public Optional<Long> findAvailableUnmaskedCookie() {
        String query = "MATCH (n:flow_cookie) "
                + "OPTIONAL MATCH (n1:flow_cookie) "
                + "WHERE (n.unmasked_cookie + 1) = n1.unmasked_cookie "
                + "WITH n, n1 "
                + "WHERE n1 IS NULL "
                + "RETURN n.unmasked_cookie + 1";

        Iterator<Long> results = getSession().query(Long.class, query, Collections.emptyMap()).iterator();
        return results.hasNext() ? Optional.of(results.next()) : Optional.empty();
    }

    @Override
    Class<FlowCookie> getEntityType() {
        return FlowCookie.class;
    }
}
