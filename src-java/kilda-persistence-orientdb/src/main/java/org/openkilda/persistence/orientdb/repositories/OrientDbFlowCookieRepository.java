/* Copyright 2020 Telstra Open Source
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

package org.openkilda.persistence.orientdb.repositories;

import static java.lang.String.format;

import org.openkilda.persistence.ferma.frames.FlowCookieFrame;
import org.openkilda.persistence.ferma.repositories.FermaFlowCookieRepository;
import org.openkilda.persistence.orientdb.OrientDbPersistenceImplementation;
import org.openkilda.persistence.repositories.FlowCookieRepository;

import org.apache.tinkerpop.gremlin.orientdb.executor.OGremlinResult;
import org.apache.tinkerpop.gremlin.orientdb.executor.OGremlinResultSet;

import java.util.Iterator;
import java.util.Optional;

/**
 * OrientDB implementation of {@link FlowCookieRepository}.
 */
public class OrientDbFlowCookieRepository extends FermaFlowCookieRepository {
    private final GraphSupplier graphSupplier;

    OrientDbFlowCookieRepository(OrientDbPersistenceImplementation implementation, GraphSupplier graphSupplier) {
        super(implementation);
        this.graphSupplier = graphSupplier;
    }

    @Override
    public boolean exists(long unmaskedCookie) {
        try (OGremlinResultSet results = graphSupplier.get().querySql(
                format("SELECT @rid FROM %s WHERE %s = ? LIMIT 1",
                        FlowCookieFrame.FRAME_LABEL, FlowCookieFrame.UNMASKED_COOKIE_PROPERTY), unmaskedCookie)) {
            return results.iterator().hasNext();
        }
    }

    @Override
    public Optional<Long> findFirstUnassignedCookie(long lowestCookieValue, long highestCookieValue) {
        try (OGremlinResultSet results = graphSupplier.get().querySql(
                format("SELECT FROM (SELECT difference(unionAll($init_cookie, $next_to_cookies).cookie, "
                                + "$cookies.cookie) as cookie "
                                + "LET $init_cookie = (SELECT %sL as cookie), "
                                + "$cookies = (SELECT %s.asLong() as cookie FROM %s WHERE %s >= ? AND %s < ?), "
                                + "$next_to_cookies = (SELECT %s.asLong() + 1 as cookie FROM %s "
                                + "WHERE %s >= ? AND %s < ?) "
                                + "UNWIND cookie) ORDER by cookie LIMIT 1",
                        lowestCookieValue,
                        FlowCookieFrame.UNMASKED_COOKIE_PROPERTY, FlowCookieFrame.FRAME_LABEL,
                        FlowCookieFrame.UNMASKED_COOKIE_PROPERTY, FlowCookieFrame.UNMASKED_COOKIE_PROPERTY,
                        FlowCookieFrame.UNMASKED_COOKIE_PROPERTY, FlowCookieFrame.FRAME_LABEL,
                        FlowCookieFrame.UNMASKED_COOKIE_PROPERTY, FlowCookieFrame.UNMASKED_COOKIE_PROPERTY),
                lowestCookieValue, highestCookieValue, lowestCookieValue, highestCookieValue)) {
            Iterator<OGremlinResult> it = results.iterator();
            if (it.hasNext()) {
                Number cookie = it.next().getProperty("cookie");
                if (cookie != null) {
                    return Optional.of(cookie.longValue());
                }
            }
            return Optional.empty();
        }
    }
}
