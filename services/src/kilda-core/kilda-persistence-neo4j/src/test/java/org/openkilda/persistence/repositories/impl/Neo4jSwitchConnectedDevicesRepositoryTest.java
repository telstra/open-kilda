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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.openkilda.model.ConnectedDeviceType.ARP;
import static org.openkilda.model.ConnectedDeviceType.LLDP;

import org.openkilda.model.SwitchConnectedDevice;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.Neo4jBasedTest;
import org.openkilda.persistence.repositories.SwitchConnectedDeviceRepository;

import com.google.common.collect.Lists;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public class Neo4jSwitchConnectedDevicesRepositoryTest extends Neo4jBasedTest {
    private static final SwitchId FIRST_SWITCH_ID = new SwitchId("01");
    private static final SwitchId SECOND_SWITCH_ID = new SwitchId("02");
    private static final int FIRST_PORT_NUMBER = 1;
    private static final int SECOND_PORT_NUMBER = 2;
    private static final int FIRST_VLAN = 1;
    private static final int SECOND_VLAN = 2;
    private static final String FIRST_FLOW_ID = "first_flow";
    private static final String SECOND_FLOW_ID = "second_flow";
    private static final String MAC_ADDRESS_1 = "00:00:00:00:00:00:00:01";
    private static final String MAC_ADDRESS_2 = "00:00:00:00:00:00:00:02";
    private static final String CHASSIS_ID = "00:00:00:00:00:00:00:03";
    private static final String PORT_ID = "123";
    private static final int TTL = 120;
    private static final String PORT = "some_port";
    private static final String SYSTEM_NAME = "ubuntu";
    private static final String SYSTEM_DESCRIPTION = "desc";
    private static final String CAPABILITIES = "capabilities";
    private static final String MANAGEMENT_ADDRESS = "127.0.0.1";
    private static final Instant TIME_FIRST_SEEN = Instant.now().minusSeconds(10);
    private static final Instant TIME_LAST_SEEN = Instant.now();

    private SwitchConnectedDevice connectedDeviceA = new SwitchConnectedDevice(
            FIRST_SWITCH_ID, FIRST_PORT_NUMBER, FIRST_VLAN, FIRST_FLOW_ID, MAC_ADDRESS_1, LLDP, CHASSIS_ID, PORT_ID,
            TTL, PORT, SYSTEM_NAME, SYSTEM_DESCRIPTION, CAPABILITIES, MANAGEMENT_ADDRESS, TIME_FIRST_SEEN,
            TIME_LAST_SEEN);
    private SwitchConnectedDevice connectedDeviceB = new SwitchConnectedDevice(
            SECOND_SWITCH_ID, FIRST_PORT_NUMBER, FIRST_VLAN, SECOND_FLOW_ID, MAC_ADDRESS_1, LLDP, CHASSIS_ID,
            PORT_ID, TTL, PORT, SYSTEM_NAME, SYSTEM_DESCRIPTION, CAPABILITIES, MANAGEMENT_ADDRESS, TIME_FIRST_SEEN,
            TIME_LAST_SEEN);
    private SwitchConnectedDevice connectedDeviceC = new SwitchConnectedDevice(
            SECOND_SWITCH_ID, SECOND_PORT_NUMBER, SECOND_VLAN, null, MAC_ADDRESS_2, ARP, CHASSIS_ID, PORT_ID, TTL,
            PORT, SYSTEM_NAME, SYSTEM_DESCRIPTION, CAPABILITIES, MANAGEMENT_ADDRESS, TIME_FIRST_SEEN,
            TIME_LAST_SEEN);

    private static SwitchConnectedDeviceRepository repository;

    @BeforeClass
    public static void setUp() {
        repository = new Neo4jSwitchConnectedDevicesRepository(neo4jSessionFactory, txManager);
    }

    @Test
    public void createConnectedDeviceTest() {
        repository.createOrUpdate(connectedDeviceA);
        Collection<SwitchConnectedDevice> devices = repository.findAll();
        assertEquals(connectedDeviceA, devices.iterator().next());
    }

    @Test
    public void deleteConnectedDeviceTest() {
        repository.createOrUpdate(connectedDeviceA);
        repository.createOrUpdate(connectedDeviceB);
        assertEquals(2, repository.findAll().size());

        repository.delete(connectedDeviceA);
        assertEquals(1, repository.findAll().size());
        assertEquals(connectedDeviceB, repository.findAll().iterator().next());

        repository.delete(connectedDeviceB);
        assertEquals(0, repository.findAll().size());
    }

    @Test
    public void findBySwitchIdTest() {
        repository.createOrUpdate(connectedDeviceA);
        repository.createOrUpdate(connectedDeviceB);
        repository.createOrUpdate(connectedDeviceC);

        Collection<SwitchConnectedDevice> firstSwitchDevices = repository.findBySwitchId(FIRST_SWITCH_ID);
        assertEquals(1, firstSwitchDevices.size());
        assertEquals(connectedDeviceA, firstSwitchDevices.iterator().next());

        Collection<SwitchConnectedDevice> secondFlowDevices = repository.findBySwitchId(SECOND_SWITCH_ID);
        assertEquals(2, secondFlowDevices.size());
        assertEquals(Lists.newArrayList(connectedDeviceB, connectedDeviceC), secondFlowDevices);
    }

    @Test
    public void findByUniqueFields() {
        repository.createOrUpdate(connectedDeviceA);
        repository.createOrUpdate(connectedDeviceB);
        repository.createOrUpdate(connectedDeviceC);

        runFindByUniqueFields(connectedDeviceA);
        runFindByUniqueFields(connectedDeviceB);
        runFindByUniqueFields(connectedDeviceC);

        assertFalse(repository.findByUniqueFieldCombination(
                new SwitchId("999"), 999, 999, "fake", LLDP, CHASSIS_ID, PORT_ID).isPresent());
    }

    private void runFindByUniqueFields(SwitchConnectedDevice device) {
        Optional<SwitchConnectedDevice> foundDevice = repository.findByUniqueFieldCombination(device.getSwitchId(),
                device.getPortNumber(), device.getVlan(),  device.getMacAddress(), device.getType(),
                device.getChassisId(), device.getPortId());

        assertTrue(foundDevice.isPresent());
        assertEquals(device, foundDevice.get());
    }

    @Test
    public void uniqueIndexTest() {
        SwitchConnectedDevice createdDevice = validateIndexAndUpdate(connectedDeviceA);

        createdDevice.setSwitchId(SECOND_SWITCH_ID);
        SwitchConnectedDevice updatedSwitchDevice = validateIndexAndUpdate(createdDevice);

        updatedSwitchDevice.setPortNumber(SECOND_PORT_NUMBER);
        SwitchConnectedDevice updatedPortDevice = validateIndexAndUpdate(updatedSwitchDevice);

        updatedPortDevice.setVlan(SECOND_VLAN);
        SwitchConnectedDevice updatedVlanDevice = validateIndexAndUpdate(updatedPortDevice);

        updatedVlanDevice.setMacAddress(MAC_ADDRESS_2);
        SwitchConnectedDevice updatedMacDevice = validateIndexAndUpdate(updatedVlanDevice);

        updatedMacDevice.setType(ARP);
        SwitchConnectedDevice updatedType = validateIndexAndUpdate(updatedMacDevice);

        updatedType.setChassisId("chas_id_2");
        SwitchConnectedDevice updatedChassis = validateIndexAndUpdate(updatedType);

        updatedChassis.setPortId("new_port");
        validateIndexAndUpdate(updatedChassis);
    }

    private SwitchConnectedDevice validateIndexAndUpdate(SwitchConnectedDevice device) {
        String expectedIndex = format("%s_%s_%s_%s_%s_%s_%s", device.getSwitchId(), device.getPortNumber(),
                device.getVlan(), device.getMacAddress(), device.getType(), device.getChassisId(), device.getPortId());

        // chack that index was updated after call of set***() method or after constructing
        assertEquals(expectedIndex, device.getUniqueIndex());
        repository.createOrUpdate(device);
        SwitchConnectedDevice updatedDevice = repository.findAll().iterator().next();
        assertEquals(expectedIndex, updatedDevice.getUniqueIndex());
        return updatedDevice;
    }
}
