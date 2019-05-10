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

import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.Neo4jBasedTest;

import org.junit.Before;
import org.junit.Test;

public class MeterPoolTest extends Neo4jBasedTest {
    private static final SwitchId SWITCH_ID = new SwitchId("ff:00");

    private MeterPool meterPool;

    @Before
    public void setUp() {
        meterPool = new MeterPool(persistenceManager, new MeterId(31), new MeterId(40));

        SwitchRepository switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        switchRepository.createOrUpdate(Switch.builder().switchId(SWITCH_ID).build());
    }

    @Test
    public void meterPoolTest() {
        long first = meterPool.allocate(SWITCH_ID, "flow_1", new PathId("path_1")).getValue();
        assertEquals(31, first);

        PathId path2 = new PathId("path_2");
        long second = meterPool.allocate(SWITCH_ID, "flow_2", path2).getValue();
        assertEquals(32, second);

        PathId path3 = new PathId("path_3");
        long third = meterPool.allocate(SWITCH_ID, "flow_3", path3).getValue();
        assertEquals(33, third);

        meterPool.deallocate(path3);
        meterPool.deallocate(path2);

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
}
