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

import org.openkilda.model.Switch;
import org.openkilda.persistence.neo4j.Neo4jSessionFactory;
import org.openkilda.persistence.repositories.SwitchRepository;

import java.util.HashMap;
import java.util.Map;

/**
 * Neo4J OGM implementation of {@link SwitchRepository}.
 */
public class SwitchRepositoryImpl extends GenericRepository<Switch> implements SwitchRepository {
    public SwitchRepositoryImpl(Neo4jSessionFactory sessionFactory) {
        super(sessionFactory);
    }

    @Override
    public Switch findByName(String name) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("name", name);

        return getSession().queryForObject(Switch.class, "MATCH (sw:switch{name: {name}}) RETURN sw", parameters);
    }

    @Override
    Class<Switch> getEntityType() {
        return Switch.class;
    }
}
