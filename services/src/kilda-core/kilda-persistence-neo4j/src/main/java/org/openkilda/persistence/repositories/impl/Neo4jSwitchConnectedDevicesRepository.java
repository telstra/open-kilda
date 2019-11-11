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

import org.openkilda.model.ConnectedDeviceType;
import org.openkilda.model.SwitchConnectedDevice;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.SwitchConnectedDeviceRepository;

import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;

import java.util.Collection;
import java.util.Optional;

public class Neo4jSwitchConnectedDevicesRepository
        extends Neo4jGenericRepository<SwitchConnectedDevice> implements SwitchConnectedDeviceRepository {

    private static final String SWITCH_ID_PROPERTY_NAME = "switch_id";
    private static final String PORT_NUMBER_PROPERTY_NAME = "port_number";
    private static final String VLAN_PROPERTY_NAME = "vlan";
    private static final String TYPE_PROPERTY_NAME = "type";
    private static final String MAC_ADDRESS_PROPERTY_NAME = "mac_address";
    private static final String CHASSIS_ID_PROPERTY_NAME = "chassis_id";
    private static final String PORT_ID_PROPERTY_NAME = "port_id";

    Neo4jSwitchConnectedDevicesRepository(Neo4jSessionFactory sessionFactory, TransactionManager transactionManager) {
        super(sessionFactory, transactionManager);
    }

    @Override
    public Collection<SwitchConnectedDevice> findBySwitchId(SwitchId switchId) {
        Filter switchIdFilter = new Filter(SWITCH_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, switchId);
        return loadAll(switchIdFilter);
    }

    @Override
    public Optional<SwitchConnectedDevice> findByUniqueFieldCombination(
            SwitchId switchId, int portNumber, int vlan, String macAddress, ConnectedDeviceType type, String chassisId,
            String portId) {
        Filter switchIdFilter = new Filter(SWITCH_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, switchId);
        Filter portNumberFilter = new Filter(PORT_NUMBER_PROPERTY_NAME, ComparisonOperator.EQUALS, portNumber);
        Filter vlanFilter = new Filter(VLAN_PROPERTY_NAME, ComparisonOperator.EQUALS, vlan);
        Filter macAddressFilter = new Filter(MAC_ADDRESS_PROPERTY_NAME, ComparisonOperator.EQUALS, macAddress);
        Filter typeFilter = new Filter(TYPE_PROPERTY_NAME, ComparisonOperator.EQUALS, type);
        Filter chassisIdFilter = new Filter(CHASSIS_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, chassisId);
        Filter portIdFilter = new Filter(PORT_ID_PROPERTY_NAME, ComparisonOperator.EQUALS, portId);
        Collection<SwitchConnectedDevice> devices = loadAll(
                switchIdFilter.and(portNumberFilter).and(vlanFilter).and(macAddressFilter).and(typeFilter)
                        .and(chassisIdFilter).and(portIdFilter));

        if (devices.size() > 1) {
            throw new PersistenceException(format("Found more that 1 Connected Device by switch ID '%s', port number "
                            + "'%d', vlan '%d', mac address '%s', type '%s', chassis ID '%s' and port ID, '%s'",
                    switchId, portNumber, vlan, macAddress, type, chassisId, portId));
        }
        return  devices.isEmpty() ? Optional.empty() : Optional.of(devices.iterator().next());
    }

    @Override
    protected Class<SwitchConnectedDevice> getEntityType() {
        return SwitchConnectedDevice.class;
    }
}
