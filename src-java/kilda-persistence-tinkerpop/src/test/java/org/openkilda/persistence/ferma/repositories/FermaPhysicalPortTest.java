/* Copyright 2021 Telstra Open Source
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

package org.openkilda.persistence.ferma.repositories;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.openkilda.model.LagLogicalPort;
import org.openkilda.model.PhysicalPort;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.LagLogicalPortRepository;
import org.openkilda.persistence.repositories.PhysicalPortRepository;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class FermaPhysicalPortTest extends InMemoryGraphBasedTest {
    static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    static final SwitchId SWITCH_ID_3 = new SwitchId(3);
    static final int LOGICAL_PORT_NUMBER_1 = 1;
    static final int LOGICAL_PORT_NUMBER_2 = 2;
    static final int LOGICAL_PORT_NUMBER_3 = 2;
    static final int PHYSICAL_PORT_NUMBER_1 = 4;
    static final int PHYSICAL_PORT_NUMBER_2 = 5;
    static final int PHYSICAL_PORT_NUMBER_3 = 6;

    LagLogicalPort logicalPort1;
    LagLogicalPort logicalPort2;
    LagLogicalPort logicalPort3;

    LagLogicalPortRepository lagLogicalPortRepository;
    PhysicalPortRepository physicalPortRepository;

    @Before
    public void setUp() {
        lagLogicalPortRepository = repositoryFactory.createLagLogicalPortRepository();
        physicalPortRepository = repositoryFactory.createPhysicalPortRepository();

        logicalPort1 = createLogicalPort(SWITCH_ID_1, LOGICAL_PORT_NUMBER_1);
        logicalPort2 = createLogicalPort(SWITCH_ID_1, LOGICAL_PORT_NUMBER_2);
        logicalPort3 = createLogicalPort(SWITCH_ID_2, LOGICAL_PORT_NUMBER_3);
    }

    @Test
    public void createPhysicalTest() {
        createPhysicalPort(SWITCH_ID_1, PHYSICAL_PORT_NUMBER_1, logicalPort1);
        createPhysicalPort(SWITCH_ID_1, PHYSICAL_PORT_NUMBER_2, logicalPort1);
        createPhysicalPort(SWITCH_ID_2, PHYSICAL_PORT_NUMBER_3, logicalPort3);

        List<PhysicalPort> ports = new ArrayList<>(physicalPortRepository.findAll());

        assertEquals(3, ports.size());
        ports.sort(Comparator.comparingInt(PhysicalPort::getPortNumber));

        Assert.assertEquals(PHYSICAL_PORT_NUMBER_1, ports.get(0).getPortNumber());
        Assert.assertEquals(PHYSICAL_PORT_NUMBER_2, ports.get(1).getPortNumber());
        Assert.assertEquals(PHYSICAL_PORT_NUMBER_3, ports.get(2).getPortNumber());

        Assert.assertEquals(SWITCH_ID_1, ports.get(0).getSwitchId());
        Assert.assertEquals(SWITCH_ID_1, ports.get(1).getSwitchId());
        Assert.assertEquals(SWITCH_ID_2, ports.get(2).getSwitchId());

        assertNull(ports.get(0).getLagLogicalPort());
        assertNull(ports.get(1).getLagLogicalPort());
        assertNull(ports.get(2).getLagLogicalPort());
    }

    @Test
    public void removePhysicalTest() {
        PhysicalPort physicalPort1 = createPhysicalPort(SWITCH_ID_1, PHYSICAL_PORT_NUMBER_1, logicalPort1);
        PhysicalPort physicalPort2 = createPhysicalPort(SWITCH_ID_1, PHYSICAL_PORT_NUMBER_2, logicalPort1);

        assertEquals(2, physicalPortRepository.findAll().size());
        transactionManager.doInTransaction(() -> physicalPortRepository.remove(physicalPort1));

        List<PhysicalPort> ports = new ArrayList<>(physicalPortRepository.findAll());
        assertEquals(1, ports.size());
        assertEquals(PHYSICAL_PORT_NUMBER_2, ports.get(0).getPortNumber());

        transactionManager.doInTransaction(() -> physicalPortRepository.remove(physicalPort2));
        assertEquals(0, physicalPortRepository.findAll().size());
    }

    @Test
    public void findPortNumbersBySwitchIdTest() {
        createPhysicalPort(SWITCH_ID_1, PHYSICAL_PORT_NUMBER_1, logicalPort1);
        createPhysicalPort(SWITCH_ID_1, PHYSICAL_PORT_NUMBER_2, logicalPort1);
        createPhysicalPort(SWITCH_ID_2, PHYSICAL_PORT_NUMBER_3, logicalPort3);

        Set<Integer> portNumbers = physicalPortRepository.findPortNumbersBySwitchId(SWITCH_ID_1);
        assertEquals(2, portNumbers.size());
        assertTrue(portNumbers.contains(PHYSICAL_PORT_NUMBER_1));
        assertTrue(portNumbers.contains(PHYSICAL_PORT_NUMBER_2));

        assertTrue(physicalPortRepository.findPortNumbersBySwitchId(SWITCH_ID_3).isEmpty());
    }

    private LagLogicalPort createLogicalPort(SwitchId switchId, int logicalPortNumber) {
        LagLogicalPort port = new LagLogicalPort(switchId, logicalPortNumber);
        lagLogicalPortRepository.add(port);
        return port;
    }

    private PhysicalPort createPhysicalPort(SwitchId switchId, int physicalPortNumber, LagLogicalPort logicalPort) {
        PhysicalPort port = new PhysicalPort(switchId, physicalPortNumber, logicalPort);
        physicalPortRepository.add(port);
        return port;
    }
}
