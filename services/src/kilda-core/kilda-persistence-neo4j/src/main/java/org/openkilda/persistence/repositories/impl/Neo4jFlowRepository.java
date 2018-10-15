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

import org.openkilda.model.Flow;
import org.openkilda.persistence.repositories.FlowRepository;

import java.util.HashMap;
import java.util.Map;

/**
 * Neo4J OGM implementation of {@link FlowRepository}.
 */
public class Neo4jFlowRepository extends Neo4jGenericRepository<Flow> implements FlowRepository {
    public Neo4jFlowRepository(Neo4jSessionFactory sessionFactory) {
        super(sessionFactory);
    }

    @Override
    public Iterable<Flow> findById(String flowId) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("flow_id", flowId);

        return getSession().query(Flow.class,
                "MATCH (src:switch)-[f:flow{flowid: {flow_id}}]->(dst:switch) RETURN f, src, dst", parameters);
    }

    @Override
    Class<Flow> getEntityType() {
        return Flow.class;
    }
}
