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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.openkilda.model.LagLogicalPort;
import org.openkilda.model.PhysicalPort;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.LagLogicalPortRepository;
import org.openkilda.persistence.repositories.PhysicalPortRepository;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class FermaLagLogicalPortTest extends InMemoryGraphBasedTest {
    static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    static final SwitchId SWITCH_ID_3 = new SwitchId(3);
    static final int LOGICAL_PORT_NUMBER_1 = 1;
    static final int LOGICAL_PORT_NUMBER_2 = 2;
    static final int LOGICAL_PORT_NUMBER_3 = 2;
    static final int PHYSICAL_PORT_NUMBER_1 = 4;
    static final int PHYSICAL_PORT_NUMBER_2 = 5;

    LagLogicalPortRepository lagLogicalPortRepository;
    PhysicalPortRepository physicalPortRepository;

    @Before
    public void setUp() {
        lagLogicalPortRepository = repositoryFactory.createLagLogicalPortRepository();
        physicalPortRepository = repositoryFactory.createPhysicalPortRepository();
    }

    @Test
    public void createLogicalPortWithoutPhysicalPortsTest() {
        createLogicalPort(SWITCH_ID_1, LOGICAL_PORT_NUMBER_1);
        createLogicalPort(SWITCH_ID_1, LOGICAL_PORT_NUMBER_2);
        createLogicalPort(SWITCH_ID_2, LOGICAL_PORT_NUMBER_3);

        List<LagLogicalPort> ports = new ArrayList<>(lagLogicalPortRepository.findAll());

        assertEquals(3, ports.size());
        ports.sort(Comparator.comparingInt(LagLogicalPort::getLogicalPortNumber));

        assertEquals(LOGICAL_PORT_NUMBER_1, ports.get(0).getLogicalPortNumber());
        assertEquals(LOGICAL_PORT_NUMBER_2, ports.get(1).getLogicalPortNumber());
        assertEquals(LOGICAL_PORT_NUMBER_3, ports.get(2).getLogicalPortNumber());

        assertEquals(SWITCH_ID_1, ports.get(0).getSwitchId());
        assertEquals(SWITCH_ID_1, ports.get(1).getSwitchId());
        assertEquals(SWITCH_ID_2, ports.get(2).getSwitchId());

        assertEquals(0, ports.get(0).getPhysicalPorts().size());
        assertEquals(0, ports.get(1).getPhysicalPorts().size());
        assertEquals(0, ports.get(2).getPhysicalPorts().size());
    }

    @Test
    public void createLogicalPortWithPhysicalPortsTest() {
        LagLogicalPort logicalPort = createLogicalPort(SWITCH_ID_1, LOGICAL_PORT_NUMBER_1);
        assertEquals(0, lagLogicalPortRepository.findAll().iterator().next().getPhysicalPorts().size());

        PhysicalPort physicalPort1 = createPhysicalPort(SWITCH_ID_1, PHYSICAL_PORT_NUMBER_1, logicalPort);
        PhysicalPort physicalPort2 = createPhysicalPort(SWITCH_ID_1, PHYSICAL_PORT_NUMBER_2, logicalPort);
        logicalPort.setPhysicalPorts(Lists.newArrayList(physicalPort1, physicalPort2));

        List<LagLogicalPort> ports = new ArrayList<>(lagLogicalPortRepository.findAll());
        assertEquals(1, ports.size());
        assertEquals(2, ports.get(0).getPhysicalPorts().size());

        assertEquals(PHYSICAL_PORT_NUMBER_1, ports.get(0).getPhysicalPorts().get(0).getPortNumber());
        assertEquals(PHYSICAL_PORT_NUMBER_2, ports.get(0).getPhysicalPorts().get(1).getPortNumber());

        assertEquals(SWITCH_ID_1, ports.get(0).getPhysicalPorts().get(0).getSwitchId());
        assertEquals(SWITCH_ID_1, ports.get(0).getPhysicalPorts().get(1).getSwitchId());

        assertEquals(logicalPort, ports.get(0).getPhysicalPorts().get(0).getLagLogicalPort());
        assertEquals(logicalPort, ports.get(0).getPhysicalPorts().get(1).getLagLogicalPort());
    }


    @Test
    public void removeLogicalPortTest() {
        LagLogicalPort logicalPort1 = createLogicalPort(SWITCH_ID_1, LOGICAL_PORT_NUMBER_1);
        LagLogicalPort logicalPort2 = createLogicalPort(SWITCH_ID_1, LOGICAL_PORT_NUMBER_1);

        PhysicalPort physicalPort1 = createPhysicalPort(SWITCH_ID_1, PHYSICAL_PORT_NUMBER_1, logicalPort1);
        PhysicalPort physicalPort2 = createPhysicalPort(SWITCH_ID_1, PHYSICAL_PORT_NUMBER_2, logicalPort1);
        logicalPort1.setPhysicalPorts(Lists.newArrayList(physicalPort1, physicalPort2));

        transactionManager.doInTransaction(() -> lagLogicalPortRepository.remove(logicalPort2));

        List<LagLogicalPort> ports = new ArrayList<>(lagLogicalPortRepository.findAll());
        assertEquals(1, ports.size());
        assertEquals(2, ports.get(0).getPhysicalPorts().size());
        assertEquals(2, physicalPortRepository.findAll().size());

        transactionManager.doInTransaction(() -> lagLogicalPortRepository.remove(logicalPort1));

        assertEquals(0, lagLogicalPortRepository.findAll().size());
        assertEquals(0, physicalPortRepository.findAll().size());
    }

    @Test
    public void findBySwitchIdTest() {
        LagLogicalPort logicalPort1 = createLogicalPort(SWITCH_ID_1, LOGICAL_PORT_NUMBER_1);
        createLogicalPort(SWITCH_ID_1, LOGICAL_PORT_NUMBER_2);
        createLogicalPort(SWITCH_ID_3, LOGICAL_PORT_NUMBER_3);

        PhysicalPort physicalPort1 = createPhysicalPort(SWITCH_ID_1, PHYSICAL_PORT_NUMBER_1, logicalPort1);
        PhysicalPort physicalPort2 = createPhysicalPort(SWITCH_ID_1, PHYSICAL_PORT_NUMBER_2, logicalPort1);
        logicalPort1.setPhysicalPorts(Lists.newArrayList(physicalPort1, physicalPort2));

        List<LagLogicalPort> foundPorts = new ArrayList<>(lagLogicalPortRepository.findBySwitchId(SWITCH_ID_1));
        foundPorts.sort(Comparator.comparingInt(LagLogicalPort::getLogicalPortNumber));

        assertEquals(LOGICAL_PORT_NUMBER_1, foundPorts.get(0).getLogicalPortNumber());
        assertEquals(LOGICAL_PORT_NUMBER_2, foundPorts.get(1).getLogicalPortNumber());
        assertEquals(2, foundPorts.get(0).getPhysicalPorts().size());
        assertEquals(0, foundPorts.get(1).getPhysicalPorts().size());

        assertEquals(0, lagLogicalPortRepository.findBySwitchId(SWITCH_ID_2).size());
    }

    @Test
    public void findBySwitchIdAndPortNumberTest() {
        LagLogicalPort logicalPort1 = createLogicalPort(SWITCH_ID_1, LOGICAL_PORT_NUMBER_1);
        createLogicalPort(SWITCH_ID_2, LOGICAL_PORT_NUMBER_2);

        PhysicalPort physicalPort1 = createPhysicalPort(SWITCH_ID_1, PHYSICAL_PORT_NUMBER_1, logicalPort1);
        PhysicalPort physicalPort2 = createPhysicalPort(SWITCH_ID_1, PHYSICAL_PORT_NUMBER_2, logicalPort1);
        logicalPort1.setPhysicalPorts(Lists.newArrayList(physicalPort1, physicalPort2));

        Optional<LagLogicalPort> foundPort1 = lagLogicalPortRepository.findBySwitchIdAndPortNumber(
                SWITCH_ID_1, LOGICAL_PORT_NUMBER_1);
        assertTrue(foundPort1.isPresent());
        assertEquals(LOGICAL_PORT_NUMBER_1, foundPort1.get().getLogicalPortNumber());
        assertEquals(2, foundPort1.get().getPhysicalPorts().size());

        Optional<LagLogicalPort> foundPort2 = lagLogicalPortRepository.findBySwitchIdAndPortNumber(
                SWITCH_ID_2, LOGICAL_PORT_NUMBER_2);
        assertTrue(foundPort2.isPresent());
        assertEquals(LOGICAL_PORT_NUMBER_2, foundPort2.get().getLogicalPortNumber());
        assertEquals(0, foundPort2.get().getPhysicalPorts().size());

        assertFalse(lagLogicalPortRepository.findBySwitchIdAndPortNumber(
                SWITCH_ID_3, LOGICAL_PORT_NUMBER_3).isPresent());
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
