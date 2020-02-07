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

import org.openkilda.model.FlowMeter;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.persistence.Neo4jBasedTest;
import org.openkilda.persistence.PersistenceException;
import org.openkilda.persistence.repositories.FlowMeterRepository;
import org.openkilda.persistence.repositories.SwitchRepository;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collection;

public class Neo4jFlowMeterRepositoryTest extends Neo4jBasedTest {
    static final String TEST_FLOW_ID = "test_flow";
    static final String TEST_PATH_ID = "test_path";
    static final MeterId MIN_METER_ID = new MeterId(5);
    static final MeterId MAX_METER_ID = new MeterId(25);

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

    @Test(expected = PersistenceException.class)
    public void shouldNotGetMoreThanTwoMetersForPath() {
        flowMeterRepository.createOrUpdate(createFlowMeter(1, new PathId(TEST_PATH_ID)));
        flowMeterRepository.createOrUpdate(createFlowMeter(2, new PathId(TEST_PATH_ID)));
        flowMeterRepository.createOrUpdate(createFlowMeter(3, new PathId(TEST_PATH_ID)));
        flowMeterRepository.findByPathId(new PathId(TEST_PATH_ID));
    }

    @Test
    public void shouldGetZeroMetersForPath() {
        Collection<FlowMeter> meters = flowMeterRepository.findByPathId(new PathId(TEST_PATH_ID));
        assertEquals(0, meters.size());
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

    @Test
    public void shouldSelectNextInOrderResourceWhenFindUnassignedMeter() {
        long first = findUnassignedMeterAndCreate("flow_1");
        assertEquals(5, first);

        long second = findUnassignedMeterAndCreate("flow_2");
        assertEquals(6, second);

        long third = findUnassignedMeterAndCreate("flow_3");
        assertEquals(7, third);

        flowMeterRepository.findAll().stream()
                .filter(flowMeter -> flowMeter.getMeterId().getValue() == second)
                .forEach(flowMeterRepository::delete);
        long fourth = findUnassignedMeterAndCreate("flow_4");
        assertEquals(6, fourth);

        long fifth = findUnassignedMeterAndCreate("flow_5");
        assertEquals(8, fifth);
    }

    @Test
    public void shouldAssignTwoMetersForOneFlow() {
        String flowId = "flow_1";
        long flowCookie = findUnassignedMeterAndCreate(flowId);
        assertEquals(5, flowCookie);

        long secondCookie = findUnassignedMeterAndCreate(flowId);
        assertEquals(6, secondCookie);
    }

    private long findUnassignedMeterAndCreate(String flowId) {
        MeterId availableMeterId = flowMeterRepository
                .findUnassignedMeterId(theSwitch.getSwitchId(), MIN_METER_ID, MAX_METER_ID).get();
        FlowMeter flowMeterId = FlowMeter.builder()
                .switchId(theSwitch.getSwitchId())
                .meterId(availableMeterId)
                .pathId(new PathId(TEST_PATH_ID))
                .flowId(flowId)
                .build();
        flowMeterRepository.createOrUpdate(flowMeterId);
        return availableMeterId.getValue();
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
