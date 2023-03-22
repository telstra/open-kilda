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
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.Isl;
import org.openkilda.model.IslStatus;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.GetPathsResult;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.pce.finder.BestWeightAndShortestPathFinder;
import org.openkilda.pce.finder.FailReasonType;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class LatencyPathComputationStrategyBaseTest extends InMemoryPathComputerBaseTest {

    @Test
    public void shouldFindPathOverDiamondWithAllActiveLinksByLatency()
            throws UnroutableFlowException, RecoverableException {
        createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 100L, 1000L);

        Switch srcSwitch = getSwitchById("00:01");
        Switch destSwitch = getSwitchById("00:04");

        Flow flow = new TestFlowBuilder()
                .srcSwitch(srcSwitch)
                .destSwitch(destSwitch)
                .bandwidth(100)
                .pathComputationStrategy(PathComputationStrategy.LATENCY)
                .build();

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        GetPathsResult path = pathComputer.getPath(flow);
        assertNotNull(path);
        assertThat(path.getForward().getSegments(), Matchers.hasSize(2));
        // should choose path B because it has lower latency
        assertEquals(new SwitchId("00:02"), path.getForward().getSegments().get(0).getDestSwitchId());
    }

    @Test
    public void shouldFindPathOverDiamondWithOneActiveRouteByLatency()
            throws UnroutableFlowException, RecoverableException {
        createDiamond(IslStatus.INACTIVE, IslStatus.ACTIVE, 100L, 1000L);

        Switch srcSwitch = getSwitchById("00:01");
        Switch destSwitch = getSwitchById("00:04");

        Flow flow = new TestFlowBuilder()
                .srcSwitch(srcSwitch)
                .destSwitch(destSwitch)
                .bandwidth(100)
                .pathComputationStrategy(PathComputationStrategy.LATENCY)
                .build();

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        GetPathsResult path = pathComputer.getPath(flow);
        assertNotNull(path);
        assertThat(path.getForward().getSegments(), Matchers.hasSize(2));
        // should have switch C as first hop since B is inactive
        assertEquals(new SwitchId("00:03"), path.getForward().getSegments().get(0).getDestSwitchId());
    }

    @Test
    public void shouldFindPathOverDiamondWithOneIslUnderMaintenanceByLatency()
            throws UnroutableFlowException, RecoverableException {
        createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 100L, 1000L);

        Switch srcSwitch = getSwitchById("00:01");
        Switch destSwitch = getSwitchById("00:04");
        Isl linkAB = islRepository.findBySrcSwitch(srcSwitch.getSwitchId()).stream()
                .filter(isl -> isl.getDestSwitchId().equals(new SwitchId("00:02")))
                .findAny().orElseThrow(() -> new IllegalStateException("Link A-B not found"));
        linkAB.setUnderMaintenance(true);

        Flow flow = new TestFlowBuilder()
                .srcSwitch(srcSwitch)
                .destSwitch(destSwitch)
                .bandwidth(100)
                .pathComputationStrategy(PathComputationStrategy.LATENCY)
                .build();

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        GetPathsResult path = pathComputer.getPath(flow);
        assertNotNull(path);
        assertThat(path.getForward().getSegments(), Matchers.hasSize(2));
        // should now have C as first hop since A - B link is under maintenance
        assertEquals(new SwitchId("00:03"), path.getForward().getSegments().get(0).getDestSwitchId());
    }

    @Test
    public void shouldFindPathOverDiamondWithUnstableIslByLatency()
            throws UnroutableFlowException, RecoverableException {
        createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 100L, 1000L);

        Switch srcSwitch = getSwitchById("00:01");
        Switch destSwitch = getSwitchById("00:04");
        Isl linkAB = islRepository.findBySrcSwitch(srcSwitch.getSwitchId()).stream()
                .filter(isl -> isl.getDestSwitchId().equals(new SwitchId("00:02")))
                .findAny().orElseThrow(() -> new IllegalStateException("Link A-B not found"));
        linkAB.setTimeUnstable(Instant.now());

        Flow flow = new TestFlowBuilder()
                .srcSwitch(srcSwitch)
                .destSwitch(destSwitch)
                .bandwidth(100)
                .pathComputationStrategy(PathComputationStrategy.LATENCY)
                .build();

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        GetPathsResult path = pathComputer.getPath(flow);
        assertNotNull(path);
        assertThat(path.getForward().getSegments(), Matchers.hasSize(2));
        // should now have C as first hop since A - B link is unstable
        assertEquals(new SwitchId("00:03"), path.getForward().getSegments().get(0).getDestSwitchId());
    }

    @Test
    public void shouldFindPathOverTriangleByLatency() throws UnroutableFlowException, RecoverableException {
        /*
         * should choose longer (in hops) but low latency path
         */
        createTriangleTopo(IslStatus.ACTIVE, 10, 10, "00:", 1);

        Switch srcSwitch = getSwitchById("00:01");
        Switch destSwitch = getSwitchById("00:02");

        Flow flow = new TestFlowBuilder()
                .srcSwitch(srcSwitch)
                .destSwitch(destSwitch)
                .bandwidth(100)
                .pathComputationStrategy(PathComputationStrategy.LATENCY)
                .build();

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        GetPathsResult path = pathComputer.getPath(flow);
        assertNotNull(path);
        assertThat(path.getForward().getSegments(), Matchers.hasSize(2));
        // it should now have C as first hop since A - B segment has high latency
        assertEquals(new SwitchId("00:03"), path.getForward().getSegments().get(0).getDestSwitchId());
    }

    @Test
    public void shouldFindPathOverDiamondWithNoLatencyOnOneRoute()
            throws UnroutableFlowException, RecoverableException {
        /*
         * path B has no latency, path C has latency greater then default value
         */
        createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 0L, 1_000_000_000L);

        Switch srcSwitch = getSwitchById("00:01");
        Switch destSwitch = getSwitchById("00:04");

        Flow flow = new TestFlowBuilder()
                .srcSwitch(srcSwitch)
                .destSwitch(destSwitch)
                .bandwidth(100)
                .pathComputationStrategy(PathComputationStrategy.LATENCY)
                .build();

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        GetPathsResult path = pathComputer.getPath(flow);
        assertNotNull(path);
        assertThat(path.getForward().getSegments(), Matchers.hasSize(2));
        // should choose B because default latency (500_000_000) is less then A-C latency (1_000_000_000)
        assertEquals(new SwitchId("00:02"), path.getForward().getSegments().get(0).getDestSwitchId());
    }

    @Test
    public void shouldFailToFindOverDiamondWithNoActiveRoutes() throws UnroutableFlowException, RecoverableException {
        createDiamond(IslStatus.INACTIVE, IslStatus.INACTIVE, 100L, 1000L);

        Switch srcSwitch = getSwitchById("00:01");
        Switch destSwitch = getSwitchById("00:04");

        Flow flow = new TestFlowBuilder()
                .srcSwitch(srcSwitch)
                .destSwitch(destSwitch)
                .bandwidth(100)
                .pathComputationStrategy(PathComputationStrategy.LATENCY)
                .build();

        thrown.expect(UnroutableFlowException.class);

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        pathComputer.getPath(flow);
    }

    @Test
    public void shouldFailToFindOverIslandsWithAllActiveLinks()
            throws RecoverableException, UnroutableFlowException {
        createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 20, "00:", 1);
        createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 20, "00:", 6);

        Switch srcSwitch = getSwitchById("00:01");
        Switch destSwitch = getSwitchById("00:06");

        Flow flow = new TestFlowBuilder()
                .srcSwitch(srcSwitch)
                .destSwitch(destSwitch)
                .bandwidth(100)
                .pathComputationStrategy(PathComputationStrategy.LATENCY)
                .build();

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        Exception exception = Assertions.assertThrows(UnroutableFlowException.class, () -> {
            pathComputer.getPath(flow);
        });
        MatcherAssert.assertThat(exception.getMessage(), containsString(FailReasonType.NO_CONNECTION.toString()));
    }

    @Test
    public void shouldFindDiversePath() throws RecoverableException, UnroutableFlowException {
        createDiamondWithDiversity();

        Flow flow = Flow.builder()
                .flowId("new-flow")
                .diverseGroupId("diverse")
                .bandwidth(10)
                .srcSwitch(getSwitchById("00:0A"))
                .destSwitch(getSwitchById("00:0D"))
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .pathComputationStrategy(PathComputationStrategy.LATENCY)
                .build();
        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        GetPathsResult diversePath = pathComputer.getPath(flow);

        diversePath.getForward().getSegments().forEach(
                segment -> {
                    assertNotEquals(new SwitchId("00:0B"), segment.getSrcSwitchId());
                    assertNotEquals(new SwitchId("00:0B"), segment.getDestSwitchId());
                });
    }

    @Test
    public void shouldFindTheSameDiversePath() throws RecoverableException, UnroutableFlowException {
        createDiamondWithDiversity();

        Flow flow = Flow.builder()
                .flowId("new-flow")
                .diverseGroupId("diverse")
                .bandwidth(10)
                .srcSwitch(getSwitchById("00:0A"))
                .srcPort(10)
                .destSwitch(getSwitchById("00:0D"))
                .destPort(10)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .pathComputationStrategy(PathComputationStrategy.LATENCY)
                .build();
        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        GetPathsResult diversePath = pathComputer.getPath(flow);

        FlowPath forwardPath = FlowPath.builder()
                .pathId(new PathId(UUID.randomUUID().toString()))
                .srcSwitch(flow.getSrcSwitch())
                .destSwitch(flow.getDestSwitch())
                .bandwidth(flow.getBandwidth())
                .build();
        addPathSegments(forwardPath, diversePath.getForward());
        flow.setForwardPath(forwardPath);

        FlowPath reversePath = FlowPath.builder()
                .pathId(new PathId(UUID.randomUUID().toString()))
                .srcSwitch(flow.getDestSwitch())
                .destSwitch(flow.getSrcSwitch())
                .bandwidth(flow.getBandwidth())
                .build();
        addPathSegments(reversePath, diversePath.getReverse());
        flow.setReversePath(reversePath);

        flowRepository.add(flow);

        GetPathsResult path2 = pathComputer.getPath(flow, flow.getPathIds(), false);
        assertEquals(diversePath, path2);
    }

    @Test
    public void shouldFindAffinityPathOnDiamond() throws Exception {
        shouldFindAffinityPathOnDiamond(PathComputationStrategy.LATENCY);
    }

    @Test
    public void affinityPathShouldSplitAsCloseAsPossibleToDestination() throws Exception {
        affinityPathShouldSplitAsCloseAsPossibleToDestination(PathComputationStrategy.LATENCY);
    }

    @Test
    public void affinityPathShouldPreferIslsUsedByMainPath() throws Exception {
        affinityPathShouldPreferIslsUsedByMainPath(PathComputationStrategy.LATENCY);
    }

    @Test
    public void affinityOvercomeDiversity() throws Exception {
        affinityOvercomeDiversity(PathComputationStrategy.LATENCY);
    }

    @Test
    public void shouldSetBackUpFlagWhenPathHasLatencyGreaterThanMaxLatency()
            throws RecoverableException, UnroutableFlowException {
        createTriangleTopo(IslStatus.ACTIVE, 100, 100, "00:", 1);

        //when: request a flow with LATENCY strategy and 'max-latency' is lower than found path
        Flow flow = Flow.builder()
                .flowId("test flow")
                .srcSwitch(getSwitchById("00:01")).srcPort(15)
                .destSwitch(getSwitchById("00:02")).destPort(15)
                .bandwidth(500)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .pathComputationStrategy(PathComputationStrategy.LATENCY)
                .maxLatency(50L)
                .maxLatencyTier2(300L)
                .build();
        PathComputer pathComputer = new InMemoryPathComputer(availableNetworkFactory,
                new BestWeightAndShortestPathFinder(5), config);
        GetPathsResult pathsResult = pathComputer.getPath(flow);

        //then: system returns a path built with backUpPathComputationWayUsed flag
        assertTrue(pathsResult.isBackUpPathComputationWayUsed());
        assertThat(pathsResult.getForward().getSegments().get(1).getSrcSwitchId(), equalTo(new SwitchId("00:03")));
        assertThat(pathsResult.getReverse().getSegments().get(1).getSrcSwitchId(), equalTo(new SwitchId("00:03")));
    }

    @Test
    public void shouldReturnEmptyPathWhenPathHasLatencyGreaterThanMaxLatencyTier2()
            throws RecoverableException, UnroutableFlowException {
        createLinear(100,  "00:", 1, TimeUnit.MILLISECONDS.toNanos(100));

        //when: request a flow with LATENCY strategy and 'max-latency-tier-2' is lower than found path
        Flow flow = Flow.builder()
                .flowId("test flow")
                .srcSwitch(getSwitchById("00:01")).srcPort(15)
                .destSwitch(getSwitchById("00:03")).destPort(15)
                .bandwidth(500)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .pathComputationStrategy(PathComputationStrategy.LATENCY)
                .maxLatency(TimeUnit.MILLISECONDS.toNanos(50))
                .maxLatencyTier2(TimeUnit.MILLISECONDS.toNanos(70))
                .build();
        PathComputer pathComputer = new InMemoryPathComputer(availableNetworkFactory,
                new BestWeightAndShortestPathFinder(5), config);

        //then: system can't find path
        Exception exception = assertThrows(UnroutableFlowException.class, () -> {
            pathComputer.getPath(flow);
        });
        MatcherAssert.assertThat(exception.getMessage(), containsString("Reasons: Latency limit: "
                + "Requested path must have latency 70ms or lower, but best path has latency 200ms"));
    }

    private void createLinear(int cost, String switchStart, int startIndex, long latency) {
        // A - B - C
        int index = startIndex;

        Switch nodeA = createSwitch(switchStart + format("%02X", index++));
        Switch nodeB = createSwitch(switchStart + format("%02X", index++));
        Switch nodeC = createSwitch(switchStart + format("%02X", index));

        createIsl(nodeA, nodeB, IslStatus.ACTIVE, IslStatus.ACTIVE, cost, 1000, 5, latency);
        createIsl(nodeB, nodeC, IslStatus.ACTIVE, IslStatus.ACTIVE, cost, 1000, 6, latency);

    }

    private void createDiamond(IslStatus pathBstatus, IslStatus pathCstatus, long pathBlatency, long pathClatency) {
        createDiamond(pathBstatus, pathCstatus, 10, 10, "00", 1, pathBlatency, pathClatency);
    }
}
