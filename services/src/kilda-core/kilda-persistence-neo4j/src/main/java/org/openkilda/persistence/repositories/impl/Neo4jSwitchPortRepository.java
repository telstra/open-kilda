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

import org.openkilda.model.Port;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowMeterRepository;
import org.openkilda.persistence.repositories.SwitchPortRepository;

import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;

import java.util.Collection;
import java.util.Optional;

/**
 * Neo4J OGM implementation of {@link FlowMeterRepository}.
 */
public class Neo4jSwitchPortRepository extends Neo4jGenericRepository<Port> implements SwitchPortRepository {
    static final String SWITCH_ID_PROPERTY_NAME = "switch_id";
    static final String PORT_NO_PROPERTY_NAME = "port_no";

    public Neo4jSwitchPortRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
    }

    int getDepthCreateUpdateEntity() {
        // this depth allows to link the port entity to a switch.
        return 1;
    }

    @Override
    public boolean exists(SwitchId switchId, int port) {
        Filter switchIdFilter = new Filter(SWITCH_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, switchId);
        Filter portFilter = new Filter(PORT_NO_PROPERTY_NAME, ComparisonOperator.EQUALS, port);

        return getSession().count(getEntityType(), switchIdFilter.and(portFilter)) > 0;
    }

    @Override
    public Optional<Port> findById(SwitchId switchId, int port) {
        Filter switchIdFilter = new Filter(SWITCH_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, switchId);
        Filter portFilter = new Filter(PORT_NO_PROPERTY_NAME, ComparisonOperator.EQUALS, port);

        Collection<Port> ports = loadAll(switchIdFilter.and(portFilter));
        if (ports.size() > 1) {
            throw new PersistenceException(format("Found more that 1 Port entity by (%s, %d)", switchId, port));
        }
        return ports.isEmpty() ? Optional.empty() : Optional.of(ports.iterator().next());
    }

    @Override
    Class<Port> getEntityType() {
        return Port.class;
    }
}
