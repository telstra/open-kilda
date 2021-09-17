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

package org.openkilda.pce.impl;

import static java.lang.String.format;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.IslStatus;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.GetPathsResult;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.pce.finder.BestWeightAndShortestPathFinder;

import org.junit.Test;

public class MaxLatencyPathComputationStrategyBaseTest extends InMemoryPathComputerBaseTest {

    /**
     * Special case: flow with MAX_LATENCY strategy and 'max-latency' set to 0 should pick path with least latency.
     */
    @Test
    public void maxLatencyStratWithZeroLatency() throws RecoverableException, UnroutableFlowException {
        // 1 - 2 - 4
        //   + 3 +
        //path 1>2>4 has less latency than 1>3>4
        createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 100, 100, "00:", 1, 100, 101);
        //when: request a flow with MAX_LATENCY strategy and 'max-latency' set to 0
        Flow flow = Flow.builder()
                .flowId("test flow")
                .srcSwitch(getSwitchById("00:01")).srcPort(15)
                .destSwitch(getSwitchById("00:04")).destPort(15)
                .bandwidth(500)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .pathComputationStrategy(PathComputationStrategy.MAX_LATENCY)
                .maxLatency(0L)
                .build();
        PathComputer pathComputer = new InMemoryPathComputer(availableNetworkFactory,
                new BestWeightAndShortestPathFinder(5), config);
        GetPathsResult pathsResult = pathComputer.getPath(flow);

        //then: system returns a path with least weight
        assertFalse(pathsResult.isBackUpPathComputationWayUsed());
        assertThat(pathsResult.getForward().getSegments().get(1).getSrcSwitchId(), equalTo(new SwitchId("00:02")));
        assertThat(pathsResult.getReverse().getSegments().get(1).getSrcSwitchId(), equalTo(new SwitchId("00:02")));
    }

    /**
     * Special case: flow with MAX_LATENCY strategy and 'max-latency' being unset(null) should pick path with least
     * latency.
     */
    @Test
    public void maxLatencyStratWithNullLatency() throws RecoverableException, UnroutableFlowException {
        // 1 - 2 - 4
        //   + 3 +
        //path 1>2>4 has less latency than 1>3>4
        createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 100, 100, "00:", 1, 100, 101);
        //when: request a flow with MAX_LATENCY strategy and 'max-latency' set to 0
        Flow flow = Flow.builder()
                .flowId("test flow")
                .srcSwitch(getSwitchById("00:01")).srcPort(15)
                .destSwitch(getSwitchById("00:04")).destPort(15)
                .bandwidth(500)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .pathComputationStrategy(PathComputationStrategy.MAX_LATENCY)
                .build();
        PathComputer pathComputer = new InMemoryPathComputer(availableNetworkFactory,
                new BestWeightAndShortestPathFinder(5), config);
        GetPathsResult pathsResult = pathComputer.getPath(flow);

        //then: system returns a path with least weight
        assertFalse(pathsResult.isBackUpPathComputationWayUsed());
        assertThat(pathsResult.getForward().getSegments().get(1).getSrcSwitchId(), equalTo(new SwitchId("00:02")));
        assertThat(pathsResult.getReverse().getSegments().get(1).getSrcSwitchId(), equalTo(new SwitchId("00:02")));
    }

    @Test
    public void shouldUseBackUpWeightWhenNoPathFoundInMaxLatencyStrat()
            throws RecoverableException, UnroutableFlowException {
        // 1 - 2 - 4
        //   + 3 +
        //path 1>2>4 has less latency than 1>3>4
        createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 100, 100, "00:", 1, 100, 101);

        //when: request a flow with MAX_LATENCY strategy and 'max-latency' is not enough to build a path
        Flow flow = Flow.builder()
                .flowId("test flow")
                .srcSwitch(getSwitchById("00:01")).srcPort(15)
                .destSwitch(getSwitchById("00:04")).destPort(15)
                .bandwidth(500)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .pathComputationStrategy(PathComputationStrategy.MAX_LATENCY)
                .maxLatency(100L)
                .maxLatencyTier2(300L)
                .build();
        PathComputer pathComputer = new InMemoryPathComputer(availableNetworkFactory,
                new BestWeightAndShortestPathFinder(5), config);
        GetPathsResult pathsResult = pathComputer.getPath(flow);

        //then: system returns a path built by 'max_latency_tier2'
        assertTrue(pathsResult.isBackUpPathComputationWayUsed());
        assertThat(pathsResult.getForward().getSegments().get(1).getSrcSwitchId(), equalTo(new SwitchId("00:03")));
        assertThat(pathsResult.getReverse().getSegments().get(1).getSrcSwitchId(), equalTo(new SwitchId("00:03")));
    }

    @Test
    public void maxLatencyIssueTest() throws Exception {
        createMaxLatencyIssueTopo();

        Flow flow = Flow.builder()
                .flowId("test flow")
                .srcSwitch(getSwitchById("00:01")).srcPort(15)
                .destSwitch(getSwitchById("00:04")).destPort(15)
                .bandwidth(500)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .pathComputationStrategy(PathComputationStrategy.MAX_LATENCY)
                .maxLatency(30L)
                .build();

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        GetPathsResult path = pathComputer.getPath(flow);
        assertNotNull(path);
        assertEquals(path.getForward().getSegments().size(), 3);
    }

    private void createMaxLatencyIssueTopo() {
        // A - B - C - D
        //  \ /     \ /
        String switchStart = "00:";
        int index = 1;

        Switch nodeA = createSwitch(switchStart + format("%02X", index++));
        Switch nodeB = createSwitch(switchStart + format("%02X", index++));
        Switch nodeC = createSwitch(switchStart + format("%02X", index++));
        Switch nodeD = createSwitch(switchStart + format("%02X", index));

        createBiIsl(nodeA, nodeB, IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 1000, 1, 1L);
        createBiIsl(nodeB, nodeC, IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 1000, 2, 1L);
        createBiIsl(nodeC, nodeD, IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 1000, 3, 1L);
        createBiIsl(nodeA, nodeB, IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 1000, 4, 10L);
        createBiIsl(nodeC, nodeD, IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 1000, 5, 10L);
    }

    @Test
    public void nonGreedyMaxLatencyTest() throws Exception {
        // non-greedy algorithm can't find the closest path to max-latency param
        createNonGreedyMaxLatencyTopo();

        Flow flow = Flow.builder()
                .flowId("test flow")
                .srcSwitch(getSwitchById("00:01")).srcPort(15)
                .destSwitch(getSwitchById("00:06")).destPort(15)
                .bandwidth(500)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .pathComputationStrategy(PathComputationStrategy.MAX_LATENCY)
                .maxLatency(25L)
                .build();

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        GetPathsResult path = pathComputer.getPath(flow);
        assertNotNull(path);
        // best path is A-D-B-C-F and has 4 segments and 24 latency but non-greedy algorithm can find only path A-D-E-F
        // with 3 segments and total latency 20
        assertEquals(path.getForward().getSegments().size(), 3);
    }

    private void createNonGreedyMaxLatencyTopo() {
        // A - B - C
        //  \ /     \
        //   D - E - F
        String switchStart = "00:";
        int index = 1;

        Switch nodeA = createSwitch(switchStart + format("%02X", index++));
        Switch nodeB = createSwitch(switchStart + format("%02X", index++));
        Switch nodeC = createSwitch(switchStart + format("%02X", index++));
        Switch nodeD = createSwitch(switchStart + format("%02X", index++));
        Switch nodeE = createSwitch(switchStart + format("%02X", index++));
        Switch nodeF = createSwitch(switchStart + format("%02X", index));

        createBiIsl(nodeA, nodeB, IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 1000, 1, 1L);
        createBiIsl(nodeB, nodeC, IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 1000, 2, 7L);
        createBiIsl(nodeA, nodeD, IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 1000, 3, 3L);
        createBiIsl(nodeB, nodeD, IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 1000, 4, 10L);
        createBiIsl(nodeD, nodeE, IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 1000, 5, 5L);
        createBiIsl(nodeE, nodeF, IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 1000, 6, 12L);
        createBiIsl(nodeC, nodeF, IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 1000, 7, 4L);
    }

    @Test
    public void shouldUseSlowLinkInsidePath() throws Exception {
        createTwoLinksInsidePathTopo();

        Flow flow = Flow.builder()
                .flowId("test flow")
                .srcSwitch(getSwitchById("00:01")).srcPort(15)
                .destSwitch(getSwitchById("00:06")).destPort(15)
                .bandwidth(500)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .pathComputationStrategy(PathComputationStrategy.MAX_LATENCY)
                .maxLatency(30L)
                .build();

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        GetPathsResult path = pathComputer.getPath(flow);
        assertNotNull(path);
        assertEquals(path.getForward().getSegments().size(), 5);
        assertEquals(path.getForward().getLatency(), 14);
    }

    private void createTwoLinksInsidePathTopo() {
        // A - B - C - D - E - F
        //          \ /
        String switchStart = "00:";
        int index = 1;

        Switch nodeA = createSwitch(switchStart + format("%02X", index++));
        Switch nodeB = createSwitch(switchStart + format("%02X", index++));
        Switch nodeC = createSwitch(switchStart + format("%02X", index++));
        Switch nodeD = createSwitch(switchStart + format("%02X", index++));
        Switch nodeE = createSwitch(switchStart + format("%02X", index++));
        Switch nodeF = createSwitch(switchStart + format("%02X", index));

        createBiIsl(nodeA, nodeB, IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 1000, 1, 1L);
        createBiIsl(nodeB, nodeC, IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 1000, 2, 1L);
        createBiIsl(nodeC, nodeD, IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 1000, 3, 1L);
        createBiIsl(nodeD, nodeE, IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 1000, 4, 1L);
        createBiIsl(nodeE, nodeF, IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 1000, 5, 1L);
        createBiIsl(nodeC, nodeD, IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 1000, 6, 10L);
    }

    @Test
    public void maxLatencyShouldChooseCorrectWayTest() throws Exception {
        createThreeWaysTopo();

        Flow flow = Flow.builder()
                .flowId("test flow")
                .srcSwitch(getSwitchById("00:01")).srcPort(15)
                .destSwitch(getSwitchById("00:05")).destPort(15)
                .bandwidth(500)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .pathComputationStrategy(PathComputationStrategy.MAX_LATENCY)
                .maxLatency(25L)
                .build();

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        GetPathsResult path = pathComputer.getPath(flow);
        assertNotNull(path);
        assertEquals(path.getForward().getSegments().size(), 2);
        assertEquals(path.getForward().getSegments().get(1).getSrcSwitchId(), new SwitchId("00:03"));
    }

    private void createThreeWaysTopo() {
        //   / - B - \
        //  A  - C - E
        //   \ - D - /
        String switchStart = "00:";
        int index = 1;

        Switch nodeA = createSwitch(switchStart + format("%02X", index++));
        Switch nodeB = createSwitch(switchStart + format("%02X", index++));
        Switch nodeC = createSwitch(switchStart + format("%02X", index++));
        Switch nodeD = createSwitch(switchStart + format("%02X", index++));
        Switch nodeE = createSwitch(switchStart + format("%02X", index));

        createBiIsl(nodeA, nodeB, IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 1000, 1, 1L);
        createBiIsl(nodeB, nodeE, IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 1000, 2, 10L);
        createBiIsl(nodeA, nodeC, IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 1000, 3, 1L);
        createBiIsl(nodeC, nodeE, IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 1000, 4, 20L);
        createBiIsl(nodeA, nodeD, IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 1000, 5, 1L);
        createBiIsl(nodeD, nodeE, IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 1000, 6, 30L);
    }

}
