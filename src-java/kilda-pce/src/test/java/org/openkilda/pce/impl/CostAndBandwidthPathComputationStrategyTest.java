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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.IslStatus;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.GetPathsResult;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;

import org.hamcrest.Matchers;
import org.junit.Test;

import java.util.ArrayList;
import java.util.UUID;

public class CostAndBandwidthPathComputationStrategyTest extends InMemoryPathComputerBaseTest {

    @Test
    public void shouldFindPathOverDiamondWithAllActiveLinksByCostAndBandwidth()
            throws UnroutableFlowException, RecoverableException {
        /*
         * simple happy path test .. everything has cost
         */
        createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 20, "00:", 1);

        Switch srcSwitch = getSwitchById("00:01");
        Switch destSwitch = getSwitchById("00:04");

        Flow f = getTestFlowBuilder(srcSwitch, destSwitch).build();

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        GetPathsResult path = pathComputer.getPath(f);
        assertNotNull(path);
        assertThat(path.getForward().getSegments(), Matchers.hasSize(2));
        assertEquals(new SwitchId("00:02"), path.getForward().getSegments().get(0).getDestSwitchId()); // chooses path B
    }

    @Test
    public void shouldFindPathOverDiamondWithOneActiveRouteByCostAndBandwidth()
            throws UnroutableFlowException, RecoverableException {
        createDiamond(IslStatus.INACTIVE, IslStatus.ACTIVE, 10, 20, "01:", 1);

        Switch srcSwitch = getSwitchById("01:01");
        Switch destSwitch = getSwitchById("01:04");

        Flow f = getTestFlowBuilder(srcSwitch, destSwitch).build();

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        GetPathsResult path = pathComputer.getPath(f);
        assertNotNull(path);
        assertThat(path.getForward().getSegments(), Matchers.hasSize(2));
        // ====> only difference is it should now have C as first hop .. since B is inactive
        assertEquals(new SwitchId("01:03"), path.getForward().getSegments().get(0).getDestSwitchId()); // chooses path C
    }

    @Test
    public void shouldFindPathOverTriangleWithOneActiveRouteByCostAndBandwidth()
            throws UnroutableFlowException, RecoverableException {
        /*
         * simple happy path test .. but lowest path is inactive
         */
        createTriangleTopo(IslStatus.INACTIVE, 5, 20, "02:", 1);

        Switch srcSwitch = getSwitchById("02:01");
        Switch destSwitch = getSwitchById("02:02");

        Flow f = getTestFlowBuilder(srcSwitch, destSwitch).build();

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        GetPathsResult path = pathComputer.getPath(f);
        assertNotNull(path);
        assertThat(path.getForward().getSegments(), Matchers.hasSize(2));
        // ====> only difference is it should now have C as first hop .. since B is inactive
        assertEquals(new SwitchId("02:03"), path.getForward().getSegments().get(0).getDestSwitchId()); // chooses path C
    }

    @Test
    public void shouldFindPathOverDiamondWithNoCostOnOneRoute() throws UnroutableFlowException, RecoverableException {
        /*
         * simple happy path test .. but pathB has no cost .. but still cheaper than pathC (test the default)
         */
        createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, -1, 2000, "03:", 1);

        Switch srcSwitch = getSwitchById("03:01");
        Switch destSwitch = getSwitchById("03:04");

        Flow f = getTestFlowBuilder(srcSwitch, destSwitch).build();

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        GetPathsResult path = pathComputer.getPath(f);
        assertNotNull(path);
        assertThat(path.getForward().getSegments(), Matchers.hasSize(2));
        // ====> Should choose B .. because default cost (700) cheaper than 2000
        assertEquals(new SwitchId("03:02"), path.getForward().getSegments().get(0).getDestSwitchId()); // chooses path B
    }

    @Test
    public void shouldFailToFindOverDiamondWithNoActiveRoutes() throws UnroutableFlowException, RecoverableException {
        createDiamond(IslStatus.INACTIVE, IslStatus.INACTIVE, 10, 30, "04:", 1);

        Switch srcSwitch = getSwitchById("04:01");
        Switch destSwitch = getSwitchById("04:04");

        Flow f = getTestFlowBuilder(srcSwitch, destSwitch).build();

        thrown.expect(UnroutableFlowException.class);

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        pathComputer.getPath(f);
    }


    @Test
    public void shouldFindPathOverDiamondWithAllActiveLinksAndIgnoreBandwidth()
            throws RecoverableException, UnroutableFlowException {
        createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 20, "05:", 1);

        Switch srcSwitch1 = getSwitchById("05:01");
        Switch destSwitch1 = getSwitchById("05:03");

        Flow f1 = getTestFlowBuilder(srcSwitch1, destSwitch1)
                .bandwidth(0)
                .ignoreBandwidth(false)
                .build();

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        GetPathsResult path = pathComputer.getPath(f1);
        assertNotNull(path);
        assertThat(path.getForward().getSegments(), Matchers.hasSize(1));

        Switch srcSwitch2 = getSwitchById("05:01");
        Switch destSwitch2 = getSwitchById("05:04");

        Flow f2 = getTestFlowBuilder(srcSwitch2, destSwitch2)
                .bandwidth(0)
                .ignoreBandwidth(false)
                .build();

        path = pathComputer.getPath(f2);
        assertNotNull(path);
        assertThat(path.getForward().getSegments(), Matchers.hasSize(2));
        assertEquals(new SwitchId("05:02"), path.getForward().getSegments().get(0).getDestSwitchId());
    }

    /**
     * Create a couple of islands .. try to find a path between them .. validate no path is returned, and that the
     * function completes in reasonable time ( < 10ms);
     */
    @Test
    public void shouldFailToFindOverIslandsWithAllActiveLinksAndIgnoreBandwidth()
            throws RecoverableException, UnroutableFlowException {
        createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 20, "06:", 1);
        createDiamond(IslStatus.ACTIVE, IslStatus.ACTIVE, 10, 20, "07:", 1);

        Switch srcSwitch1 = getSwitchById("06:01");
        Switch destSwitch1 = getSwitchById("06:03");

        // THIS ONE SHOULD WORK
        Flow f1 = getTestFlowBuilder(srcSwitch1, destSwitch1)
                .bandwidth(0)
                .ignoreBandwidth(false)
                .build();

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        GetPathsResult path = pathComputer.getPath(f1);
        assertNotNull(path);
        assertThat(path.getForward().getSegments(), Matchers.hasSize(1));

        Switch srcSwitch2 = getSwitchById("06:01");
        Switch destSwitch2 = getSwitchById("07:04");

        // THIS ONE SHOULD FAIL
        Flow f2 = getTestFlowBuilder(srcSwitch2, destSwitch2)
                .bandwidth(0)
                .ignoreBandwidth(false)
                .build();

        thrown.expect(UnroutableFlowException.class);

        pathComputer.getPath(f2);
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
                .pathComputationStrategy(PathComputationStrategy.COST)
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
                .pathComputationStrategy(PathComputationStrategy.COST)
                .build();
        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        GetPathsResult diversePath = pathComputer.getPath(flow);

        FlowPath forwardPath = FlowPath.builder()
                .pathId(new PathId(UUID.randomUUID().toString()))
                .srcSwitch(flow.getSrcSwitch())
                .destSwitch(flow.getDestSwitch())
                .bandwidth(flow.getBandwidth())
                .segments(new ArrayList<>())
                .build();
        addPathSegments(forwardPath, diversePath.getForward());
        flow.setForwardPath(forwardPath);

        FlowPath reversePath = FlowPath.builder()
                .pathId(new PathId(UUID.randomUUID().toString()))
                .srcSwitch(flow.getDestSwitch())
                .destSwitch(flow.getSrcSwitch())
                .bandwidth(flow.getBandwidth())
                .segments(new ArrayList<>())
                .build();
        addPathSegments(reversePath, diversePath.getReverse());
        flow.setReversePath(reversePath);

        flowRepository.add(flow);

        GetPathsResult path2 = pathComputer.getPath(flow, flow.getPathIds(), false);
        assertEquals(diversePath, path2);
    }

    @Test
    public void shouldFindAffinityPathOnDiamond() throws Exception {
        shouldFindAffinityPathOnDiamond(PathComputationStrategy.COST_AND_AVAILABLE_BANDWIDTH);
    }

    @Test
    public void affinityPathShouldSplitAsCloseAsPossibleToDestination() throws Exception {
        affinityPathShouldSplitAsCloseAsPossibleToDestination(PathComputationStrategy.COST_AND_AVAILABLE_BANDWIDTH);
    }

    @Test
    public void affinityPathShouldPreferIslsUsedByMainPath() throws Exception {
        affinityPathShouldPreferIslsUsedByMainPath(PathComputationStrategy.COST_AND_AVAILABLE_BANDWIDTH);
    }

    @Test
    public void affinityOvercomeDiversity() throws Exception {
        affinityOvercomeDiversity(PathComputationStrategy.COST_AND_AVAILABLE_BANDWIDTH);
    }

    @Test
    public void shouldFindPathWithSameCostAndMinAvailableBandwidth()
            throws RecoverableException, UnroutableFlowException {
        createDiamondWithDifferentAvailableBandwidth();

        Switch srcSwitch = getSwitchById("00:01");
        Switch destSwitch = getSwitchById("00:04");

        Flow f = getTestFlowBuilder(srcSwitch, destSwitch).build();

        PathComputer pathComputer = pathComputerFactory.getPathComputer();
        GetPathsResult path = pathComputer.getPath(f);
        assertNotNull(path);
        assertThat(path.getForward().getSegments(), Matchers.hasSize(2));
        assertEquals(new SwitchId("00:03"), path.getForward().getSegments().get(0).getDestSwitchId());
    }

    private void createDiamondWithDifferentAvailableBandwidth() {
        // A - B - D
        //   + C +
        String switchStart = "00:";
        int index = 1;

        Switch nodeA = createSwitch(switchStart + format("%02X", index++));
        Switch nodeB = createSwitch(switchStart + format("%02X", index++));
        Switch nodeC = createSwitch(switchStart + format("%02X", index++));
        Switch nodeD = createSwitch(switchStart + format("%02X", index));

        createIsl(nodeA, nodeB, IslStatus.ACTIVE, IslStatus.ACTIVE, 5, 1000, 5, 100);
        createIsl(nodeA, nodeC, IslStatus.ACTIVE, IslStatus.ACTIVE, 5, 1000, 6, 100);
        createIsl(nodeB, nodeD, IslStatus.ACTIVE, IslStatus.ACTIVE, 5, 1000, 6, 100);
        createIsl(nodeC, nodeD, IslStatus.ACTIVE, IslStatus.ACTIVE, 5, 500, 5, 100);
        createIsl(nodeB, nodeA, IslStatus.ACTIVE, IslStatus.ACTIVE, 5, 1000, 5, 100);
        createIsl(nodeC, nodeA, IslStatus.ACTIVE, IslStatus.ACTIVE, 5, 1000, 6, 100);
        createIsl(nodeD, nodeB, IslStatus.ACTIVE, IslStatus.ACTIVE, 5, 1000, 6, 100);
        createIsl(nodeD, nodeC, IslStatus.ACTIVE, IslStatus.ACTIVE, 5, 500, 5, 100);
    }

    private static TestFlowBuilder getTestFlowBuilder(Switch srcSwitch, Switch dstSwitch) {
        return new TestFlowBuilder()
                .srcSwitch(srcSwitch)
                .destSwitch(dstSwitch)
                .bandwidth(100)
                .pathComputationStrategy(PathComputationStrategy.COST_AND_AVAILABLE_BANDWIDTH);
    }
}
