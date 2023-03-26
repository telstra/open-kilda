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

package org.openkilda.pce.impl;

import static java.lang.String.format;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.openkilda.model.PathComputationStrategy.COST;
import static org.openkilda.model.PathComputationStrategy.COST_AND_AVAILABLE_BANDWIDTH;
import static org.openkilda.model.PathComputationStrategy.LATENCY;
import static org.openkilda.model.PathComputationStrategy.MAX_LATENCY;

import org.openkilda.config.provider.PropertiesBasedConfigurationProvider;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathDirection;
import org.openkilda.model.Isl;
import org.openkilda.model.IslStatus;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.SwitchStatus;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.pce.AvailableNetworkFactory;
import org.openkilda.pce.GetPathsResult;
import org.openkilda.pce.Path;
import org.openkilda.pce.Path.Segment;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.PathComputerConfig;
import org.openkilda.pce.PathComputerFactory;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.pce.finder.BestWeightAndShortestPathFinder;
import org.openkilda.pce.finder.FailReasonType;
import org.openkilda.pce.finder.PathFinder;
import org.openkilda.pce.model.Edge;
import org.openkilda.pce.model.FindOneDirectionPathResult;
import org.openkilda.pce.model.Node;
import org.openkilda.pce.model.WeightFunction;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;

import com.google.common.collect.Lists;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.rules.ExpectedException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class InMemoryPathComputerBaseTest extends InMemoryGraphBasedTest {
    public static SwitchId SWITCH_1 = new SwitchId(1);
    public static SwitchId SWITCH_2 = new SwitchId(2);
    public static String TEST_FLOW_ID = "existed-flow";

    static SwitchRepository switchRepository;
    static SwitchPropertiesRepository switchPropertiesRepository;
    static IslRepository islRepository;
    static FlowRepository flowRepository;
    static FlowPathRepository flowPathRepository;

    static PathComputerConfig config;

    static AvailableNetworkFactory availableNetworkFactory;
    static PathComputerFactory pathComputerFactory;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setUpOnce() {
        switchRepository = repositoryFactory.createSwitchRepository();
        switchPropertiesRepository = repositoryFactory.createSwitchPropertiesRepository();
        islRepository = repositoryFactory.createIslRepository();
        flowRepository = repositoryFactory.createFlowRepository();
        flowPathRepository = repositoryFactory.createFlowPathRepository();

        config = new PropertiesBasedConfigurationProvider().getConfiguration(PathComputerConfig.class);

        availableNetworkFactory = new AvailableNetworkFactory(config, repositoryFactory);
        pathComputerFactory = new PathComputerFactory(config, availableNetworkFactory);
    }

    /**
     * See how it works with a large network.
     */
    @Test
    public void findPathOverLargeIslands() throws RecoverableException, UnroutableFlowException {
        final int expectedPathForwardSegmentSize = 139;

        createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 20, "08:", 1);

        for (int i = 0; i < 50; i++) {
            createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 20, "10:", 4 * i + 1);
            createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 20, "11:", 4 * i + 1);
            createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 20, "12:", 4 * i + 1);
            createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 20, "13:", 4 * i + 1);
        }
        for (int i = 0; i < 49; i++) {
            String prev = format("%02X", 4 * i + 4);
            String next = format("%02X", 4 * i + 5);
            connectDiamonds(new SwitchId("10:" + prev), new SwitchId("10:" + next), IslStatus.ACTIVE, 20, 50);
            connectDiamonds(new SwitchId("11:" + prev), new SwitchId("11:" + next), IslStatus.ACTIVE, 20, 50);
            connectDiamonds(new SwitchId("12:" + prev), new SwitchId("12:" + next), IslStatus.ACTIVE, 20, 50);
            connectDiamonds(new SwitchId("13:" + prev), new SwitchId("13:" + next), IslStatus.ACTIVE, 20, 50);
        }
        connectDiamonds(new SwitchId("10:99"), new SwitchId("11:22"), IslStatus.ACTIVE, 20, 50);
        connectDiamonds(new SwitchId("11:99"), new SwitchId("12:22"), IslStatus.ACTIVE, 20, 50);
        connectDiamonds(new SwitchId("12:99"), new SwitchId("13:22"), IslStatus.ACTIVE, 20, 50);
        connectDiamonds(new SwitchId("13:99"), new SwitchId("10:22"), IslStatus.ACTIVE, 20, 50);

        Switch srcSwitch1 = getSwitchById("10:01");
        Switch destSwitch1 = getSwitchById("11:03");

        // THIS ONE SHOULD WORK
        Flow f1 = new TestFlowBuilder()
                .srcSwitch(srcSwitch1)
                .destSwitch(destSwitch1)
                .bandwidth(0)
                .ignoreBandwidth(false)
                .build();

        PathComputer pathComputer = new InMemoryPathComputer(availableNetworkFactory,
                new BestWeightAndShortestPathFinder(200), config);
        GetPathsResult path = pathComputer.getPath(f1);

        assertNotNull(path);
        assertEquals(expectedPathForwardSegmentSize, path.getForward().getSegments().size());

        Switch srcSwitch2 = getSwitchById("08:01");
        Switch destSwitch2 = getSwitchById("11:04");

        // THIS ONE SHOULD FAIL
        Flow f2 = new TestFlowBuilder()
                .srcSwitch(srcSwitch2)
                .destSwitch(destSwitch2)
                .bandwidth(0)
                .ignoreBandwidth(false)
                .build();

        thrown.expect(UnroutableFlowException.class);

        pathComputer.getPath(f2);
    }

    /**
     * This verifies that the getPath in PathComputer returns what we expect. Essentially, this tests the additional
     * logic wrt taking the results of the algo and convert to something installable.
     */
    @Test
    public void verifyConversionToPair() throws UnroutableFlowException, RecoverableException {
        createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 20, "09:", 1);

        Switch srcSwitch = getSwitchById("09:01");
        Switch destSwitch = getSwitchById("09:04");

        Flow flow = new TestFlowBuilder()
                .srcSwitch(srcSwitch)
                .destSwitch(destSwitch)
                .bandwidth(10)
                .ignoreBandwidth(false)
                .build();

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        GetPathsResult result = pathComputer.getPath(flow);
        assertNotNull(result);
        // ensure start/end switches match
        List<Path.Segment> left = result.getForward().getSegments();
        assertEquals(srcSwitch.getSwitchId(), left.get(0).getSrcSwitchId());
        assertEquals(destSwitch.getSwitchId(), left.get(left.size() - 1).getDestSwitchId());

        List<Path.Segment> right = result.getReverse().getSegments();
        assertEquals(destSwitch.getSwitchId(), right.get(0).getSrcSwitchId());
        assertEquals(srcSwitch.getSwitchId(), right.get(right.size() - 1).getDestSwitchId());
    }

    /**
     * Checks that existed flow should always have available path even there is only links with 0 available bandwidth.
     */
    @Test
    public void shouldAlwaysFindPathForExistedFlow() throws RecoverableException, UnroutableFlowException {
        String flowId = "flow-A1:01-A1:03";
        long bandwidth = 1000;

        createLinearTopoWithFlowSegments(10, "A1:", 1, 0L,
                flowId, bandwidth);

        Switch srcSwitch = getSwitchById("A1:01");
        Switch destSwitch = getSwitchById("A1:03");

        Flow flow = new TestFlowBuilder()
                .flowId(flowId)
                .srcSwitch(srcSwitch)
                .destSwitch(destSwitch)
                .bandwidth(bandwidth)
                .ignoreBandwidth(false)
                .build();
        Flow oldFlow = flowRepository.findById(flowId).orElseThrow(() -> new AssertionError("Flow not found"));

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        GetPathsResult result = pathComputer.getPath(flow, oldFlow.getPathIds(), false);

        assertThat(result.getForward().getSegments(), Matchers.hasSize(2));
        assertThat(result.getReverse().getSegments(), Matchers.hasSize(2));
    }

    /**
     * Tests the case when we try to increase bandwidth of the flow and there is no available bandwidth left.
     */
    @Test
    public void shouldNotFindPathForExistedFlowAndIncreasedBandwidth()
            throws RecoverableException, UnroutableFlowException {
        String flowId = "flow-A1:01-A1:03";
        long originFlowBandwidth = 1000L;

        // create network, all links have available bandwidth 0
        createLinearTopoWithFlowSegments(10, "A1:", 1, 0,
                flowId, originFlowBandwidth);

        Switch srcSwitch = getSwitchById("A1:01");
        Switch destSwitch = getSwitchById("A1:03");

        Flow flow = new TestFlowBuilder()
                .flowId(flowId)
                .srcSwitch(srcSwitch)
                .destSwitch(destSwitch)
                .bandwidth(originFlowBandwidth)
                .ignoreBandwidth(false)
                .build();

        long updatedFlowBandwidth = originFlowBandwidth + 1;
        flow.setBandwidth(updatedFlowBandwidth);

        PathComputer pathComputer = pathComputerFactory.getPathComputer();

        Exception exception = Assertions.assertThrows(UnroutableFlowException.class, () -> {
            pathComputer.getPath(flow, flow.getPathIds(), false);
        });
        MatcherAssert.assertThat(exception.getMessage(), containsString(FailReasonType.MAX_BANDWIDTH.toString()));
    }

    @Test
    public void getNPathsCostSortByBandwidth() throws RecoverableException, UnroutableFlowException {
        List<Path> foundPaths = mockPathComputerWithoutMaxWeight().getNPaths(SWITCH_1, SWITCH_2, 10, null, COST,
                Duration.ZERO, Duration.ZERO);

        assertEquals(5, foundPaths.size());
        assertSortedByBandwidthAndLatency(foundPaths);
    }

    @Test
    public void getNPathsCostAndAvailableBandwidthSortByBandwidth()
            throws RecoverableException, UnroutableFlowException {
        List<Path> foundPaths = mockPathComputerWithoutMaxWeight().getNPaths(
                SWITCH_1, SWITCH_2, 10, null, COST_AND_AVAILABLE_BANDWIDTH, Duration.ZERO, Duration.ZERO);

        assertEquals(5, foundPaths.size());
        assertSortedByBandwidthAndLatency(foundPaths);
    }

    @Test
    public void getNPathsLatencySortByLatency() throws RecoverableException, UnroutableFlowException {
        List<Path> foundPaths = mockPathComputerWithoutMaxWeight().getNPaths(
                SWITCH_1, SWITCH_2, 10, null, LATENCY, Duration.ZERO, Duration.ZERO);

        assertEquals(5, foundPaths.size());
        assertSortedByLatencyAndBandwidth(foundPaths);
    }

    @Test
    public void getNPathsMaxLatencyWithNullMaxLatencySortByLatency()
            throws RecoverableException, UnroutableFlowException {
        // We used MAX_LATENCY strategy with null max latency param. In this case LATENCY strategy must be used
        // We check it by using PathComputerWithoutMaxWeight() mock instead of mockPathComputerWithMaxWeight()
        List<Path> foundPaths = mockPathComputerWithoutMaxWeight().getNPaths(
                SWITCH_1, SWITCH_2, 10, null, MAX_LATENCY, null, null);

        assertEquals(5, foundPaths.size());
        assertSortedByLatencyAndBandwidth(foundPaths);
    }

    @Test
    public void getNPathsMaxLatencyWithZeroMaxLatencySortByLatency()
            throws RecoverableException, UnroutableFlowException {
        // We used MAX_LATENCY strategy with 0 max latency param. In this case LATENCY strategy must be used
        // We check it by using PathComputerWithoutMaxWeight() mock instead of mockPathComputerWithMaxWeight()
        List<Path> foundPaths = mockPathComputerWithoutMaxWeight().getNPaths(
                SWITCH_1, SWITCH_2, 10, null, MAX_LATENCY, Duration.ZERO, null);

        assertEquals(5, foundPaths.size());
        assertSortedByLatencyAndBandwidth(foundPaths);
    }

    @Test
    public void getNPathsMaxLatencySortByLatency() throws RecoverableException, UnroutableFlowException {
        List<Path> foundPaths = mockPathComputerWithMaxWeight().getNPaths(
                SWITCH_1, SWITCH_2, 10, null, MAX_LATENCY, Duration.ofNanos(1000L), Duration.ZERO);

        assertEquals(5, foundPaths.size());
        assertSortedByLatencyAndBandwidth(foundPaths);
    }

    private PathComputer mockPathComputerWithoutMaxWeight() throws UnroutableFlowException {
        PathFinder pathFinderMock = mock(PathFinder.class);
        when(pathFinderMock.findNPathsBetweenSwitches(
                any(AvailableNetwork.class), eq(SWITCH_1), eq(SWITCH_2), anyInt(), any(WeightFunction.class)))
                .thenReturn(getPaths());
        return new InMemoryPathComputer(availableNetworkFactory, pathFinderMock, config);
    }

    private PathComputer mockPathComputerWithMaxWeight() throws UnroutableFlowException {
        PathFinder pathFinderMock = mock(PathFinder.class);
        when(pathFinderMock.findNPathsBetweenSwitches(
                any(AvailableNetwork.class), eq(SWITCH_1), eq(SWITCH_2), anyInt(), any(WeightFunction.class),
                anyLong(), anyLong()))
                .thenReturn(getPaths());
        return new InMemoryPathComputer(availableNetworkFactory, pathFinderMock, config);
    }

    private List<FindOneDirectionPathResult> getPaths() {
        List<Edge> path1 = createPath(10, 5);
        List<Edge> path2 = createPath(10, 4);
        List<Edge> path3 = createPath(15, 5);
        List<Edge> path4 = createPath(100, 100);
        List<Edge> path5 = createPath(1, 1);

        return Lists.newArrayList(
                new FindOneDirectionPathResult(path1, false),
                new FindOneDirectionPathResult(path2, false),
                new FindOneDirectionPathResult(path3, false),
                new FindOneDirectionPathResult(path4, false),
                new FindOneDirectionPathResult(path5, false));
    }

    private void assertSortedByBandwidthAndLatency(List<Path> paths) {
        for (int i = 1; i < paths.size(); i++) {
            Path prev = paths.get(i - 1);
            Path cur = paths.get(i);
            assertTrue(prev.getMinAvailableBandwidth() >= cur.getMinAvailableBandwidth());
            if (prev.getMinAvailableBandwidth() == cur.getMinAvailableBandwidth()) {
                assertTrue(prev.getLatency() <= cur.getLatency());
            }
        }
    }

    private void assertSortedByLatencyAndBandwidth(List<Path> paths) {
        for (int i = 1; i < paths.size(); i++) {
            Path prev = paths.get(i - 1);
            Path cur = paths.get(i);
            assertTrue(prev.getLatency() <= cur.getLatency());
            if (prev.getLatency() == cur.getLatency()) {
                assertTrue(prev.getMinAvailableBandwidth() >= cur.getMinAvailableBandwidth());
            }
        }
    }

    private ArrayList<Edge> createPath(long availableBandwidth, long latency) {
        return Lists.newArrayList(Edge.builder()
                .srcSwitch(new Node(SWITCH_1, ""))
                .destSwitch(new Node(SWITCH_2, ""))
                .availableBandwidth(availableBandwidth)
                .latency(latency)
                .build());
    }

    void addPathSegments(FlowPath flowPath, Path path) {
        path.getSegments().forEach(segment ->
                addPathSegment(flowPath, switchRepository.findById(segment.getSrcSwitchId()).get(),
                        switchRepository.findById(segment.getDestSwitchId()).get(),
                        segment.getSrcPort(), segment.getDestPort()));
    }

    private void createLinearTopoWithFlowSegments(int cost, String switchStart, int startIndex, long linkBw,
                                                  String flowId, long flowBandwidth) {
        // A - B - C
        int index = startIndex;

        Switch nodeA = createSwitch(switchStart + format("%02X", index++));
        Switch nodeB = createSwitch(switchStart + format("%02X", index++));
        Switch nodeC = createSwitch(switchStart + format("%02X", index));

        createIsl(nodeA, nodeB, IslStatus.ACTIVE, IslStatus.ACTIVE, cost, linkBw, 5);
        createIsl(nodeB, nodeC, IslStatus.ACTIVE, IslStatus.ACTIVE, cost, linkBw, 6);
        createIsl(nodeC, nodeB, IslStatus.ACTIVE, IslStatus.ACTIVE, cost, linkBw, 6);
        createIsl(nodeB, nodeA, IslStatus.ACTIVE, IslStatus.ACTIVE, cost, linkBw, 5);

        Flow flow = Flow.builder()
                .flowId(flowId)
                .srcSwitch(nodeA)
                .destSwitch(nodeC)
                .build();

        FlowPath forwardPath = FlowPath.builder()
                .pathId(new PathId(UUID.randomUUID().toString()))
                .srcSwitch(nodeA)
                .destSwitch(nodeC)
                .bandwidth(flowBandwidth)
                .build();
        addPathSegment(forwardPath, nodeA, nodeB, 5, 5);
        addPathSegment(forwardPath, nodeB, nodeC, 6, 6);
        flow.setForwardPath(forwardPath);

        FlowPath reversePath = FlowPath.builder()
                .pathId(new PathId(UUID.randomUUID().toString()))
                .srcSwitch(nodeC)
                .destSwitch(nodeA)
                .bandwidth(flowBandwidth)
                .build();
        addPathSegment(reversePath, nodeC, nodeB, 6, 6);
        addPathSegment(reversePath, nodeB, nodeA, 5, 5);
        flow.setReversePath(reversePath);

        flowRepository.add(flow);
    }

    // A - B - D    and A-B-D is used in flow diverse group
    //   + C +
    void createDiamondWithDiversity() {
        Switch nodeA = createSwitch("00:0A");
        Switch nodeB = createSwitch("00:0B");
        Switch nodeC = createSwitch("00:0C");
        Switch nodeD = createSwitch("00:0D");

        IslStatus status = IslStatus.ACTIVE;
        int cost = 100;
        createIsl(nodeA, nodeB, status, status, cost, 1000, 1);
        createIsl(nodeA, nodeC, status, status, cost * 2, 1000, 2);
        createIsl(nodeB, nodeD, status, status, cost, 1000, 3);
        createIsl(nodeC, nodeD, status, status, cost * 2, 1000, 4);
        createIsl(nodeB, nodeA, status, status, cost, 1000, 1);
        createIsl(nodeC, nodeA, status, status, cost * 2, 1000, 2);
        createIsl(nodeD, nodeB, status, status, cost, 1000, 3);
        createIsl(nodeD, nodeC, status, status, cost * 2, 1000, 4);

        int bandwith = 10;
        String flowId = "existed-flow";

        String diverseGroupId = "diverse";

        Flow flow = Flow.builder()
                .flowId(flowId)
                .srcSwitch(nodeA).srcPort(15)
                .destSwitch(nodeD).destPort(16)
                .diverseGroupId(diverseGroupId)
                .bandwidth(bandwith)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .build();

        FlowPath forwardPath = FlowPath.builder()
                .pathId(new PathId(UUID.randomUUID().toString()))
                .srcSwitch(nodeA)
                .destSwitch(nodeD)
                .bandwidth(bandwith)
                .build();
        addPathSegment(forwardPath, nodeA, nodeB, 1, 1);
        addPathSegment(forwardPath, nodeB, nodeD, 3, 3);
        flow.setForwardPath(forwardPath);

        FlowPath reversePath = FlowPath.builder()
                .pathId(new PathId(UUID.randomUUID().toString()))
                .srcSwitch(nodeD)
                .destSwitch(nodeA)
                .bandwidth(bandwith)
                .build();
        addPathSegment(reversePath, nodeD, nodeB, 3, 3);
        addPathSegment(reversePath, nodeB, nodeA, 1, 1);
        flow.setReversePath(reversePath);

        flowRepository.add(flow);
    }

    // A - B - D    and A-B-D is used in flow affinity group
    //   \   /
    //     C
    void createDiamondWithAffinity() {
        Switch nodeA = createSwitch("00:0A");
        Switch nodeB = createSwitch("00:0B");
        Switch nodeC = createSwitch("00:0C");
        Switch nodeD = createSwitch("00:0D");

        IslStatus status = IslStatus.ACTIVE;
        int cost = 100;
        createIsl(nodeA, nodeB, status, status, cost * 2, 1000, 1, cost * 2);
        createIsl(nodeA, nodeC, status, status, cost, 1000, 2, cost);
        createIsl(nodeB, nodeD, status, status, cost * 2, 1000, 3, cost * 2);
        createIsl(nodeC, nodeD, status, status, cost, 1000, 4, cost);
        createIsl(nodeB, nodeA, status, status, cost * 2, 1000, 1, cost * 2);
        createIsl(nodeC, nodeA, status, status, cost, 1000, 2, cost);
        createIsl(nodeD, nodeB, status, status, cost * 2, 1000, 3, cost * 2);
        createIsl(nodeD, nodeC, status, status, cost, 1000, 4, cost);

        int bandwith = 10;

        Flow flow = Flow.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(nodeA).srcPort(15)
                .destSwitch(nodeD).destPort(16)
                .affinityGroupId(TEST_FLOW_ID)
                .bandwidth(bandwith)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .build();

        FlowPath forwardPath = FlowPath.builder()
                .pathId(new PathId(UUID.randomUUID().toString()))
                .srcSwitch(nodeA)
                .destSwitch(nodeD)
                .bandwidth(bandwith)
                .cookie(new FlowSegmentCookie(1L).toBuilder().direction(FlowPathDirection.FORWARD).build())
                .build();
        addPathSegment(forwardPath, nodeA, nodeB, 1, 1);
        addPathSegment(forwardPath, nodeB, nodeD, 3, 3);
        flow.setForwardPath(forwardPath);

        FlowPath reversePath = FlowPath.builder()
                .pathId(new PathId(UUID.randomUUID().toString()))
                .srcSwitch(nodeD)
                .destSwitch(nodeA)
                .bandwidth(bandwith)
                .cookie(new FlowSegmentCookie(1L).toBuilder().direction(FlowPathDirection.REVERSE).build())
                .build();
        addPathSegment(reversePath, nodeD, nodeB, 3, 3);
        addPathSegment(reversePath, nodeB, nodeA, 1, 1);
        flow.setReversePath(reversePath);

        flowRepository.add(flow);
    }

    // A - B - C - D    and A-B-C-D is used in flow affinity group
    // |   |   |   |
    // E   F   \   |
    // |   |     \ |
    // G - H - - - Z
    void createTestTopologyForAffinityTesting() {
        final Switch nodeA = createSwitch("00:0A");
        final Switch nodeB = createSwitch("00:0B");
        final Switch nodeC = createSwitch("00:0C");
        final Switch nodeD = createSwitch("00:0D");
        final Switch nodeE = createSwitch("00:0E");
        final Switch nodeF = createSwitch("00:0F");
        final Switch nodeG = createSwitch("00:01");
        final Switch nodeH = createSwitch("00:02");
        final Switch nodeZ = createSwitch("00:03");

        IslStatus status = IslStatus.ACTIVE;
        int cost = 100;
        long bw = 1000;
        long latency = 1000;
        createBiIsl(nodeA, nodeB, status, status, cost, bw, 1, latency);
        createBiIsl(nodeB, nodeC, status, status, cost, bw, 2, latency);
        createBiIsl(nodeC, nodeD, status, status, cost, bw, 3, latency);
        createBiIsl(nodeA, nodeE, status, status, cost, bw, 4, latency);
        createBiIsl(nodeE, nodeG, status, status, cost, bw, 5, latency);
        createBiIsl(nodeG, nodeH, status, status, cost, bw, 6, latency);
        createBiIsl(nodeB, nodeF, status, status, cost, bw, 7, latency);
        createBiIsl(nodeF, nodeH, status, status, cost, bw, 8, latency);
        createBiIsl(nodeH, nodeZ, status, status, cost, bw, 9, latency);
        createBiIsl(nodeC, nodeZ, status, status, cost, bw, 10, latency);
        createBiIsl(nodeD, nodeZ, status, status, cost, bw, 11, latency);

        int bandwidth = 10;

        Flow flow = Flow.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(nodeA).srcPort(15)
                .destSwitch(nodeD).destPort(16)
                .affinityGroupId(TEST_FLOW_ID)
                .bandwidth(bandwidth)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .build();

        FlowPath forwardPath = FlowPath.builder()
                .pathId(new PathId(UUID.randomUUID().toString()))
                .srcSwitch(nodeA)
                .destSwitch(nodeD)
                .bandwidth(bandwidth)
                .cookie(new FlowSegmentCookie(1L).toBuilder().direction(FlowPathDirection.FORWARD).build())
                .build();
        flow.setForwardPath(forwardPath);
        addPathSegment(forwardPath, nodeA, nodeB, 1, 1);
        addPathSegment(forwardPath, nodeB, nodeC, 2, 2);
        addPathSegment(forwardPath, nodeC, nodeD, 3, 3);

        FlowPath reversePath = FlowPath.builder()
                .pathId(new PathId(UUID.randomUUID().toString()))
                .srcSwitch(nodeD)
                .destSwitch(nodeA)
                .bandwidth(bandwidth)
                .cookie(new FlowSegmentCookie(1L).toBuilder().direction(FlowPathDirection.REVERSE).build())
                .build();
        flow.setReversePath(reversePath);
        addPathSegment(reversePath, nodeD, nodeC, 3, 3);
        addPathSegment(reversePath, nodeC, nodeB, 2, 2);
        addPathSegment(reversePath, nodeB, nodeA, 1, 1);

        flowRepository.add(flow);
    }

    void shouldFindAffinityPathOnDiamond(PathComputationStrategy pathComputationStrategy) throws Exception {
        createDiamondWithAffinity();

        Flow flow = Flow.builder()
                .flowId("new-flow")
                .affinityGroupId(TEST_FLOW_ID)
                .bandwidth(10)
                .srcSwitch(getSwitchById("00:0A"))
                .destSwitch(getSwitchById("00:0D"))
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .pathComputationStrategy(pathComputationStrategy)
                .build();
        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        GetPathsResult affinityPath = pathComputer.getPath(flow);

        List<Segment> segments = affinityPath.getForward().getSegments();
        assertEquals(new SwitchId("00:0B"), segments.get(1).getSrcSwitchId());
        assertEquals(new SwitchId("00:0B"), segments.get(0).getDestSwitchId());
    }

    void affinityPathShouldPreferIslsUsedByMainPath(PathComputationStrategy pathComputationStrategy) throws Exception {
        createTestTopologyForAffinityTesting();

        Flow flow = Flow.builder()
                .flowId("new-flow")
                .affinityGroupId(TEST_FLOW_ID)
                .bandwidth(10)
                .srcSwitch(getSwitchById("00:0E"))
                .destSwitch(getSwitchById("00:0F"))
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .pathComputationStrategy(pathComputationStrategy)
                .build();
        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        GetPathsResult affinityPath = pathComputer.getPath(flow);

        List<Segment> segments = affinityPath.getForward().getSegments();
        assertEquals(3, segments.size());
        assertEquals(new SwitchId("00:0E"), segments.get(0).getSrcSwitchId());
        assertEquals(new SwitchId("00:0A"), segments.get(1).getSrcSwitchId());
        assertEquals(new SwitchId("00:0B"), segments.get(2).getSrcSwitchId());

        assertEquals(new SwitchId("00:0A"), segments.get(0).getDestSwitchId());
        assertEquals(new SwitchId("00:0B"), segments.get(1).getDestSwitchId());
        assertEquals(new SwitchId("00:0F"), segments.get(2).getDestSwitchId());
    }

    void affinityPathShouldSplitAsCloseAsPossibleToDestination(PathComputationStrategy pathComputationStrategy)
            throws Exception {
        createTestTopologyForAffinityTesting();

        Flow flow = Flow.builder()
                .flowId("new-flow")
                .affinityGroupId(TEST_FLOW_ID)
                .bandwidth(10)
                .srcSwitch(getSwitchById("00:0A"))
                .destSwitch(getSwitchById("00:03"))
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .pathComputationStrategy(pathComputationStrategy)
                .build();
        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        GetPathsResult affinityPath = pathComputer.getPath(flow);

        List<Segment> segments = affinityPath.getForward().getSegments();
        assertEquals(3, segments.size());
        assertEquals(new SwitchId("00:0A"), segments.get(0).getSrcSwitchId());
        assertEquals(new SwitchId("00:0B"), segments.get(1).getSrcSwitchId());
        assertEquals(new SwitchId("00:0C"), segments.get(2).getSrcSwitchId());

        assertEquals(new SwitchId("00:0B"), segments.get(0).getDestSwitchId());
        assertEquals(new SwitchId("00:0C"), segments.get(1).getDestSwitchId());
        assertEquals(new SwitchId("00:03"), segments.get(2).getDestSwitchId());
    }

    void affinityOvercomeDiversity(PathComputationStrategy pathComputationStrategy)
            throws Exception {
        createTestTopologyForAffinityTesting();  // contains flow (A)-(B)-(C)-(D)

        String diversityGroup = "some-other-flow-id";
        Flow flowA = flowRepository.findById(TEST_FLOW_ID)
                .orElseThrow(() -> new AssertionError(String.format(
                        "Pre creation of flow \"%s\" expectation don't met", TEST_FLOW_ID)));
        flowA.setDiverseGroupId(diversityGroup);

        Flow flowB = Flow.builder()
                .flowId("subject")
                .affinityGroupId(TEST_FLOW_ID)
                .diverseGroupId(diversityGroup)
                .bandwidth(10)
                .srcSwitch(getSwitchById("00:0A"))
                .destSwitch(getSwitchById("00:03"))
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .pathComputationStrategy(pathComputationStrategy)
                .build();
        Assert.assertNotEquals(flowA.getFlowId(), flowB.getFlowId());
        Assert.assertEquals(flowA.getAffinityGroupId(), flowB.getAffinityGroupId());

        flowRepository.findById(TEST_FLOW_ID)
                .ifPresent(entry -> Assert.assertEquals(diversityGroup, entry.getDiverseGroupId()));

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        GetPathsResult affinityPath = pathComputer.getPath(flowB);

        List<Segment> segments = affinityPath.getForward().getSegments();
        assertEquals(3, segments.size());
        assertEquals(new SwitchId("00:0A"), segments.get(0).getSrcSwitchId());
        assertEquals(new SwitchId("00:0B"), segments.get(0).getDestSwitchId());

        assertEquals(new SwitchId("00:0B"), segments.get(1).getSrcSwitchId());
        assertEquals(new SwitchId("00:0C"), segments.get(1).getDestSwitchId());

        assertEquals(new SwitchId("00:0C"), segments.get(2).getSrcSwitchId());
        assertEquals(new SwitchId("00:03"), segments.get(2).getDestSwitchId());
    }

    void createDiamond(IslStatus pathBstatus, IslStatus pathCstatus, int pathBcost, int pathCcost,
                       String switchStart, int startIndex) {
        createDiamond(pathBstatus, pathCstatus, pathBcost, pathCcost, switchStart, startIndex, 5L, 5L);
    }

    void createDiamond(IslStatus pathBstatus, IslStatus pathCstatus, int pathBcost, int pathCcost,
                       String switchStart, int startIndex, long pathBlatency, long pathClatency) {
        // A - B - D
        //   + C +
        int index = startIndex;

        Switch nodeA = createSwitch(switchStart + format("%02X", index++));
        Switch nodeB = createSwitch(switchStart + format("%02X", index++));
        Switch nodeC = createSwitch(switchStart + format("%02X", index++));
        Switch nodeD = createSwitch(switchStart + format("%02X", index));

        IslStatus actual = (pathBstatus == IslStatus.ACTIVE) && (pathCstatus == IslStatus.ACTIVE)
                ? IslStatus.ACTIVE : IslStatus.INACTIVE;
        createIsl(nodeA, nodeB, pathBstatus, actual, pathBcost, 1000, 5, pathBlatency);
        createIsl(nodeA, nodeC, pathCstatus, actual, pathCcost, 1000, 6, pathClatency);
        createIsl(nodeB, nodeD, pathBstatus, actual, pathBcost, 1000, 6, pathBlatency);
        createIsl(nodeC, nodeD, pathCstatus, actual, pathCcost, 1000, 5, pathClatency);
        createIsl(nodeB, nodeA, pathBstatus, actual, pathBcost, 1000, 5, pathBlatency);
        createIsl(nodeC, nodeA, pathCstatus, actual, pathCcost, 1000, 6, pathClatency);
        createIsl(nodeD, nodeB, pathBstatus, actual, pathBcost, 1000, 6, pathBlatency);
        createIsl(nodeD, nodeC, pathCstatus, actual, pathCcost, 1000, 5, pathClatency);
    }

    private void connectDiamonds(SwitchId switchA, SwitchId switchB, IslStatus status, int cost, int port) {
        // A - B - D
        //   + C +
        Switch nodeA = switchRepository.findById(switchA).get();
        Switch nodeB = switchRepository.findById(switchB).get();
        createIsl(nodeA, nodeB, status, status, cost, 1000, port);
        createIsl(nodeB, nodeA, status, status, cost, 1000, port);
    }

    void createTriangleTopo(IslStatus pathABstatus, int pathABcost, int pathCcost,
                            String switchStart, int startIndex) {
        // A - B
        // + C +
        int index = startIndex;

        Switch nodeA = createSwitch(switchStart + format("%02X", index++));
        Switch nodeB = createSwitch(switchStart + format("%02X", index++));
        Switch nodeC = createSwitch(switchStart + format("%02X", index));

        createIsl(nodeA, nodeB, pathABstatus, pathABstatus, pathABcost, 1000, 5, 1000L);
        createIsl(nodeB, nodeA, pathABstatus, pathABstatus, pathABcost, 1000, 5, 1000L);
        createIsl(nodeA, nodeC, IslStatus.ACTIVE, IslStatus.ACTIVE, pathCcost, 1000, 6, 100L);
        createIsl(nodeC, nodeA, IslStatus.ACTIVE, IslStatus.ACTIVE, pathCcost, 1000, 6, 100L);
        createIsl(nodeC, nodeB, IslStatus.ACTIVE, IslStatus.ACTIVE, pathCcost, 1000, 7, 100L);
        createIsl(nodeB, nodeC, IslStatus.ACTIVE, IslStatus.ACTIVE, pathCcost, 1000, 7, 100L);
    }

    Switch createSwitch(String name) {
        Switch sw = Switch.builder().switchId(new SwitchId(name)).status(SwitchStatus.ACTIVE)
                .build();
        switchRepository.add(sw);

        SwitchProperties switchProperties = SwitchProperties.builder().switchObj(sw)
                .supportedTransitEncapsulation(SwitchProperties.DEFAULT_FLOW_ENCAPSULATION_TYPES).build();
        switchPropertiesRepository.add(switchProperties);
        return sw;
    }

    private void createIsl(Switch srcSwitch, Switch dstSwitch, IslStatus status, IslStatus actual,
                           int cost, long bw, int port) {
        createIsl(srcSwitch, dstSwitch, status, actual, cost, bw, port, 5L);
    }

    void createIsl(Switch srcSwitch, Switch dstSwitch, IslStatus status, IslStatus actual,
                   int cost, long bw, int port, long latency) {
        Isl.IslBuilder isl = Isl.builder()
                .srcSwitch(srcSwitch)
                .destSwitch(dstSwitch)
                .status(status)
                .actualStatus(actual);
        if (cost >= 0) {
            isl.cost(cost);
        }
        isl.availableBandwidth(bw);
        isl.latency(latency);
        isl.srcPort(port);
        isl.destPort(port);

        islRepository.add(isl.build());
    }

    void createBiIsl(Switch srcSwitch, Switch dstSwitch, IslStatus status, IslStatus actual,
                     int cost, long bw, int port, long latency) {
        createIsl(srcSwitch, dstSwitch, status, actual, cost, bw, port, latency);
        createIsl(dstSwitch, srcSwitch, status, actual, cost, bw, port, latency);
    }

    private void addPathSegment(FlowPath flowPath, Switch src, Switch dst, int srcPort, int dstPort) {
        PathSegment ps = PathSegment.builder()
                .pathId(flowPath.getPathId())
                .srcSwitch(src)
                .destSwitch(dst)
                .srcPort(srcPort)
                .destPort(dstPort)
                .latency(null)
                .build();
        List<PathSegment> segments = new ArrayList<>(flowPath.getSegments());
        segments.add(ps);
        flowPath.setSegments(segments);
    }

    Switch getSwitchById(String id) {
        return switchRepository.findById(new SwitchId(id))
                .orElseThrow(() -> new IllegalArgumentException(format("Switch %s not found", id)));
    }

    @Test
    public void shouldGetYPoint2FlowPaths() {
        FlowPath flowPathA = buildFlowPath("flow_path_a", 1, 2, 3);
        FlowPath flowPathB = buildFlowPath("flow_path_b", 1, 2, 4);
        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        SwitchId result = pathComputer.getIntersectionPoint(SWITCH_1, flowPathA, flowPathB);
        assertEquals(new SwitchId(2), result);
    }

    @Test
    public void shouldGetYPoint2FlowPathsShorter() {
        FlowPath flowPathA = buildFlowPath("flow_path_a", 1, 2);
        FlowPath flowPathB = buildFlowPath("flow_path_b", 1, 2, 3);
        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        SwitchId result = pathComputer.getIntersectionPoint(SWITCH_1, flowPathA, flowPathB);
        assertEquals(SWITCH_2, result);
    }

    @Test
    public void shouldGetYPoint2ReverseFlowPaths() {
        FlowPath flowPathA = buildFlowPath("flow_path_a", 2, 3);
        FlowPath flowPathB = buildFlowPath("flow_path_b", 1, 2, 3);
        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        SwitchId result = pathComputer.getIntersectionPoint(new SwitchId(3), flowPathA, flowPathB);
        assertEquals(SWITCH_2, result);
    }

    @Test
    public void shouldGetYPoint3FlowPaths() {
        FlowPath flowPathA = buildFlowPath("flow_path_a", 1, 2, 3, 7);
        FlowPath flowPathB = buildFlowPath("flow_path_b", 1, 2, 3, 4, 5);
        FlowPath flowPathC = buildFlowPath("flow_path_c", 1, 2, 3, 4, 6);

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        SwitchId result = pathComputer.getIntersectionPoint(SWITCH_1, flowPathA, flowPathB, flowPathC);
        assertEquals(new SwitchId(3), result);
    }

    @Test
    public void shouldGetYPoint2FlowPathsWithSameEndpoints() {
        FlowPath flowPathA = buildFlowPath("flow_path_a", 1, 2, 3);
        FlowPath flowPathB = buildFlowPath("flow_path_b", 1, 2, 3);
        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        SwitchId result = pathComputer.getIntersectionPoint(SWITCH_1, flowPathA, flowPathB);
        assertEquals(new SwitchId(3), result);
    }

    @Test
    public void shouldGetYPoint2DifferentFlowPathsWithSameEndpoints() {
        FlowPath flowPathA = buildFlowPath("flow_path_a", 1, 2, 3);
        FlowPath flowPathB = buildFlowPath("flow_path_b", 1, 4, 3);
        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        SwitchId result = pathComputer.getIntersectionPoint(SWITCH_1, flowPathA, flowPathB);
        assertEquals(SWITCH_1, result);
    }

    @Test
    public void shouldConvertFlowPathsToSwitchLists() {
        int[] switchIdsA = {1, 2, 3};
        int[] switchIdsB = {1, 2, 4, 5};
        FlowPath flowPathA = buildFlowPath("flow_path_a", switchIdsA);
        FlowPath flowPathB = buildFlowPath("flow_path_b", switchIdsB);

        InMemoryPathComputer pathComputer = new InMemoryPathComputer(availableNetworkFactory,
                new BestWeightAndShortestPathFinder(config.getMaxAllowedDepth()), config);
        List<LinkedList<SwitchId>> result =
                pathComputer.convertFlowPathsToSwitchLists(SWITCH_1, flowPathA, flowPathB);
        assertEquals(2, result.size());
        assertArrayEquals(switchIdsA, result.get(0).stream().mapToInt(id -> (int) id.toLong()).toArray());
        assertArrayEquals(switchIdsB, result.get(1).stream().mapToInt(id -> (int) id.toLong()).toArray());
    }

    @Test
    public void shouldThrowSharedSwitchIsNotEndpointSwitch() {
        FlowPath flowPathA = buildFlowPath("flow_path_a", 6, 2, 3);
        FlowPath flowPathB = buildFlowPath("flow_path_b", 1, 2, 4, 5);


        InMemoryPathComputer pathComputer = new InMemoryPathComputer(availableNetworkFactory,
                new BestWeightAndShortestPathFinder(config.getMaxAllowedDepth()), config);
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                pathComputer.convertFlowPathsToSwitchLists(SWITCH_1, flowPathA, flowPathB));

        assertEquals("Shared switch '00:00:00:00:00:00:00:01' is not an endpoint switch for path 'flow_path_a'",
                exception.getMessage());
    }

    @Test
    public void shouldThrowPathHasNoSegmentsWhenSegmentsIsNull() {
        FlowPath flowPathA = FlowPath.builder()
                .pathId(new PathId("flow_path_a"))
                .srcSwitch(Switch.builder().switchId(new SwitchId(1)).build())
                .destSwitch(Switch.builder().switchId(new SwitchId(3)).build())
                .build();
        FlowPath flowPathB = buildFlowPath("flow_path_b", 1, 2, 4, 5);

        InMemoryPathComputer pathComputer = new InMemoryPathComputer(availableNetworkFactory,
                new BestWeightAndShortestPathFinder(config.getMaxAllowedDepth()), config);
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                pathComputer.convertFlowPathsToSwitchLists(SWITCH_1, flowPathA, flowPathB));

        assertEquals("The path 'flow_path_a' has no path segments", exception.getMessage());
    }

    @Test
    public void shouldThrowPathHasNoSegmentsWhenSegmentsIsEmpty() {
        FlowPath flowPathA = FlowPath.builder()
                .pathId(new PathId("flow_path_a"))
                .srcSwitch(Switch.builder().switchId(new SwitchId(1)).build())
                .destSwitch(Switch.builder().switchId(new SwitchId(3)).build())
                .segments(new ArrayList<>())
                .build();
        FlowPath flowPathB = buildFlowPath("flow_path_b", 1, 2, 4, 5);

        InMemoryPathComputer pathComputer = new InMemoryPathComputer(availableNetworkFactory,
                new BestWeightAndShortestPathFinder(config.getMaxAllowedDepth()), config);
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                pathComputer.convertFlowPathsToSwitchLists(SWITCH_1, flowPathA, flowPathB));

        assertEquals("The path 'flow_path_a' has no path segments", exception.getMessage());
    }

    private FlowPath buildFlowPath(String pathIdAsString, int... ids) {
        PathId pathId = new PathId(pathIdAsString);
        List<PathSegment> segments = new ArrayList<>();

        for (int i = 1; i < ids.length; i++) {
            segments.add(PathSegment.builder()
                    .pathId(pathId)
                    .srcSwitch(Switch.builder().switchId(new SwitchId(ids[i - 1])).build())
                    .destSwitch(Switch.builder().switchId(new SwitchId(ids[i])).build())
                    .build());
        }

        return FlowPath.builder()
                .pathId(pathId)
                .srcSwitch(Switch.builder().switchId(new SwitchId(ids[0])).build())
                .destSwitch(Switch.builder().switchId(new SwitchId(ids[ids.length - 1])).build())
                .segments(segments)
                .build();
    }

    protected void addLink(
            AvailableNetwork network, SwitchId srcSwitchId, SwitchId dstSwitchId, int srcPort, int dstPort,
            long latency, int affinityCounter) {
        Edge edge = Edge.builder()
                .srcSwitch(network.getOrAddNode(srcSwitchId, null))
                .srcPort(srcPort)
                .destSwitch(network.getOrAddNode(dstSwitchId, null))
                .destPort(dstPort)
                .latency(latency)
                .cost(1)
                .availableBandwidth(500000)
                .underMaintenance(false)
                .unstable(false)
                .affinityGroupUseCounter(affinityCounter)
                .build();
        network.addEdge(edge);
    }

    protected void addBidirectionalLink(
            AvailableNetwork network, SwitchId firstSwitch, SwitchId secondSwitch, int srcPort, int dstPort,
            long latency, int affinityCounter) {
        addLink(network, firstSwitch, secondSwitch, srcPort, dstPort, latency, affinityCounter);
        addLink(network, secondSwitch, firstSwitch, dstPort, srcPort, latency, affinityCounter);
    }
}
