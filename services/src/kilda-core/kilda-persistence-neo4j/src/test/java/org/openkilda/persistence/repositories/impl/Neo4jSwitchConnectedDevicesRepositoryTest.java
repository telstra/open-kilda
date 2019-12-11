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

import static com.google.common.collect.Sets.newHashSet;
import static java.lang.String.format;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.openkilda.model.ConnectedDeviceType.ARP;
import static org.openkilda.model.ConnectedDeviceType.LLDP;

import org.openkilda.model.Switch;
import org.openkilda.model.SwitchConnectedDevice;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.Neo4jBasedTest;
import org.openkilda.persistence.repositories.SwitchConnectedDeviceRepository;
import org.openkilda.persistence.repositories.SwitchRepository;

import org.junit.Before;
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

    private Switch firstSwitch = Switch.builder().switchId(FIRST_SWITCH_ID).build();
    private Switch secondSwitch = Switch.builder().switchId(SECOND_SWITCH_ID).build();

    private SwitchConnectedDevice connectedDeviceA = new SwitchConnectedDevice(
            firstSwitch, FIRST_PORT_NUMBER, FIRST_VLAN, FIRST_FLOW_ID, MAC_ADDRESS_1, LLDP, CHASSIS_ID, PORT_ID,
            TTL, PORT, SYSTEM_NAME, SYSTEM_DESCRIPTION, CAPABILITIES, MANAGEMENT_ADDRESS, TIME_FIRST_SEEN,
            TIME_LAST_SEEN);
    private SwitchConnectedDevice connectedDeviceB = new SwitchConnectedDevice(
            secondSwitch, FIRST_PORT_NUMBER, FIRST_VLAN, SECOND_FLOW_ID, MAC_ADDRESS_1, LLDP, CHASSIS_ID,
            PORT_ID, TTL, PORT, SYSTEM_NAME, SYSTEM_DESCRIPTION, CAPABILITIES, MANAGEMENT_ADDRESS, TIME_FIRST_SEEN,
            TIME_LAST_SEEN);
    private SwitchConnectedDevice connectedDeviceC = new SwitchConnectedDevice(
            secondSwitch, SECOND_PORT_NUMBER, SECOND_VLAN, null, MAC_ADDRESS_2, ARP, CHASSIS_ID, PORT_ID, TTL,
            PORT, SYSTEM_NAME, SYSTEM_DESCRIPTION, CAPABILITIES, MANAGEMENT_ADDRESS, TIME_FIRST_SEEN,
            TIME_LAST_SEEN);

    private static SwitchRepository switchRepository;
    private static SwitchConnectedDeviceRepository connectedDeviceRepository;

    @BeforeClass
    public static void setUpOnes() {
        switchRepository = new Neo4jSwitchRepository(neo4jSessionFactory, txManager);
        connectedDeviceRepository = new Neo4jSwitchConnectedDevicesRepository(neo4jSessionFactory, txManager);
    }

    @Before
    public void setUp() {
        switchRepository.createOrUpdate(firstSwitch);
        switchRepository.createOrUpdate(secondSwitch);
    }

    @Test
    public void createConnectedDeviceTest() {
        connectedDeviceRepository.createOrUpdate(connectedDeviceA);
        Collection<SwitchConnectedDevice> devices = connectedDeviceRepository.findAll();
        assertEquals(connectedDeviceA, devices.iterator().next());
        assertNotNull(devices.iterator().next().getSwitchObj());
    }

    @Test
    public void deleteConnectedDeviceTest() {
        connectedDeviceRepository.createOrUpdate(connectedDeviceA);
        connectedDeviceRepository.createOrUpdate(connectedDeviceB);
        assertEquals(2, connectedDeviceRepository.findAll().size());

        connectedDeviceRepository.delete(connectedDeviceA);
        assertEquals(1, connectedDeviceRepository.findAll().size());
        assertEquals(connectedDeviceB, connectedDeviceRepository.findAll().iterator().next());

        connectedDeviceRepository.delete(connectedDeviceB);
        assertEquals(0, connectedDeviceRepository.findAll().size());
    }

    @Test
    public void findBySwitchIdTest() {
        connectedDeviceRepository.createOrUpdate(connectedDeviceA);
        connectedDeviceRepository.createOrUpdate(connectedDeviceB);
        connectedDeviceRepository.createOrUpdate(connectedDeviceC);

        Collection<SwitchConnectedDevice> firstSwitchDevices = connectedDeviceRepository
                .findBySwitchId(FIRST_SWITCH_ID);
        assertEquals(1, firstSwitchDevices.size());
        assertEquals(connectedDeviceA, firstSwitchDevices.iterator().next());

        Collection<SwitchConnectedDevice> secondFlowDevices = connectedDeviceRepository
                .findBySwitchId(SECOND_SWITCH_ID);
        assertEquals(2, secondFlowDevices.size());
        assertEquals(newHashSet(connectedDeviceB, connectedDeviceC), newHashSet(secondFlowDevices));
    }

    @Test
    public void findByUniqueFields() {
        connectedDeviceRepository.createOrUpdate(connectedDeviceA);
        connectedDeviceRepository.createOrUpdate(connectedDeviceB);
        connectedDeviceRepository.createOrUpdate(connectedDeviceC);

        runFindByUniqueFields(connectedDeviceA);
        runFindByUniqueFields(connectedDeviceB);
        runFindByUniqueFields(connectedDeviceC);

        assertFalse(connectedDeviceRepository.findByUniqueFieldCombination(
                new SwitchId("999"), 999, 999, "fake", LLDP, CHASSIS_ID, PORT_ID).isPresent());
    }

    private void runFindByUniqueFields(SwitchConnectedDevice device) {
        Optional<SwitchConnectedDevice> foundDevice = connectedDeviceRepository.findByUniqueFieldCombination(
                device.getSwitchObj().getSwitchId(), device.getPortNumber(), device.getVlan(),  device.getMacAddress(),
                device.getType(), device.getChassisId(), device.getPortId());

        assertTrue(foundDevice.isPresent());
        assertEquals(device, foundDevice.get());
    }

    @Test
    public void uniqueIndexTest() {
        SwitchConnectedDevice createdDevice = validateIndexAndUpdate(connectedDeviceA);

        createdDevice.setSwitch(secondSwitch);
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
        String expectedIndex = format("%s_%s_%s_%s_%s_%s_%s", device.getSwitchObj().getSwitchId(),
                device.getPortNumber(), device.getVlan(), device.getMacAddress(), device.getType(),
                device.getChassisId(), device.getPortId());

        // chack that index was updated after call of set***() method or after constructing
        assertEquals(expectedIndex, device.getUniqueIndex());
        connectedDeviceRepository.createOrUpdate(device);
        SwitchConnectedDevice updatedDevice = connectedDeviceRepository.findAll().iterator().next();
        assertEquals(expectedIndex, updatedDevice.getUniqueIndex());
        return updatedDevice;
    }
}
