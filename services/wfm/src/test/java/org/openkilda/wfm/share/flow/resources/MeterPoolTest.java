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

package org.openkilda.wfm.share.flow.resources;

import static java.lang.String.format;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.openkilda.model.FlowMeter;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.FlowMeterRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.Neo4jBasedTest;

import org.junit.Before;
import org.junit.Test;

import java.util.Collection;

public class MeterPoolTest extends Neo4jBasedTest {
    private static final SwitchId SWITCH_ID = new SwitchId("ff:00");
    private static final String FLOW_1 = "flow_1";
    private static final String FLOW_2 = "flow_2";
    private static final String FLOW_3 = "flow_3";
    private static final PathId PATH_ID_1 = new PathId("path_1");
    private static final PathId PATH_ID_2 = new PathId("path_2");
    private static final PathId PATH_ID_3 = new PathId("path_3");

    private MeterPool meterPool;
    private FlowMeterRepository flowMeterRepository;

    @Before
    public void setUp() {
        meterPool = new MeterPool(persistenceManager, new MeterId(31), new MeterId(40));

        SwitchRepository switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        switchRepository.createOrUpdate(Switch.builder().switchId(SWITCH_ID).build());
        flowMeterRepository = persistenceManager.getRepositoryFactory().createFlowMeterRepository();
    }

    @Test
    public void meterPoolTest() {
        long first = meterPool.allocate(SWITCH_ID, FLOW_1, PATH_ID_1).getValue();
        assertEquals(31, first);

        long second = meterPool.allocate(SWITCH_ID, FLOW_2, PATH_ID_2).getValue();
        assertEquals(32, second);

        long third = meterPool.allocate(SWITCH_ID, FLOW_3, PATH_ID_3).getValue();
        assertEquals(33, third);

        meterPool.deallocate(PATH_ID_3);
        meterPool.deallocate(PATH_ID_2);

        long fourth = meterPool.allocate(SWITCH_ID, "flow_4", new PathId("path_4")).getValue();
        assertEquals(32, fourth);

        long fifth = meterPool.allocate(SWITCH_ID, "flow_5", new PathId("path_5")).getValue();
        assertEquals(33, fifth);

        long sixth = meterPool.allocate(SWITCH_ID, "flow_6", new PathId("path_6")).getValue();
        assertEquals(34, sixth);
    }

    @Test(expected = ResourceNotAvailableException.class)
    public void meterPoolFullTest() {
        for (int i = 31; i <= 41; i++) {
            meterPool.allocate(SWITCH_ID, format("flow_%d", i), new PathId(format("path_%d", i)));
        }
    }

    @Test
    public void createTwoMeterForOnePathTest() {
        long first = meterPool.allocate(SWITCH_ID, FLOW_1, PATH_ID_1).getValue();
        long second = meterPool.allocate(SWITCH_ID, FLOW_1, PATH_ID_1).getValue();
        assertNotEquals(first, second);
        assertEquals(2, flowMeterRepository.findAll().size());
    }

    @Test
    public void deallocateMetersByPathTest() {
        meterPool.allocate(SWITCH_ID, FLOW_1, PATH_ID_1);
        meterPool.allocate(SWITCH_ID, FLOW_1, PATH_ID_1);
        meterPool.allocate(SWITCH_ID, FLOW_2, PATH_ID_2);
        long meterId = meterPool.allocate(SWITCH_ID, FLOW_3, PATH_ID_3).getValue();
        assertEquals(4, flowMeterRepository.findAll().size());

        meterPool.deallocate(PATH_ID_1, PATH_ID_2);
        Collection<FlowMeter> flowMeters = flowMeterRepository.findAll();
        assertEquals(1, flowMeters.size());
        assertEquals(meterId, flowMeters.iterator().next().getMeterId().getValue());
    }
}
