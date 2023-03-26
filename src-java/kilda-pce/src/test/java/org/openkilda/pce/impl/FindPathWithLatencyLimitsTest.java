/* Copyright 2022 Telstra Open Source
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.openkilda.config.provider.PropertiesBasedConfigurationProvider;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.PathComputerConfig;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.pce.finder.BestWeightAndShortestPathFinder;
import org.openkilda.pce.finder.FailReasonType;
import org.openkilda.pce.model.Edge;
import org.openkilda.pce.model.FindPathResult;
import org.openkilda.pce.model.WeightFunction;

import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class FindPathWithLatencyLimitsTest {
    private static final int ALLOWED_DEPTH = 35;
    private static final SwitchId SWITCH_ID_1 = new SwitchId("00:00:00:00:00:00:00:01");
    private static final SwitchId SWITCH_ID_2 = new SwitchId("00:00:00:00:00:00:00:02");
    private static final SwitchId SWITCH_ID_3 = new SwitchId("00:00:00:00:00:00:00:03");
    private static final SwitchId SWITCH_ID_4 = new SwitchId("00:00:00:00:00:00:00:04");
    private static final SwitchId SWITCH_ID_5 = new SwitchId("00:00:00:00:00:00:00:05");
    private static final SwitchId SWITCH_ID_6 = new SwitchId("00:00:00:00:00:00:00:06");
    private final AvailableNetwork network = new AvailableNetwork();
    private final BestWeightAndShortestPathFinder pathFinder = new BestWeightAndShortestPathFinder(ALLOWED_DEPTH);

    private InMemoryPathComputer pathComputer;

    @Before
    public void setUp() {
        PathComputerConfig pathComputerConfig = new PropertiesBasedConfigurationProvider()
                .getConfiguration(PathComputerConfig.class);
        pathComputer = new InMemoryPathComputer(null, null, pathComputerConfig);
    }

    @Test
    public void shouldFindPathIfAllIslsOnUnderMaintainance() throws UnroutableFlowException {
        /*
         *   Topology:
         *
         *   SW1--m--SW2--m---SW3
         *
         *   All ISLs are in under-maintenance mode
         */
        addBidirectionalLink(network, SWITCH_ID_1, SWITCH_ID_2, 1, 2, true, 5);
        addBidirectionalLink(network, SWITCH_ID_2, SWITCH_ID_3, 5, 6, true, 5);

        WeightFunction weightFunction = pathComputer
                .getWeightFunctionByStrategy(PathComputationStrategy.MAX_LATENCY, false);

        FindPathResult path = pathFinder.findPathWithWeightCloseToMaxWeight(network, SWITCH_ID_1, SWITCH_ID_3,
                weightFunction, 100, 100);

        assertThat(path, is(notNullValue()));

        Edge firstEdge = path.getFoundPath().getLeft().get(0);
        assertAll(
                () -> assertThat(firstEdge.getSrcSwitch().getSwitchId(), Matchers.equalTo(SWITCH_ID_1)),
                () -> assertThat(firstEdge.getDestSwitch().getSwitchId(), Matchers.equalTo(SWITCH_ID_2))
        );

        Edge lastEdge = path.getFoundPath().getLeft().get(1);
        assertAll(
                () -> assertThat(lastEdge.getSrcSwitch().getSwitchId(), Matchers.equalTo(SWITCH_ID_2)),
                () -> assertThat(lastEdge.getDestSwitch().getSwitchId(), Matchers.equalTo(SWITCH_ID_3))
        );

        Edge firstReverseEdge = path.getFoundPath().getRight().get(0);
        assertAll(
                () -> assertThat(firstReverseEdge.getSrcSwitch().getSwitchId(), Matchers.equalTo(SWITCH_ID_3)),
                () -> assertThat(firstReverseEdge.getDestSwitch().getSwitchId(), Matchers.equalTo(SWITCH_ID_2))
        );

        Edge lastReverseEdge = path.getFoundPath().getRight().get(1);
        assertAll(
                () -> assertThat(lastReverseEdge.getSrcSwitch().getSwitchId(), Matchers.equalTo(SWITCH_ID_2)),
                () -> assertThat(lastReverseEdge.getDestSwitch().getSwitchId(), Matchers.equalTo(SWITCH_ID_1))
        );
    }

    @Test
    public void shouldNotFindPathBecauseMaxWeight() {
        /*   they have connection, but latency of this connection is too slow
         *   Topology:
         *
         *   SW1--m--SW2--m---SW3
         *
         *   All ISLs are in under-maintenance mode
         */
        addBidirectionalLink(network, SWITCH_ID_1, SWITCH_ID_2, 1, 2, true, 55);
        addBidirectionalLink(network, SWITCH_ID_2, SWITCH_ID_3, 5, 6, true, 55);

        WeightFunction weightFunction = pathComputer
                .getWeightFunctionByStrategy(PathComputationStrategy.MAX_LATENCY, false);

        Exception exception = assertThrows(UnroutableFlowException.class, () -> {
            pathFinder.findPathWithWeightCloseToMaxWeight(network, SWITCH_ID_1, SWITCH_ID_3,
                    weightFunction, 100, 100);
        });

        assertThat(exception.getMessage(), containsString(FailReasonType.MAX_WEIGHT_EXCEEDED.toString()));

    }

    @Test
    public void shouldNotFindPathBecauseNoConnection() {
        /*   switches SW1 and SW4 have no connection at all
         *
         *   Topology:
         *
         *   SW1--m--SW2   SW3----SW4
         *
         *   No link between SW2 and SW3
         */
        addBidirectionalLink(network, SWITCH_ID_1, SWITCH_ID_2, 1, 1, false, 55);
        addBidirectionalLink(network, SWITCH_ID_3, SWITCH_ID_4, 1, 1, false, 55);

        WeightFunction weightFunction = pathComputer
                .getWeightFunctionByStrategy(PathComputationStrategy.MAX_LATENCY, false);

        Exception exception = assertThrows(UnroutableFlowException.class, () -> {
            pathFinder.findPathWithWeightCloseToMaxWeight(network, SWITCH_ID_1, SWITCH_ID_4,
                    weightFunction, 100, 100);
        });

        assertThat(exception.getMessage(), containsString(FailReasonType.NO_CONNECTION.toString()));
    }

    @Test
    public void shouldChoosePathCloseToMaxWeight() throws UnroutableFlowException {
        /*
         *   Topology:
         *
         *   SW1--m--SW2--m---SW3
         *            \        |
         *             ok     ok
         *              \_SW4_|
         *
         */
        addBidirectionalLink(network, SWITCH_ID_1, SWITCH_ID_2, 1, 2, true, 5);
        addBidirectionalLink(network, SWITCH_ID_2, SWITCH_ID_3, 5, 6, true, 5);
        addBidirectionalLink(network, SWITCH_ID_2, SWITCH_ID_4, 7, 8, false, 25);
        addBidirectionalLink(network, SWITCH_ID_4, SWITCH_ID_3, 9, 10, false, 25);

        WeightFunction weightFunction = pathComputer
                .getWeightFunctionByStrategy(PathComputationStrategy.MAX_LATENCY, false);

        FindPathResult path = pathFinder.findPathWithWeightCloseToMaxWeight(network, SWITCH_ID_1, SWITCH_ID_3,
                weightFunction, 100, 100);

        assertThat(path, is(notNullValue()));
        List<Edge> forwardPath = path.getFoundPath().getLeft();
        List<Edge> reversePath = path.getFoundPath().getRight();

        assertAll(
                () -> assertThat(forwardPath.size(), is(3)),
                () -> assertThat(forwardPath.get(2).getSrcSwitch().getSwitchId(), is(SWITCH_ID_4)),
                () -> assertThat(reversePath.get(0).getDestSwitch().getSwitchId(), is(SWITCH_ID_4))
        );
    }

    @Test
    public void shouldChoosePathCloseToMaxWeight2() throws UnroutableFlowException {
        /*
         *   Topology:
         *
         *        SW1--m--SW2--m---SW3
         *           \            |
         *            m           m
         *             \_  SW4  _|
         *
         */
        addBidirectionalLink(network, SWITCH_ID_1, SWITCH_ID_2, 1, 2, true, 5);
        addBidirectionalLink(network, SWITCH_ID_2, SWITCH_ID_3, 5, 6, true, 5);
        addBidirectionalLink(network, SWITCH_ID_1, SWITCH_ID_4, 7, 8, true, 25);
        addBidirectionalLink(network, SWITCH_ID_4, SWITCH_ID_3, 9, 10, true, 25);

        WeightFunction weightFunction = pathComputer
                .getWeightFunctionByStrategy(PathComputationStrategy.MAX_LATENCY, false);

        FindPathResult path = pathFinder.findPathWithWeightCloseToMaxWeight(network, SWITCH_ID_1, SWITCH_ID_3,
                weightFunction, 100, 100);

        assertThat(path, is(notNullValue()));
        List<Edge> forwardPath = path.getFoundPath().getLeft();
        List<Edge> reversePath = path.getFoundPath().getRight();

        assertAll(
                () -> assertThat(forwardPath.size(), is(2)),
                () -> assertThat(reversePath.size(), is(2)),
                () -> assertThat(forwardPath.get(0).getDestSwitch().getSwitchId(), is(SWITCH_ID_4)),
                () -> assertThat(reversePath.get(0).getDestSwitch().getSwitchId(), is(SWITCH_ID_4))
        );
    }

    @Test
    public void shouldChoosePathCloseToMaxWeight3() throws UnroutableFlowException {
        /*
         *   Topology:
         *
         *        SW1--m--SW2---m--SW3
         *             m  SW4
         *             m  SW5   m
         *             m  SW6   m
         *
         */
        addBidirectionalLink(network, SWITCH_ID_1, SWITCH_ID_2, 1, 2, true, 5);
        addBidirectionalLink(network, SWITCH_ID_1, SWITCH_ID_4, 3, 4, true, 5);
        addBidirectionalLink(network, SWITCH_ID_1, SWITCH_ID_5, 5, 6, true, 5);
        addBidirectionalLink(network, SWITCH_ID_1, SWITCH_ID_6, 7, 8, true, 5);
        addBidirectionalLink(network, SWITCH_ID_2, SWITCH_ID_3, 9, 10, true, 5);
        addBidirectionalLink(network, SWITCH_ID_4, SWITCH_ID_3, 11, 12, false, 1);
        addBidirectionalLink(network, SWITCH_ID_5, SWITCH_ID_3, 13, 14, true, 5);
        addBidirectionalLink(network, SWITCH_ID_6, SWITCH_ID_3, 15, 16, true, 5);


        WeightFunction weightFunction = pathComputer
                .getWeightFunctionByStrategy(PathComputationStrategy.MAX_LATENCY, false);

        FindPathResult path = pathFinder.findPathWithWeightCloseToMaxWeight(network, SWITCH_ID_1, SWITCH_ID_3,
                weightFunction, 100, 100);

        assertThat(path, is(notNullValue()));
        List<Edge> forwardPath = path.getFoundPath().getLeft();
        List<Edge> reversePath = path.getFoundPath().getRight();

        assertAll(
                () -> assertThat(forwardPath.size(), is(2)),
                () -> assertThat(reversePath.size(), is(2)),
                () -> assertThat(forwardPath.get(0).getDestSwitch().getSwitchId(), is(SWITCH_ID_4)),
                () -> assertThat(reversePath.get(0).getDestSwitch().getSwitchId(), is(SWITCH_ID_4))
        );
    }

    @Test
    public void shouldChoosePathCloseToMaxWeight4() throws UnroutableFlowException {
        /*
         *   Topology:
         *
         *        SW1--m--SW2--m--SW3
         *           \             |
         *            m           |
         *             \_  SW4  _|
         *
         */
        addBidirectionalLink(network, SWITCH_ID_1, SWITCH_ID_2, 1, 2, true, 5);
        addBidirectionalLink(network, SWITCH_ID_2, SWITCH_ID_3, 5, 6, true, 5);
        addBidirectionalLink(network, SWITCH_ID_1, SWITCH_ID_4, 7, 8, true, 25);
        addBidirectionalLink(network, SWITCH_ID_4, SWITCH_ID_3, 9, 10, false, 25);

        WeightFunction weightFunction = pathComputer
                .getWeightFunctionByStrategy(PathComputationStrategy.MAX_LATENCY, false);

        FindPathResult path = pathFinder.findPathWithWeightCloseToMaxWeight(network, SWITCH_ID_1, SWITCH_ID_3,
                weightFunction, 100, 100);

        assertThat(path, is(notNullValue()));
        List<Edge> forwardPath = path.getFoundPath().getLeft();
        List<Edge> reversePath = path.getFoundPath().getRight();

        assertAll(
                () -> assertThat(forwardPath.size(), is(2)),
                () -> assertThat(reversePath.size(), is(2)),
                () -> assertThat(forwardPath.get(0).getDestSwitch().getSwitchId(), is(SWITCH_ID_4)),
                () -> assertThat(reversePath.get(0).getDestSwitch().getSwitchId(), is(SWITCH_ID_4))
        );
    }

    @Test
    public void shouldFindPathAroundMaintainancedIsls() throws UnroutableFlowException {
        /*
         *   Topology:
         *
         *   SW1--m--SW2--m---SW3
         *      \             |
         *       ok          ok
         *        \___SW4____|
         *
         */
        addBidirectionalLink(network, SWITCH_ID_1, SWITCH_ID_2, 1, 2, true, 5);
        addBidirectionalLink(network, SWITCH_ID_2, SWITCH_ID_3, 5, 6, true, 5);
        addBidirectionalLink(network, SWITCH_ID_1, SWITCH_ID_4, 7, 8, false, 25);
        addBidirectionalLink(network, SWITCH_ID_4, SWITCH_ID_3, 9, 10, false, 25);

        WeightFunction weightFunction = pathComputer
                .getWeightFunctionByStrategy(PathComputationStrategy.MAX_LATENCY, false);

        FindPathResult path = pathFinder.findPathWithWeightCloseToMaxWeight(network, SWITCH_ID_1, SWITCH_ID_3,
                weightFunction, 100, 100);

        assertThat(path, is(notNullValue()));
        List<Edge> forwardPath = path.getFoundPath().getLeft();
        assertThat(forwardPath.get(forwardPath.size() - 1).getSrcSwitch().getSwitchId(), is(SWITCH_ID_4));
        List<Edge> reversePath = path.getFoundPath().getRight();
        assertThat(reversePath.get(0).getDestSwitch().getSwitchId(), is(SWITCH_ID_4));
    }

    private void addBidirectionalLink(AvailableNetwork network, SwitchId firstSwitch, SwitchId secondSwitch,
                                      int srcPort, int dstPort, boolean isUnderMaintainatce, int weight) {
        addLink(network, firstSwitch, secondSwitch, srcPort, dstPort, isUnderMaintainatce, weight);
        addLink(network, secondSwitch, firstSwitch, dstPort, srcPort, isUnderMaintainatce, weight);
    }

    private void addLink(AvailableNetwork network, SwitchId srcDpid, SwitchId dstDpid, int srcPort, int dstPort,
                         boolean isUnderMaintainatce, int weight) {
        Edge edge = Edge.builder()
                .srcSwitch(network.getOrAddNode(srcDpid, null))
                .srcPort(srcPort)
                .destSwitch(network.getOrAddNode(dstDpid, null))
                .destPort(dstPort)
                .latency(weight)
                .cost(weight)
                .availableBandwidth(500000)
                .underMaintenance(isUnderMaintainatce)
                .unstable(false)
                .build();
        network.addEdge(edge);
    }
}
