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

package org.openkilda.wfm.topology.packetmon.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.openkilda.wfm.topology.packetmon.type.FlowDirection;
import org.openkilda.wfm.topology.packetmon.type.FlowType;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FlowStatsTest {
    private static final int windowSize = 5;
    private static final int maxTimeDelta = 10;
    private static final double maxFlowVariance = 0.1;
    private static final String flowId = "99999999";

    private FlowStats flowStats;
    private long[][] ingress = {          // time, value
            {1530058871, 1219425071},
            {1530058931, 1223990911},
            {1530058991, 1228449180},
            {1530059051, 1232932728},
            {1530059111, 1237574591}
    };

    private long[][] egress = {
            {1530058870, 70089325625L},
            {1530058930, 70093899662L},
            {1530058990, 70098376201L},
            {1530059050, 70102869421L},
            {1530059110, 70107524551L}
    };

    @Before
    public void setUp() throws Exception {
        flowStats = new FlowStats(flowId, windowSize, maxTimeDelta, maxFlowVariance);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void validFlowTest() {
        for (int i = 0; i < ingress.length; i++) {
            flowStats.add(FlowDirection.forward, FlowType.EGRESS, egress[i][0], egress[i][1]);
            flowStats.add(FlowDirection.forward, FlowType.INGRESS, ingress[i][0], ingress[i][1]);
        }
        assertTrue(flowStats.ok(FlowDirection.forward));
    }

    @Test
    public void flowWithPacketLoss() {
        double packetLoss = 0.05;
        for (int i = 0; i < ingress.length; i++) {
            long egressValue = (long) (egress[i][1] * (1 - packetLoss));
            flowStats.add(FlowDirection.forward, FlowType.EGRESS, egress[i][0], egressValue);
            flowStats.add(FlowDirection.forward, FlowType.INGRESS, ingress[i][0], ingress[i][1]);
        }
        assertFalse(flowStats.ok(FlowDirection.forward));
    }

    @Test
    public void haveEnoughDataTest() {

        flowStats.add(FlowDirection.forward, FlowType.EGRESS, 1, 1);
        assertFalse(flowStats.haveEnoughData(FlowDirection.forward));
        flowStats.add(FlowDirection.forward, FlowType.INGRESS, 1, 1);
        assertFalse(flowStats.haveEnoughData(FlowDirection.forward));
        flowStats.add(FlowDirection.forward, FlowType.EGRESS, 20, 2);
        assertFalse(flowStats.haveEnoughData(FlowDirection.forward));
        flowStats.add(FlowDirection.forward, FlowType.INGRESS, 20, 2);
        assertTrue(flowStats.haveEnoughData(FlowDirection.forward));
    }

    @Test
    public void maxTimeTest() {
        flowStats.add(FlowDirection.forward, FlowType.EGRESS, 1, 1);
        flowStats.add(FlowDirection.forward, FlowType.INGRESS, 2 + maxTimeDelta, 1);
        assertFalse(flowStats.valuesWithinTimeDelta(FlowDirection.forward));

        flowStats.add(FlowDirection.forward, FlowType.EGRESS, 2 + maxTimeDelta, 1);
        assertTrue(flowStats.valuesWithinTimeDelta(FlowDirection.forward));
    }

    @Test
    public void isWithinVarianceTest() {
        flowStats.add(FlowDirection.forward, FlowType.EGRESS, 1, 1);
        flowStats.add(FlowDirection.forward, FlowType.INGRESS, 1, 1);
        flowStats.add(FlowDirection.forward, FlowType.EGRESS, 2, 2);
        flowStats.add(FlowDirection.forward, FlowType.INGRESS, 2, 2);
        assertTrue(flowStats.isWithinVariance(FlowDirection.forward));
    }
}
