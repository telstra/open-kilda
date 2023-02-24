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
import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FermaLagLogicalPortTest extends InMemoryGraphBasedTest {
    static final int LOGICAL_PORT_NUMBER_1 = 1;
    static final int LOGICAL_PORT_NUMBER_2 = 2;
    static final int LOGICAL_PORT_NUMBER_3 = 3;
    static final int LOGICAL_PORT_NUMBER_4 = 4;
    static final int PHYSICAL_PORT_NUMBER_1 = 5;
    static final int PHYSICAL_PORT_NUMBER_2 = 6;

    LagLogicalPortRepository lagLogicalPortRepository;
    PhysicalPortRepository physicalPortRepository;

    @Before
    public void setUp() {
        lagLogicalPortRepository = repositoryFactory.createLagLogicalPortRepository();
        physicalPortRepository = repositoryFactory.createPhysicalPortRepository();
    }

    @Test
    public void createLogicalPortWithoutPhysicalPortsTest() {
        createLogicalPort(SWITCH_ID_1, LOGICAL_PORT_NUMBER_1, true);
        createLogicalPort(SWITCH_ID_1, LOGICAL_PORT_NUMBER_2, false);
        createLogicalPort(SWITCH_ID_2, LOGICAL_PORT_NUMBER_3, true);

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

        assertTrue(ports.get(0).isLacpReply());
        assertFalse(ports.get(1).isLacpReply());
        assertTrue(ports.get(2).isLacpReply());
    }

    @Test
    public void createLogicalPortWithPhysicalPortsTest() {
        LagLogicalPort logicalPort = createLogicalPort(SWITCH_ID_1, LOGICAL_PORT_NUMBER_1, true,
                PHYSICAL_PORT_NUMBER_1, PHYSICAL_PORT_NUMBER_2);

        List<LagLogicalPort> ports = new ArrayList<>(lagLogicalPortRepository.findAll());
        assertEquals(1, ports.size());
        assertEquals(2, ports.get(0).getPhysicalPorts().size());

        assertEquals(PHYSICAL_PORT_NUMBER_1, ports.get(0).getPhysicalPorts().get(0).getPortNumber());
        assertEquals(PHYSICAL_PORT_NUMBER_2, ports.get(0).getPhysicalPorts().get(1).getPortNumber());

        assertEquals(SWITCH_ID_1, ports.get(0).getPhysicalPorts().get(0).getSwitchId());
        assertEquals(SWITCH_ID_1, ports.get(0).getPhysicalPorts().get(1).getSwitchId());

        assertEquals(logicalPort, ports.get(0).getPhysicalPorts().get(0).getLagLogicalPort());
        assertEquals(logicalPort, ports.get(0).getPhysicalPorts().get(1).getLagLogicalPort());

        assertTrue(ports.get(0).isLacpReply());
    }

    @Test
    public void createLogicalPortAndSetPhysicalPortsTest() {
        LagLogicalPort logicalPort = createLogicalPort(SWITCH_ID_1, LOGICAL_PORT_NUMBER_1, false);
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
        LagLogicalPort logicalPort1 = createLogicalPort(SWITCH_ID_1, LOGICAL_PORT_NUMBER_1, true,
                PHYSICAL_PORT_NUMBER_1, PHYSICAL_PORT_NUMBER_2);
        LagLogicalPort logicalPort2 = createLogicalPort(SWITCH_ID_1, LOGICAL_PORT_NUMBER_2, false);

        transactionManager.doInTransaction(() -> lagLogicalPortRepository.remove(logicalPort2));

        List<LagLogicalPort> ports = new ArrayList<>(lagLogicalPortRepository.findAll());
        assertEquals(1, ports.size());
        basicLagAssert(LOGICAL_PORT_NUMBER_1, 2, true, ports.get(0));
        assertEquals(2, physicalPortRepository.findAll().size());

        transactionManager.doInTransaction(() -> lagLogicalPortRepository.remove(logicalPort1));

        assertEquals(0, lagLogicalPortRepository.findAll().size());
        assertEquals(0, physicalPortRepository.findAll().size());
    }

    @Test
    public void findBySwitchIdTest() {
        createLogicalPort(SWITCH_ID_1, LOGICAL_PORT_NUMBER_1, false, PHYSICAL_PORT_NUMBER_1, PHYSICAL_PORT_NUMBER_2);
        createLogicalPort(SWITCH_ID_1, LOGICAL_PORT_NUMBER_2, true);
        createLogicalPort(SWITCH_ID_3, LOGICAL_PORT_NUMBER_3, false);

        List<LagLogicalPort> foundPorts = new ArrayList<>(lagLogicalPortRepository.findBySwitchId(SWITCH_ID_1));
        foundPorts.sort(Comparator.comparingInt(LagLogicalPort::getLogicalPortNumber));

        basicLagAssert(LOGICAL_PORT_NUMBER_1, 2, false, foundPorts.get(0));
        basicLagAssert(LOGICAL_PORT_NUMBER_2, 0, true, foundPorts.get(1));

        assertEquals(0, lagLogicalPortRepository.findBySwitchId(SWITCH_ID_2).size());
    }

    @Test
    public void findBySwitchIdsTest() {
        createLogicalPort(SWITCH_ID_1, LOGICAL_PORT_NUMBER_1, true, PHYSICAL_PORT_NUMBER_1, PHYSICAL_PORT_NUMBER_2);
        createLogicalPort(SWITCH_ID_2, LOGICAL_PORT_NUMBER_2, false);
        createLogicalPort(SWITCH_ID_2, LOGICAL_PORT_NUMBER_3, true);
        createLogicalPort(SWITCH_ID_3, LOGICAL_PORT_NUMBER_4, false);

        Map<SwitchId, List<LagLogicalPort>> foundPorts = lagLogicalPortRepository.findBySwitchIds(
                Sets.newHashSet(SWITCH_ID_1, SWITCH_ID_2));

        assertEquals(2, foundPorts.size());

        assertEquals(1, foundPorts.get(SWITCH_ID_1).size());
        basicLagAssert(LOGICAL_PORT_NUMBER_1, 2, true, foundPorts.get(SWITCH_ID_1).get(0));

        assertEquals(2, foundPorts.get(SWITCH_ID_2).size());
        foundPorts.get(SWITCH_ID_2).sort(Comparator.comparingInt(LagLogicalPort::getLogicalPortNumber));
        basicLagAssert(LOGICAL_PORT_NUMBER_2, 0, false, foundPorts.get(SWITCH_ID_2).get(0));
        basicLagAssert(LOGICAL_PORT_NUMBER_3, 0, true, foundPorts.get(SWITCH_ID_2).get(1));

        assertEquals(0, lagLogicalPortRepository.findBySwitchIds(Sets.newHashSet(SWITCH_ID_4)).size());
    }

    @Test
    public void findBySwitchIdAndPortNumberTest() {
        createLogicalPort(SWITCH_ID_1, LOGICAL_PORT_NUMBER_1, true, PHYSICAL_PORT_NUMBER_1, PHYSICAL_PORT_NUMBER_2);
        createLogicalPort(SWITCH_ID_2, LOGICAL_PORT_NUMBER_2, false);

        Optional<LagLogicalPort> foundPort1 = lagLogicalPortRepository.findBySwitchIdAndPortNumber(
                SWITCH_ID_1, LOGICAL_PORT_NUMBER_1);
        assertTrue(foundPort1.isPresent());
        basicLagAssert(LOGICAL_PORT_NUMBER_1, 2, true, foundPort1.get());

        Optional<LagLogicalPort> foundPort2 = lagLogicalPortRepository.findBySwitchIdAndPortNumber(
                SWITCH_ID_2, LOGICAL_PORT_NUMBER_2);
        assertTrue(foundPort2.isPresent());
        basicLagAssert(LOGICAL_PORT_NUMBER_2, 0, false, foundPort2.get());

        assertFalse(lagLogicalPortRepository.findBySwitchIdAndPortNumber(
                SWITCH_ID_3, LOGICAL_PORT_NUMBER_3).isPresent());
    }

    private LagLogicalPort createLogicalPort(
            SwitchId switchId, int logicalPortNumber, boolean lacpReply, Integer... physicalPorts) {
        LagLogicalPort port = new LagLogicalPort(switchId, logicalPortNumber, Arrays.asList(physicalPorts), lacpReply);
        lagLogicalPortRepository.add(port);
        return port;
    }

    private PhysicalPort createPhysicalPort(SwitchId switchId, int physicalPortNumber, LagLogicalPort logicalPort) {
        PhysicalPort port = new PhysicalPort(switchId, physicalPortNumber, logicalPort);
        physicalPortRepository.add(port);
        return port;
    }

    private void basicLagAssert(
            int expectedPortNumber, int expectedPhysPortCount, boolean expectedLacpReply, LagLogicalPort port) {
        assertEquals(expectedPortNumber, port.getLogicalPortNumber());
        assertEquals(expectedPhysPortCount, port.getPhysicalPorts().size());
        assertEquals(expectedLacpReply, port.isLacpReply());
    }
}
