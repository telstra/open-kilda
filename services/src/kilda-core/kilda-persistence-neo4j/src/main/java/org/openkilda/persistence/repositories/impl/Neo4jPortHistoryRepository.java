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

import org.openkilda.model.SwitchId;
import org.openkilda.model.history.PortHistory;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.history.PortHistoryRepository;

import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.cypher.Filters;
import org.neo4j.ogm.cypher.query.SortOrder;

import java.util.Collection;

public class Neo4jPortHistoryRepository extends Neo4jGenericRepository<PortHistory> implements PortHistoryRepository {

    private static final String SWITCH_ID_PROPERTY_NAME = "switch_id";
    private static final String PORT_NUMBER_PROPERTY_NAME = "port_no";
    private static final String BOUNCING_STARTED_PROPERTY_NAME = "bouncing_started";

    public Neo4jPortHistoryRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
    }

    @Override
    public Collection<PortHistory> findBySwitchIdAndPortNumber(SwitchId switchId, int portNumber) {
        Filters filters = new Filters();
        filters.and(new Filter(SWITCH_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, switchId));
        filters.and(new Filter(PORT_NUMBER_PROPERTY_NAME, ComparisonOperator.EQUALS, portNumber));

        return getSession().loadAll(getEntityType(), filters, new SortOrder(BOUNCING_STARTED_PROPERTY_NAME));
    }

    @Override
    protected Class<PortHistory> getEntityType() {
        return PortHistory.class;
    }
}
