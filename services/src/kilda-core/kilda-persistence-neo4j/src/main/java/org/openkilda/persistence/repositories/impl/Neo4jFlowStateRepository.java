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

import org.openkilda.model.history.FlowDump;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.history.FlowStateRepository;

import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;

import java.util.Collection;

public class Neo4jFlowStateRepository extends Neo4jGenericRepository<FlowDump> implements FlowStateRepository {
    private static final String TASK_ID_PROPERTY_NAME = "task_id";

    Neo4jFlowStateRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
    }

    @Override
    public Collection<FlowDump> listFlowDumpByTaskId(String taskId) {
        Filter taskIdFilter = new Filter(TASK_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, taskId);
        return getSession().loadAll(getEntityType(), taskIdFilter, DEPTH_LOAD_ENTITY);
    }

    @Override
    Class<FlowDump> getEntityType() {
        return FlowDump.class;
    }
}
