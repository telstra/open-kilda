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

import org.openkilda.model.FlowSegment;
import org.openkilda.persistence.neo4j.Neo4jSessionFactory;
import org.openkilda.persistence.repositories.FlowSegmentRepository;

import java.util.HashMap;
import java.util.Map;

/**
 * Neo4J OGM implementation of {@link FlowSegmentRepository}.
 */
public class FlowSegmentRepositoryImpl extends GenericRepository<FlowSegment> implements FlowSegmentRepository {
    public FlowSegmentRepositoryImpl(Neo4jSessionFactory sessionFactory) {
        super(sessionFactory);
    }

    @Override
    public Iterable<FlowSegment> findByFlowId(String flowId) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("flow_id", flowId);

        return getSession().query(FlowSegment.class,
                "MATCH (src:switch)-[fs:flow_segment{flowid: {flow_id}}]->(dst:switch) RETURN fs, src, dst",
                parameters);
    }

    @Override
    Class<FlowSegment> getEntityType() {
        return FlowSegment.class;
    }
}
