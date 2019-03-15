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

import org.openkilda.model.history.FlowEvent;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.history.FlowEventRepository;

import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.cypher.Filters;
import org.neo4j.ogm.cypher.query.SortOrder;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public class Neo4jFlowEventRepository extends Neo4jGenericRepository<FlowEvent> implements FlowEventRepository {
    private static final String TASK_ID_PROPERTY_NAME = "task_id";
    private static final String FLOW_ID_PROPERTY_NAME = "flow_id";
    private static final String TIMESTAMP_PROPERTY_NAME = "timestamp";

    Neo4jFlowEventRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
    }

    @Override
    Class<FlowEvent> getEntityType() {
        return FlowEvent.class;
    }

    @Override
    public Optional<FlowEvent> findByTaskId(String taskId) {
        Filter taskIdFilter = new Filter(TASK_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, taskId);
        Collection<FlowEvent> flowEvents = getSession().loadAll(getEntityType(), taskIdFilter, DEPTH_LOAD_ENTITY);
        if (flowEvents.size() > 1) {
            throw new PersistenceException(format("Found more than 1 FlowEvent entity by %s as taskId", taskId));
        }
        return flowEvents.isEmpty() ? Optional.empty() : Optional.of(flowEvents.iterator().next());
    }

    @Override
    public Collection<FlowEvent> findByFlowIdAndTimeFrame(String flowId, Instant timeFrom, Instant timeTo) {
        Filter flowIdFilter = new Filter(FLOW_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, flowId);
        Filter beforeFilter = new Filter(TIMESTAMP_PROPERTY_NAME, ComparisonOperator.LESS_THAN_EQUAL, timeTo);
        Filter afterFilter = new Filter(TIMESTAMP_PROPERTY_NAME, ComparisonOperator.GREATER_THAN_EQUAL, timeFrom);
        Filters filters = new Filters(flowIdFilter).and(beforeFilter).and(afterFilter);
        return getSession()
                .loadAll(getEntityType(), filters, new SortOrder(TIMESTAMP_PROPERTY_NAME), DEPTH_LOAD_ENTITY);
    }
}
