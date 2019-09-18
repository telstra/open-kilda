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

import org.openkilda.model.ConnectedDevice;
import org.openkilda.model.ConnectedDeviceType;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.ConnectedDeviceRepository;

import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;

import java.util.Collection;
import java.util.Optional;

public class Neo4jConnectedDevicesRepository
        extends Neo4jGenericRepository<ConnectedDevice> implements ConnectedDeviceRepository {

    private static final String FLOW_ID_PROPERTY_NAME = "flow_id";
    private static final String SOURCE_PROPERTY_NAME = "source";
    private static final String MAC_ADDRESS_PROPERTY_NAME = "mac_address";

    Neo4jConnectedDevicesRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
    }

    @Override
    public Collection<ConnectedDevice> findByFlowId(String flowId) {
        Filter flowIdFilter = new Filter(FLOW_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, flowId);
        return loadAll(flowIdFilter);
    }

    @Override
    public Optional<ConnectedDevice> findByFlowIdSourceMacAndType(String flowId, boolean source, String macAddress,
                                                                  ConnectedDeviceType type) {
        Filter flowIdFilter = new Filter(FLOW_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, flowId);
        Filter forwardFilter = new Filter(SOURCE_PROPERTY_NAME, ComparisonOperator.EQUALS, source);
        Filter macAddressFilter = new Filter(MAC_ADDRESS_PROPERTY_NAME, ComparisonOperator.EQUALS, macAddress);
        Collection<ConnectedDevice> devices = loadAll(flowIdFilter.and(forwardFilter).and(macAddressFilter));

        if (devices.size() > 1) {
            throw new PersistenceException(format("Found more that 1 Connected Device by flowId '%s', source '%s', "
                            + "mac address '%s' and type '%s'", flowId, source, macAddress, type));
        }
        return  devices.isEmpty() ? Optional.empty() : Optional.of(devices.iterator().next());
    }

    @Override
    public boolean exists(String flowId, String macAddress, boolean source) {
        Filter flowIdFilter = new Filter(FLOW_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, flowId);
        Filter forwardFilter = new Filter(SOURCE_PROPERTY_NAME, ComparisonOperator.EQUALS, source);
        Filter macAddressFilter = new Filter(MAC_ADDRESS_PROPERTY_NAME, ComparisonOperator.EQUALS, macAddress);

        return getSession().count(getEntityType(), flowIdFilter.and(forwardFilter).and(macAddressFilter)) > 0;
    }

    @Override
    protected Class<ConnectedDevice> getEntityType() {
        return ConnectedDevice.class;
    }
}
