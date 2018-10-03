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

import org.openkilda.model.Isl;
import org.openkilda.persistence.neo4j.Neo4jSessionFactory;
import org.openkilda.persistence.repositories.IslRepository;

import org.neo4j.ogm.cypher.query.SortOrder;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Neo4J OGM implementation of {@link IslRepository}.
 */
public class IslRepositoryImpl extends GenericRepository<Isl> implements IslRepository {
    public IslRepositoryImpl(Neo4jSessionFactory sessionFactory) {
        super(sessionFactory);
    }

    @Override
    public Isl findByEndpoint(long switchId, int port) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("src_switch", switchId);
        parameters.put("src_port", port);

        return getSession().queryForObject(Isl.class, "MATCH (src:switch)-[target:isl]->(:switch)\n"
                + " WHERE src.name=$src_switch AND target.src_port=$src_port\n"
                + " RETURN target", parameters);
    }

    public Collection<Isl> findAllOrderedBySrcSwitch() {
        return getSession().loadAll(getEntityType(), new SortOrder("src_switch"));
    }

    @Override
    Class<Isl> getEntityType() {
        return Isl.class;
    }
}
