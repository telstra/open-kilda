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
import static org.openkilda.model.PortProperties.PORT_NO_PROPERTY_NAME;
import static org.openkilda.persistence.repositories.impl.Neo4jSwitchRepository.SWITCH_NAME_PROPERTY_NAME;

import org.openkilda.model.PortProperties;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.PortPropertiesRepository;

import com.google.common.collect.Lists;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.cypher.Filters;

import java.util.Collection;
import java.util.Optional;

public class Neo4jPortPropertiesRepository extends Neo4jGenericRepository<PortProperties>
        implements PortPropertiesRepository {

    private static final String SWITCH_FIELD = "switchObj";

    public Neo4jPortPropertiesRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
    }

    @Override
    protected Class<PortProperties> getEntityType() {
        return PortProperties.class;
    }

    @Override
    public Optional<PortProperties> getBySwitchIdAndPort(SwitchId switchId, int port) {
        if (switchId == null) {
            throw new IllegalArgumentException("Switch id should be not null for PortProperties");
        }
        Filter switchFilter = new Filter(SWITCH_NAME_PROPERTY_NAME, ComparisonOperator.EQUALS, switchId.toString());
        switchFilter.setNestedPath(new Filter.NestedPathSegment(SWITCH_FIELD, Switch.class));
        Filters filters = new Filters();
        filters.and(switchFilter);
        filters.and(new Filter(PORT_NO_PROPERTY_NAME, ComparisonOperator.EQUALS, port));

        Collection<PortProperties> results = Lists.newArrayList(loadAll(filters));

        if (results.size() > 1) {
            throw new PersistenceException(format(
                    "Found more that 1 PortProperties entity by switch name '%s' and port '%d'", switchId, port));
        }
        return results.isEmpty() ? Optional.empty() : Optional.of(results.iterator().next());
    }

    @Override
    protected int getDepthCreateUpdateEntity() {
        return 1;
    }
}
