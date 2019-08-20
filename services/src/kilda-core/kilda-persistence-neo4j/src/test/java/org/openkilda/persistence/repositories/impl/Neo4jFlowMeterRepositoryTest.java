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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.openkilda.model.FlowMeter;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.persistence.Neo4jBasedTest;
import org.openkilda.persistence.repositories.FlowMeterRepository;
import org.openkilda.persistence.repositories.SwitchRepository;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collection;
import java.util.Optional;

public class Neo4jFlowMeterRepositoryTest extends Neo4jBasedTest {
    static final String TEST_FLOW_ID = "test_flow";
    static final String TEST_PATH_ID = "test_path";

    static FlowMeterRepository flowMeterRepository;
    static SwitchRepository switchRepository;

    private Switch theSwitch;

    @BeforeClass
    public static void setUp() {
        flowMeterRepository = new Neo4jFlowMeterRepository(neo4jSessionFactory, txManager);
        switchRepository = new Neo4jSwitchRepository(neo4jSessionFactory, txManager);
    }

    @Before
    public void createSwitches() {
        theSwitch = buildTestSwitch(1);
        switchRepository.createOrUpdate(theSwitch);
    }

    @Test
    public void shouldCreateFlowMeter() {
        FlowMeter meter = createFlowMeter();
        flowMeterRepository.createOrUpdate(meter);

        Collection<FlowMeter> allMeters = flowMeterRepository.findAll();
        FlowMeter foundMeter = allMeters.iterator().next();

        assertEquals(theSwitch.getSwitchId(), foundMeter.getSwitchId());
        assertEquals(TEST_FLOW_ID, foundMeter.getFlowId());
    }

    @Test
    public void shouldCreateLldpMeter() {
        FlowMeter meter = createFlowMeter(5, null); // LLDP meter has no path ID
        flowMeterRepository.createOrUpdate(meter);

        Collection<FlowMeter> allMeters = flowMeterRepository.findAll();
        assertEquals(1, allMeters.size());
        FlowMeter foundMeter = allMeters.iterator().next();

        assertEquals(theSwitch.getSwitchId(), foundMeter.getSwitchId());
        assertEquals(TEST_FLOW_ID, foundMeter.getFlowId());
        assertEquals(5, foundMeter.getMeterId().getValue());
        assertNull(foundMeter.getPathId());
    }

    @Test
    public void shouldCreateTwoLldpMeterForOneSwitch() {
        FlowMeter meter1 = createFlowMeter(5, null); // LLDP meter has no path ID
        FlowMeter meter2 = createFlowMeter(7, null); // LLDP meter has no path ID
        flowMeterRepository.createOrUpdate(meter1);
        flowMeterRepository.createOrUpdate(meter2);

        Collection<FlowMeter> allMeters = flowMeterRepository.findAll();
        assertEquals(2, allMeters.size());


        FlowMeter createdMeter1 = flowMeterRepository.findLldpMeterByMeterIdSwitchIdAndFlowId(
                meter1.getMeterId(), meter1.getSwitchId(), meter1.getFlowId()).get();
        FlowMeter createdMeter2 = flowMeterRepository.findLldpMeterByMeterIdSwitchIdAndFlowId(
                meter2.getMeterId(), meter2.getSwitchId(), meter2.getFlowId()).get();

        assertEquals(meter1, createdMeter1);
        assertEquals(meter2, createdMeter2);
    }

    @Test
    public void shouldFindLldpMeter() {
        FlowMeter flowMeter = createFlowMeter();

        MeterId lldpMeterId = new MeterId(33);
        FlowMeter lldpMeter = createFlowMeter(33, null); // LLDP meter has no path ID

        flowMeterRepository.createOrUpdate(flowMeter);
        flowMeterRepository.createOrUpdate(lldpMeter);

        Optional<FlowMeter> meter = flowMeterRepository.findLldpMeterByMeterIdSwitchIdAndFlowId(
                lldpMeterId, theSwitch.getSwitchId(), TEST_FLOW_ID);

        assertTrue(meter.isPresent());

        assertEquals(theSwitch.getSwitchId(), meter.get().getSwitchId());
        assertEquals(TEST_FLOW_ID, meter.get().getFlowId());
        assertEquals(33, meter.get().getMeterId().getValue());
        assertNull(meter.get().getPathId());
    }

    @Test
    public void shouldDeleteFlowMeter() {
        FlowMeter meter = createFlowMeter();
        flowMeterRepository.createOrUpdate(meter);

        flowMeterRepository.delete(meter);

        assertEquals(0, flowMeterRepository.findAll().size());
    }

    @Test
    public void shouldDeleteFoundFlowMeter() {
        FlowMeter meter = createFlowMeter();
        flowMeterRepository.createOrUpdate(meter);

        Collection<FlowMeter> allMeters = flowMeterRepository.findAll();
        FlowMeter foundMeter = allMeters.iterator().next();
        flowMeterRepository.delete(foundMeter);

        assertEquals(0, flowMeterRepository.findAll().size());
    }

    private FlowMeter createFlowMeter(int meterId, PathId pathId) {
        return FlowMeter.builder()
                .switchId(theSwitch.getSwitchId())
                .meterId(new MeterId(meterId))
                .pathId(pathId)
                .flowId(TEST_FLOW_ID)
                .build();
    }

    private FlowMeter createFlowMeter() {
        return createFlowMeter(1, new PathId(TEST_PATH_ID));
    }
}
