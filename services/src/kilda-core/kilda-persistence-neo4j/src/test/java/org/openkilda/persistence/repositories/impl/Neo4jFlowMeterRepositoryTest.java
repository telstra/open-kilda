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
import org.openkilda.persistence.repositories.FlowMeterRepository;
import org.openkilda.persistence.repositories.SwitchRepository;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collection;

public class Neo4jFlowMeterRepositoryTest extends Neo4jBasedTest {
    static final String TEST_FLOW_ID = "test_flow";

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
        FlowMeter meter = FlowMeter.builder()
                .theSwitch(theSwitch)
                .meterId(new MeterId(1))
                .pathId(new PathId(TEST_FLOW_ID + "_path"))
                .flowId(TEST_FLOW_ID)
                .build();
        flowMeterRepository.createOrUpdate(meter);

        Collection<FlowMeter> allMeters = flowMeterRepository.findAll();
        FlowMeter foundMeter = allMeters.iterator().next();

        assertEquals(theSwitch.getSwitchId(), foundMeter.getTheSwitch().getSwitchId());
        assertEquals(TEST_FLOW_ID, foundMeter.getFlowId());
    }

    @Test
    public void shouldDeleteFlowMeter() {
        FlowMeter meter = FlowMeter.builder()
                .theSwitch(theSwitch)
                .meterId(new MeterId(1))
                .pathId(new PathId(TEST_FLOW_ID + "_path"))
                .flowId(TEST_FLOW_ID)
                .build();
        flowMeterRepository.createOrUpdate(meter);

        flowMeterRepository.delete(meter);

        assertEquals(0, flowMeterRepository.findAll().size());
    }

    @Test
    public void shouldDeleteFoundFlowMeter() {
        FlowMeter meter = FlowMeter.builder()
                .theSwitch(theSwitch)
                .meterId(new MeterId(1))
                .pathId(new PathId(TEST_FLOW_ID + "_path"))
                .flowId(TEST_FLOW_ID)
                .build();
        flowMeterRepository.createOrUpdate(meter);

        Collection<FlowMeter> allMeters = flowMeterRepository.findAll();
        FlowMeter foundMeter = allMeters.iterator().next();
        flowMeterRepository.delete(foundMeter);

        assertEquals(0, flowMeterRepository.findAll().size());
    }
}
