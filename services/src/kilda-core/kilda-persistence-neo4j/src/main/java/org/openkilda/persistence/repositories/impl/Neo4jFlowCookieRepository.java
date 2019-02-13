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

import org.openkilda.model.Cookie;
import org.openkilda.model.FlowCookie;
import org.openkilda.model.PathId;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowCookieRepository;

import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * Neo4J OGM implementation of {@link FlowCookieRepository}.
 */
public class Neo4jFlowCookieRepository extends Neo4jGenericRepository<FlowCookie> implements FlowCookieRepository {
    static final String COOKIE_PROPERTY_NAME = "cookie";
    static final String PATH_ID_PROPERTY_NAME = "path_id";

    public Neo4jFlowCookieRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
    }

    @Override
    public boolean exists(Cookie cookie) {
        Filter cookieFilter = new Filter(COOKIE_PROPERTY_NAME, ComparisonOperator.EQUALS, cookie);

        return getSession().count(getEntityType(), Collections.singleton(cookieFilter)) > 0;
    }

    @Override
    public Optional<FlowCookie> findById(Cookie cookie) {
        Filter cookieFilter = new Filter(COOKIE_PROPERTY_NAME, ComparisonOperator.EQUALS, cookie);

        Collection<FlowCookie> cookies = loadAll(cookieFilter);
        if (cookies.size() > 1) {
            throw new PersistenceException(format("Found more that 1 Cookie entity by (%s)", cookie));
        }
        return cookies.isEmpty() ? Optional.empty() : Optional.of(cookies.iterator().next());
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
    Class<FlowCookie> getEntityType() {
        return FlowCookie.class;
    }
}
