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

import org.openkilda.model.ConnectedDevice;
import org.openkilda.persistence.Neo4jBasedTest;
import org.openkilda.persistence.repositories.ConnectedDeviceRepository;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public class Neo4jConnectedDeviceRepositoryTest extends Neo4jBasedTest {
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

    private ConnectedDevice connectedDeviceA;
    private ConnectedDevice connectedDeviceB;
    private ConnectedDevice connectedDeviceC;

    private static ConnectedDeviceRepository repository;

    @BeforeClass
    public static void setUp() {
        repository = new Neo4jConnectedDevicesRepository(neo4jSessionFactory, txManager);
    }

    @Before
    public void createDevices() {
        connectedDeviceA = new ConnectedDevice(
                FIRST_FLOW_ID, true, MAC_ADDRESS_1, LLDP, CHASSIS_ID, PORT_ID, TTL, PORT, SYSTEM_NAME,
                SYSTEM_DESCRIPTION, CAPABILITIES, MANAGEMENT_ADDRESS, TIME_FIRST_SEEN, TIME_LAST_SEEN);

        connectedDeviceB = new ConnectedDevice(
                SECOND_FLOW_ID, false, MAC_ADDRESS_1, LLDP, CHASSIS_ID, PORT_ID, TTL, PORT, SYSTEM_NAME,
                SYSTEM_DESCRIPTION, CAPABILITIES, MANAGEMENT_ADDRESS, TIME_FIRST_SEEN, TIME_LAST_SEEN);

        connectedDeviceC = new ConnectedDevice(
                SECOND_FLOW_ID, true, MAC_ADDRESS_2, ARP, CHASSIS_ID, PORT_ID, TTL, PORT, SYSTEM_NAME,
                SYSTEM_DESCRIPTION, CAPABILITIES, MANAGEMENT_ADDRESS, TIME_FIRST_SEEN, TIME_LAST_SEEN);
    }

    @Test
    public void createConnectedDeviceTest() {
        repository.createOrUpdate(connectedDeviceA);
        Collection<ConnectedDevice> devices = repository.findAll();
        assertEquals(connectedDeviceA, devices.iterator().next());
    }

    @Test
    public void existsConnectedDeviceTest() {
        repository.createOrUpdate(connectedDeviceA);

        assertTrue(repository.exists(FIRST_FLOW_ID, MAC_ADDRESS_1, true));
        assertFalse(repository.exists(FIRST_FLOW_ID, MAC_ADDRESS_1, false));
        assertFalse(repository.exists(FIRST_FLOW_ID, MAC_ADDRESS_2, true));
        assertFalse(repository.exists(SECOND_FLOW_ID, MAC_ADDRESS_1, true));
        assertFalse(repository.exists(SECOND_FLOW_ID, MAC_ADDRESS_2, false));
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
    public void findByFlowIdTest() {
        repository.createOrUpdate(connectedDeviceA);
        repository.createOrUpdate(connectedDeviceB);
        repository.createOrUpdate(connectedDeviceC);

        Collection<ConnectedDevice> firstFlowDevices = repository.findByFlowId(FIRST_FLOW_ID);
        assertEquals(1, firstFlowDevices.size());
        assertEquals(connectedDeviceA, firstFlowDevices.iterator().next());

        Collection<ConnectedDevice> secondFlowDevices = repository.findByFlowId(SECOND_FLOW_ID);
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
                "fake", false, "fake", LLDP, CHASSIS_ID, PORT_ID).isPresent());
    }

    private void runFindByUniqueFields(ConnectedDevice device) {
        Optional<ConnectedDevice> foundDevice = repository.findByUniqueFieldCombination(
                device.getFlowId(), device.isSource(), device.getMacAddress(), device.getType(),
                device.getChassisId(), device.getPortId());

        assertTrue(foundDevice.isPresent());
        assertEquals(device, foundDevice.get());
    }

    @Test
    public void uniqueIndexTest() {
        ConnectedDevice createdDevice = validateIndexAndUpdate(connectedDeviceA);

        createdDevice.setFlowId("some_flow");
        ConnectedDevice updatedFlowDevice = validateIndexAndUpdate(createdDevice);

        updatedFlowDevice.setMacAddress(MAC_ADDRESS_2);
        ConnectedDevice updatedMacDevice = validateIndexAndUpdate(updatedFlowDevice);

        updatedMacDevice.setSource(false);
        ConnectedDevice updatedSource = validateIndexAndUpdate(updatedMacDevice);

        updatedSource.setType(ARP);
        ConnectedDevice updatedType = validateIndexAndUpdate(updatedSource);

        updatedType.setChassisId("chas_id_2");
        ConnectedDevice updatedChassis = validateIndexAndUpdate(updatedType);

        updatedChassis.setPortId("new_port");
        validateIndexAndUpdate(updatedChassis);
    }

    private ConnectedDevice validateIndexAndUpdate(ConnectedDevice device) {
        String expectedIndex = format("%s_%s_%s_%s_%s_%s", device.getFlowId(),
                device.isSource() ? "source" : "destination", device.getMacAddress(), device.getType(),
                device.getChassisId(), device.getPortId());

        // chack that index was updated after call of set***() method or after constructing
        assertEquals(expectedIndex, device.getUniqueIndex());
        repository.createOrUpdate(device);
        ConnectedDevice updatedDevice = repository.findAll().iterator().next();
        assertEquals(expectedIndex, updatedDevice.getUniqueIndex());
        return updatedDevice;
    }
}
