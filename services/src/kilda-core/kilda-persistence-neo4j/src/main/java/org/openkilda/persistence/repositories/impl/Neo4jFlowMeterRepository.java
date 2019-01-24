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

import org.openkilda.model.FlowMeter;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.FlowMeterRepository;

import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;

import java.util.Collection;
import java.util.Optional;

/**
 * Neo4J OGM implementation of {@link FlowMeterRepository}.
 */
public class Neo4jFlowMeterRepository extends Neo4jGenericRepository<FlowMeter> implements FlowMeterRepository {
    static final String SWITCH_ID_PROPERTY_NAME = "switch_id";
    static final String METER_ID_PROPERTY_NAME = "meter_id";
    static final String PATH_ID_PROPERTY_NAME = "path_id";

    public Neo4jFlowMeterRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
    }

    int getDepthCreateUpdateEntity() {
        // this depth allows to link the meter entity to a switch.
        return 1;
    }

    @Override
    public boolean exists(SwitchId switchId, MeterId meterId) {
        Filter switchIdFilter = new Filter(SWITCH_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, switchId);
        Filter meterIdFilter = new Filter(METER_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, meterId);

        return getSession().count(getEntityType(), switchIdFilter.and(meterIdFilter)) > 0;
    }

    @Override
    public Optional<FlowMeter> findById(SwitchId switchId, MeterId meterId) {
        Filter switchIdFilter = new Filter(SWITCH_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, switchId);
        Filter meterIdFilter = new Filter(METER_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, meterId);

        Collection<FlowMeter> meters = loadAll(switchIdFilter.and(meterIdFilter));
        if (meters.size() > 1) {
            throw new PersistenceException(format("Found more that 1 Meter entity by (%s, %s)", switchId, meterId));
        }
        return meters.isEmpty() ? Optional.empty() : Optional.of(meters.iterator().next());
    }

    @Override
    public Optional<FlowMeter> findByPathId(PathId pathId) {
        Filter pathIdFilter = new Filter(PATH_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, pathId);

        Collection<FlowMeter> meters = loadAll(pathIdFilter);
        if (meters.size() > 1) {
            throw new PersistenceException(format("Found more that 1 Meter entity by (%s)", pathId));
        }
        return meters.isEmpty() ? Optional.empty() : Optional.of(meters.iterator().next());
    }

    @Override
    Class<FlowMeter> getEntityType() {
        return FlowMeter.class;
    }
}
