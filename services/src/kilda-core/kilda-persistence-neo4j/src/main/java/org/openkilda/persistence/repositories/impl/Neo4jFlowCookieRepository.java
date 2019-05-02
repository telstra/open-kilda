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

import org.openkilda.model.FlowCookie;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowCookieRepository;

import com.google.common.collect.ImmutableMap;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Neo4j OGM implementation of {@link FlowCookieRepository}.
 */
public class Neo4jFlowCookieRepository extends Neo4jGenericRepository<FlowCookie> implements FlowCookieRepository {
    static final String UNMASKED_COOKIE_PROPERTY_NAME = "unmasked_cookie";

    public Neo4jFlowCookieRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
    }

    @Override
    public Optional<FlowCookie> findByCookie(long unmaskedCookie) {
        Filter cookieFilter = new Filter(UNMASKED_COOKIE_PROPERTY_NAME, ComparisonOperator.EQUALS, unmaskedCookie);

        Collection<FlowCookie> cookies = loadAll(cookieFilter);
        if (cookies.size() > 1) {
            throw new PersistenceException(format("Found more that 1 Cookie entity by (%s)", unmaskedCookie));
        }
        return cookies.isEmpty() ? Optional.empty() : Optional.of(cookies.iterator().next());
    }

    @Override
    public Optional<Long> findUnassignedCookie(long defaultCookie) {
        Map<String, Object> parameters = ImmutableMap.of(
                "default_cookie", defaultCookie);

        // The query returns the default_cookie if it's not used in any flow_cookie,
        // otherwise locates a gap between / after the values used in flow_cookie entities.

        String query = "UNWIND [$default_cookie] AS cookie "
                + "OPTIONAL MATCH (n:flow_cookie) "
                + "WHERE cookie = n.unmasked_cookie "
                + "WITH cookie, n "
                + "WHERE n IS NULL "
                + "RETURN cookie "
                + "UNION ALL "
                + "MATCH (n1:flow_cookie) "
                + "WHERE n1.unmasked_cookie > $default_cookie "
                + "OPTIONAL MATCH (n2:flow_cookie) "
                + "WHERE (n1.unmasked_cookie + 1) = n2.unmasked_cookie "
                + "WITH n1, n2 "
                + "WHERE n2 IS NULL "
                + "RETURN n1.unmasked_cookie + 1 AS cookie "
                + "ORDER BY cookie "
                + "LIMIT 1";

        Iterator<Long> results = getSession().query(Long.class, query, parameters).iterator();
        return results.hasNext() ? Optional.of(results.next()) : Optional.empty();
    }

    @Override
    protected Class<FlowCookie> getEntityType() {
        return FlowCookie.class;
    }
}
