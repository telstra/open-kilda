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

package org.openkilda.pce.finder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.openkilda.pce.model.PathWeight.Penalty.AFFINITY_ISL_LATENCY;
import static org.openkilda.pce.model.PathWeight.Penalty.DIVERSITY_ISL_LATENCY;
import static org.openkilda.pce.model.PathWeight.Penalty.DIVERSITY_POP_ISL_COST;
import static org.openkilda.pce.model.PathWeight.Penalty.DIVERSITY_SWITCH_LATENCY;
import static org.openkilda.pce.model.PathWeight.Penalty.UNDER_MAINTENANCE;
import static org.openkilda.pce.model.PathWeight.Penalty.UNSTABLE;

import org.openkilda.model.SwitchId;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.pce.impl.AvailableNetwork;
import org.openkilda.pce.model.Edge;
import org.openkilda.pce.model.FindPathResult;
import org.openkilda.pce.model.PathWeight;
import org.openkilda.pce.model.PathWeight.Penalty;
import org.openkilda.pce.model.WeightFunction;

import org.junit.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class FindPathWithLatencyLimitsTest {
    private static final int ALLOWED_DEPTH = 35;
    private static final long PENALTY = 10_000L;
    private static final SwitchId SWITCH_ID_1 = new SwitchId("00:00:00:00:00:00:00:01");
    private static final SwitchId SWITCH_ID_2 = new SwitchId("00:00:00:00:00:00:00:02");
    private static final SwitchId SWITCH_ID_3 = new SwitchId("00:00:00:00:00:00:00:03");
    private static final SwitchId SWITCH_ID_4 = new SwitchId("00:00:00:00:00:00:00:04");
    private final WeightFunction weightFunction = this::weightByLatency;
    private final AvailableNetwork network = new AvailableNetwork();
    private final BestWeightAndShortestPathFinder pathFinder = new BestWeightAndShortestPathFinder(ALLOWED_DEPTH);

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

        FindPathResult path = pathFinder.findPathWithWeightCloseToMaxWeight(network, SWITCH_ID_1, SWITCH_ID_3,
                weightFunction, 100, 100);

        assertThat(path, is(notNullValue()));
    }

    @Test(expected = UnroutableFlowException.class)
    public void shouldNotFindPath() throws UnroutableFlowException {
        /*
         *   Topology:
         *
         *   SW1--m--SW2--m---SW3
         *
         *   All ISLs are in under-maintenance mode
         */
        addBidirectionalLink(network, SWITCH_ID_1, SWITCH_ID_2, 1, 2, true, 55);
        addBidirectionalLink(network, SWITCH_ID_2, SWITCH_ID_3, 5, 6, true, 55);

        FindPathResult path = pathFinder.findPathWithWeightCloseToMaxWeight(network, SWITCH_ID_1, SWITCH_ID_3,
                weightFunction, 100, 100);
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

        FindPathResult path = pathFinder.findPathWithWeightCloseToMaxWeight(network, SWITCH_ID_1, SWITCH_ID_3,
                weightFunction, 100, 100);

        assertThat(path, is(notNullValue()));
        List<Edge> forwardPath = path.getFoundPath().getLeft();

        assertAll(
                () -> assertThat(forwardPath.size(), is(3)),
                () -> assertThat(forwardPath.get(forwardPath.size() - 1).getSrcSwitch().getSwitchId(), is(SWITCH_ID_4))
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

        FindPathResult path = pathFinder.findPathWithWeightCloseToMaxWeight(network, SWITCH_ID_1, SWITCH_ID_3,
                weightFunction, 100, 100);

        assertThat(path, is(notNullValue()));
        List<Edge> forwardPath = path.getFoundPath().getLeft();
        assertThat(forwardPath.get(forwardPath.size() - 1).getSrcSwitch().getSwitchId(), is(SWITCH_ID_4));
    }

    private PathWeight weightByLatency(Edge edge) {
        long edgeLatency = edge.getLatency() <= 0 ? 10_000L : edge.getLatency();
        Map<Penalty, Long> penalties = new EnumMap<>(Penalty.class);

        if (edge.isUnderMaintenance()) {
            penalties.put(UNDER_MAINTENANCE, PENALTY);
        }

        if (edge.isUnstable()) {
            penalties.put(UNSTABLE, PENALTY);
        }

        if (edge.getDiversityGroupUseCounter() > 0) {
            long value = edge.getDiversityGroupUseCounter() * PENALTY;
            penalties.put(DIVERSITY_ISL_LATENCY, value);
        }

        if (edge.getDiversityGroupPerPopUseCounter() > 0) {
            int value = edge.getDiversityGroupPerPopUseCounter() * (int) PENALTY;
            penalties.put(DIVERSITY_POP_ISL_COST, (long) value);
        }

        if (edge.getDestSwitch().getDiversityGroupUseCounter() > 0) {
            long value = edge.getDestSwitch().getDiversityGroupUseCounter() * PENALTY;
            penalties.put(DIVERSITY_SWITCH_LATENCY, value);
        }

        if (edge.getAffinityGroupUseCounter() > 0) {
            long value = edge.getAffinityGroupUseCounter() * PENALTY;
            penalties.put(AFFINITY_ISL_LATENCY, value);
        }

        return new PathWeight(edgeLatency, penalties);
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
