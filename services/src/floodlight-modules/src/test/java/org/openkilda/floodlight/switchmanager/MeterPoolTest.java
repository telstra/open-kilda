/* Copyright 2017 Telstra Open Source
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

package org.openkilda.floodlight.switchmanager;

import static org.junit.Assert.assertEquals;

import org.openkilda.model.SwitchId;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class MeterPoolTest {
    private static final SwitchId SWITCH_1_ID = new SwitchId("ff:01");
    private static final SwitchId SWITCH_2_ID = new SwitchId("ff:02");
    private static final SwitchId SWITCH_3_ID = new SwitchId("ff:03");
    private static final String FLOW_1_ID = "first-flow";
    private static final String FLOW_2_ID = "second-flow";
    private static final String FLOW_3_ID = "third-flow";

    private MeterPool meterPool = new MeterPool();

    @Test
    public void testMeterPool() {
        Set<Integer> expected = new HashSet<>();

        int m1 = meterPool.allocate(SWITCH_1_ID, FLOW_1_ID);
        int m2 = meterPool.allocate(SWITCH_2_ID, FLOW_1_ID);

        expected.add(m1);
        expected.add(m2);
        assertEquals(expected, meterPool.getMetersByFlow(FLOW_1_ID));
        expected.clear();

        int m3 = meterPool.allocate(SWITCH_1_ID, FLOW_2_ID);
        int m4 = meterPool.allocate(SWITCH_2_ID, FLOW_2_ID);
        expected.add(m3);
        expected.add(m4);
        assertEquals(expected, meterPool.getMetersByFlow(FLOW_2_ID));
        expected.clear();

        expected.add(m1);
        expected.add(m3);
        assertEquals(expected, meterPool.getMetersBySwitch(SWITCH_1_ID));
        expected.clear();

        expected.add(m2);
        expected.add(m4);
        assertEquals(expected, meterPool.getMetersBySwitch(SWITCH_2_ID));
        expected.clear();

        meterPool.deallocate(SWITCH_1_ID, FLOW_1_ID);
        meterPool.deallocate(SWITCH_2_ID, FLOW_1_ID);

        int m5 = meterPool.allocate(SWITCH_1_ID, FLOW_3_ID);
        int m6 = meterPool.allocate(SWITCH_3_ID, FLOW_3_ID);

        expected.add(m5);
        expected.add(m6);
        assertEquals(expected, meterPool.getMetersByFlow(FLOW_3_ID));
        expected.clear();

        expected.add(m3);
        expected.add(m5);
        assertEquals(expected, meterPool.getMetersBySwitch(SWITCH_1_ID));
        expected.clear();

        assertEquals(m1 + 2, m5);
    }
}
