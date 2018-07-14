/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.topology.packetmon.bolts;

import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.mock;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.openkilda.wfm.topology.packetmon.data.FlowStats;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.tuple.Tuple;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class FlowMonBoltTest {
    private static final int cacheTimeout = 2;
    private static final int numOfDatapoints = 10;
    private static final int maxTimeDelta = 10;
    private static final int maxFlowVariance = 1;
    private static final String FlowIdOne = "1";

    private Tuple tuple;
    private Map map;
    private TopologyContext context;
    private OutputCollector collector;
    FlowMonBolt flowMonBolt;

    @Before
    public void setUp() throws Exception {
        // Mock objects
        tuple = mock(Tuple.class);
        collector = createNiceMock(OutputCollector.class);
        context = createStrictMock(TopologyContext.class);
        map = new HashMap();

        flowMonBolt = new FlowMonBolt(cacheTimeout, numOfDatapoints, maxTimeDelta, maxFlowVariance);
        flowMonBolt.prepare(map, context, collector);
    }

    @After
    public void tearDown() throws Exception {
    }

    @SuppressWarnings("checkstyle:variabledeclarationusagedistance")
    @Test
    public void cacheTest() throws Exception {
        assertEquals(0, flowMonBolt.getCache().size());

        FlowStats flowOneEntry = flowMonBolt.getCache().getUnchecked(FlowIdOne);
        assertEquals(1, flowMonBolt.getCache().size());
        Thread.sleep((cacheTimeout + 1) * 1000);  // Give it enough time to timeout
        assertNull(flowMonBolt.getCache().getIfPresent(flowOneEntry));
    }
}
