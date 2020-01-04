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
import static org.openkilda.persistence.repositories.impl.Neo4jSwitchRepository.SWITCH_NAME_PROPERTY_NAME;

import org.openkilda.model.PortProperties;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.PortPropertiesRepository;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.cypher.Filters;

import java.util.Collection;
import java.util.Optional;

@Slf4j
public class Neo4jPortPropertiesRepository extends Neo4jGenericRepository<PortProperties>
        implements PortPropertiesRepository {

    private static final String SWITCH_FIELD = "switchObj";
    private static final String PORT_FIELD = "port_no";

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
            throw new PersistenceException("Switch id should not be null");
        }
        Filter switchFilter = new Filter(SWITCH_NAME_PROPERTY_NAME, ComparisonOperator.EQUALS, switchId.toString());
        switchFilter.setNestedPath(new Filter.NestedPathSegment(SWITCH_FIELD, Switch.class));
        Filter portFilter = new Filter(PORT_FIELD, ComparisonOperator.EQUALS, port);
        Filters filters = switchFilter.and(portFilter);

        Collection<PortProperties> portProperties = loadAll(filters);

        if (portProperties.size() > 1) {
            throw new PersistenceException(
                    format("Found more than one PortProperties by switch id '%s' and port '%d'", switchId, port));
        }
        return portProperties.stream().findFirst();
    }

    @Override
    public Collection<PortProperties> getAllBySwitchId(SwitchId switchId) {
        if (switchId == null) {
            throw new PersistenceException("Switch id should not be null");
        }
        Filter switchFilter = new Filter(SWITCH_NAME_PROPERTY_NAME, ComparisonOperator.EQUALS, switchId.toString());
        switchFilter.setNestedPath(new Filter.NestedPathSegment(SWITCH_FIELD, Switch.class));

        return loadAll(switchFilter);
    }

    @Override
    protected int getDepthCreateUpdateEntity() {
        return 1;
    }
}
