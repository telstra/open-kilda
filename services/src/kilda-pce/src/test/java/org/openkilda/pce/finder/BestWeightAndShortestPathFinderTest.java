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

package org.openkilda.pce.finder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

import org.openkilda.model.Isl;
import org.openkilda.model.IslConfig;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.pce.impl.AvailableNetwork;
import org.openkilda.pce.model.Edge;
import org.openkilda.pce.model.WeightFunction;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class BestWeightAndShortestPathFinderTest {

    private static final int ALLOWED_DEPTH = 35;
    private static final WeightFunction WEIGHT_FUNCTION = edge -> {
        long total = edge.getCost();
        if (edge.isUnderMaintenance()) {
            total += 10_000;
        }
        if (edge.isUnstable()) {
            total += 10_000;
        }
        total += edge.getDiversityGroupUseCounter() * 1000 + edge.getDestSwitch().getDiversityGroupUseCounter() * 100;
        return total;
    };

    private static final SwitchId SWITCH_ID_A = new SwitchId("00:00:00:22:3d:5a:04:87");
    private static final SwitchId SWITCH_ID_B = new SwitchId("00:00:70:72:cf:d2:48:6c");
    private static final SwitchId SWITCH_ID_C = new SwitchId("00:00:00:22:3d:6c:00:b8");
    private static final SwitchId SWITCH_ID_D = new SwitchId("00:00:00:22:3d:6b:00:04");
    private static final SwitchId SWITCH_ID_E = new SwitchId("00:00:70:72:cf:d2:47:a6");
    private static final SwitchId SWITCH_ID_F = new SwitchId("00:00:b0:d2:f5:00:5a:b8");

    private static final SwitchId SWITCH_ID_1 = new SwitchId("00:00:00:00:00:00:00:01");
    private static final SwitchId SWITCH_ID_2 = new SwitchId("00:00:00:00:00:00:00:02");
    private static final SwitchId SWITCH_ID_3 = new SwitchId("00:00:00:00:00:00:00:03");
    private static final SwitchId SWITCH_ID_4 = new SwitchId("00:00:00:00:00:00:00:04");
    private static final SwitchId SWITCH_ID_5 = new SwitchId("00:00:00:00:00:00:00:05");

    @Test
    public void shouldChooseExpensiveOverTooDeep() throws  UnroutableFlowException {
        AvailableNetwork network = buildLongAndExpensivePathsNetwork();

        BestWeightAndShortestPathFinder pathFinder = new BestWeightAndShortestPathFinder(2);
        Pair<List<Edge>, List<Edge>> pairPath =
                pathFinder.findPathInNetwork(network, SWITCH_ID_1, SWITCH_ID_4, WEIGHT_FUNCTION);
        List<Edge> fpath = pairPath.getLeft();
        assertThat(fpath, Matchers.hasSize(2));
        assertEquals(SWITCH_ID_2, fpath.get(1).getSrcSwitch().getSwitchId());

        List<Edge> rpath = pairPath.getRight();
        assertThat(rpath, Matchers.hasSize(2));
        assertEquals(SWITCH_ID_2, rpath.get(0).getDestSwitch().getSwitchId());
    }

    @Test
    public void shouldChooseExpensiveOverTooDeepForReverseOrder()
            throws  UnroutableFlowException {
        AvailableNetwork network = buildLongAndExpensivePathsNetwork();

        BestWeightAndShortestPathFinder pathFinder = new BestWeightAndShortestPathFinder(2);
        Pair<List<Edge>, List<Edge>> pairPath =
                pathFinder.findPathInNetwork(network, SWITCH_ID_4, SWITCH_ID_1, WEIGHT_FUNCTION);
        List<Edge> fpath = pairPath.getLeft();
        assertThat(fpath, Matchers.hasSize(2));
        assertEquals(SWITCH_ID_2, fpath.get(1).getSrcSwitch().getSwitchId());

        List<Edge> rpath = pairPath.getRight();
        assertThat(rpath, Matchers.hasSize(2));
        assertEquals(SWITCH_ID_2, rpath.get(0).getDestSwitch().getSwitchId());
    }

    @Test
    public void shouldChooseDeeperOverExpensive() throws  UnroutableFlowException {
        AvailableNetwork network = buildLongAndExpensivePathsNetwork();

        BestWeightAndShortestPathFinder pathFinder = new BestWeightAndShortestPathFinder(4);
        Pair<List<Edge>, List<Edge>> pairPath =
                pathFinder.findPathInNetwork(network, SWITCH_ID_1, SWITCH_ID_4, WEIGHT_FUNCTION);
        List<Edge> fpath = pairPath.getLeft();
        assertThat(fpath, Matchers.hasSize(4));
        assertEquals(SWITCH_ID_5, fpath.get(3).getSrcSwitch().getSwitchId());

        List<Edge> rpath = pairPath.getRight();
        assertThat(rpath, Matchers.hasSize(4));
        assertEquals(SWITCH_ID_5, rpath.get(0).getDestSwitch().getSwitchId());
    }

    @Test
    public void shouldChooseCheaperWithSameDepth() throws  UnroutableFlowException {
        AvailableNetwork network = buildLongAndExpensivePathsNetwork();

        BestWeightAndShortestPathFinder pathFinder = new BestWeightAndShortestPathFinder(3);
        Pair<List<Edge>, List<Edge>> pairPath =
                pathFinder.findPathInNetwork(network, SWITCH_ID_1, SWITCH_ID_5, WEIGHT_FUNCTION);
        List<Edge> fpath = pairPath.getLeft();
        assertThat(fpath, Matchers.hasSize(3));
        assertEquals(SWITCH_ID_3, fpath.get(2).getSrcSwitch().getSwitchId());

        List<Edge> rpath = pairPath.getRight();
        assertThat(rpath, Matchers.hasSize(3));
        assertEquals(SWITCH_ID_3, rpath.get(0).getDestSwitch().getSwitchId());
    }

    private AvailableNetwork buildLongAndExpensivePathsNetwork() {
        /*
         *   Topology:
         *
         *   SW1---SW2~~~SW4
         *          |     |
         *         SW3---SW5
         *
         *   SW2 - SW4 is expensive by cost.
         */
        AvailableNetwork network = new AvailableNetwork();
        addBidirectionalLink(network, SWITCH_ID_1, SWITCH_ID_2, 1, 2, 100);
        addBidirectionalLink(network, SWITCH_ID_2, SWITCH_ID_4, 3, 4, 10000);
        addBidirectionalLink(network, SWITCH_ID_2, SWITCH_ID_3, 5, 6, 100);
        addBidirectionalLink(network, SWITCH_ID_3, SWITCH_ID_5, 7, 8, 100);
        addBidirectionalLink(network, SWITCH_ID_4, SWITCH_ID_5, 9, 10, 100);

        network.reduceByWeight(WEIGHT_FUNCTION);
        return network;
    }

    @Test(expected = UnroutableFlowException.class)
    public void shouldFailWhenPathIsLongerThenAllowedDepth() throws UnroutableFlowException {
        AvailableNetwork network = buildTestNetwork();

        BestWeightAndShortestPathFinder pathFinder = new BestWeightAndShortestPathFinder(1);
        pathFinder.findPathInNetwork(network, SWITCH_ID_D, SWITCH_ID_F, WEIGHT_FUNCTION);
    }

    @Test
    public void shouldReturnTheShortestPath() throws  UnroutableFlowException {
        AvailableNetwork network = buildTestNetwork();

        BestWeightAndShortestPathFinder pathFinder = new BestWeightAndShortestPathFinder(ALLOWED_DEPTH);
        Pair<List<Edge>, List<Edge>> pairPath =
                pathFinder.findPathInNetwork(network, SWITCH_ID_E, SWITCH_ID_F, WEIGHT_FUNCTION);
        List<Edge> fpath = pairPath.getLeft();
        assertThat(fpath, Matchers.hasSize(2));
        assertEquals(SWITCH_ID_E, fpath.get(0).getSrcSwitch().getSwitchId());
        assertEquals(SWITCH_ID_F, fpath.get(1).getDestSwitch().getSwitchId());

        List<Edge> rpath = pairPath.getRight();
        assertThat(rpath, Matchers.hasSize(2));
        assertEquals(SWITCH_ID_F, rpath.get(0).getSrcSwitch().getSwitchId());
        assertEquals(SWITCH_ID_E, rpath.get(1).getDestSwitch().getSwitchId());
    }

    @Test(expected = UnroutableFlowException.class)
    public void failToFindASwitch() throws  UnroutableFlowException {
        AvailableNetwork network = buildTestNetwork();

        SwitchId srcDpid = new SwitchId("00:00:00:00:00:00:00:ff");

        BestWeightAndShortestPathFinder pathFinder = new BestWeightAndShortestPathFinder(ALLOWED_DEPTH);
        pathFinder.findPathInNetwork(network, srcDpid, SWITCH_ID_F, WEIGHT_FUNCTION);
    }

    @Test
    public void testForwardAndBackwardPathsEquality() throws UnroutableFlowException {
        AvailableNetwork network = buildEqualCostsNetwork();
        BestWeightAndShortestPathFinder pathFinder = new BestWeightAndShortestPathFinder(ALLOWED_DEPTH);
        Pair<List<Edge>, List<Edge>> paths =
                pathFinder.findPathInNetwork(network, SWITCH_ID_1, SWITCH_ID_5, WEIGHT_FUNCTION);

        List<SwitchId> forwardSwitchPath = getSwitchIdsFlowPath(paths.getLeft());
        List<SwitchId> backwardSwitchPath = Lists.reverse(getSwitchIdsFlowPath(paths.getRight()));
        assertEquals(forwardSwitchPath, backwardSwitchPath);
    }

    @Test
    public void shouldAddIntermediateSwitchWeightOnce() throws UnroutableFlowException {
        AvailableNetwork network = buildTestNetwork();
        // shouldn't affect path if added once
        network.getSwitch(SWITCH_ID_A).increaseDiversityGroupUseCounter();

        BestWeightAndShortestPathFinder pathFinder = new BestWeightAndShortestPathFinder(ALLOWED_DEPTH);
        Pair<List<Edge>, List<Edge>> paths =
                pathFinder.findPathInNetwork(network, SWITCH_ID_D, SWITCH_ID_F, WEIGHT_FUNCTION);

        assertEquals(Arrays.asList(SWITCH_ID_D, SWITCH_ID_A, SWITCH_ID_F), getSwitchIdsFlowPath(paths.getLeft()));
    }

    @Test
    public void shouldFindSymmetricPath() throws UnroutableFlowException {
        AvailableNetwork network = buildLinearNetworkWithPairLinks();
        BestWeightAndShortestPathFinder pathFinder = new BestWeightAndShortestPathFinder(2);

        Pair<List<Edge>, List<Edge>> pathPair =
                pathFinder.findPathInNetwork(network, SWITCH_ID_1, SWITCH_ID_3, WEIGHT_FUNCTION);
        List<Edge> forward = pathPair.getLeft();
        List<Edge> reverse = Lists.reverse(pathPair.getRight());

        List<Boolean> validation = IntStream.range(0, forward.size())
                .mapToObj(i -> Objects.equals(forward.get(i).getSrcPort(), reverse.get(i).getDestPort()))
                .collect(Collectors.toList());
        assertFalse(validation.contains(false));
    }

    private AvailableNetwork buildLinearNetworkWithPairLinks() {
        /*
         * Topology:
         *
         * SW1===SW2===SW3
         *
         * All ISLs have equal cost.
         */
        AvailableNetwork network = new AvailableNetwork();
        addBidirectionalLink(network, SWITCH_ID_1, SWITCH_ID_2, 1, 2, 10000);
        addBidirectionalLink(network, SWITCH_ID_1, SWITCH_ID_2, 3, 4, 10000);
        addBidirectionalLink(network, SWITCH_ID_2, SWITCH_ID_3, 5, 6, 10000);
        addBidirectionalLink(network, SWITCH_ID_2, SWITCH_ID_3, 7, 8, 10000);
        return network;
    }

    private AvailableNetwork buildEqualCostsNetwork() {
        /*
         *   Topology:
         *
         *   SW1---SW2---SW4
         *          |     |
         *         SW3---SW5
         *
         *   All ISLs have equal cost.
         */
        AvailableNetwork network = new AvailableNetwork();
        addBidirectionalLink(network, SWITCH_ID_1, SWITCH_ID_2, 1, 2, 100);
        addBidirectionalLink(network, SWITCH_ID_2, SWITCH_ID_4, 3, 4, 100);
        addBidirectionalLink(network, SWITCH_ID_2, SWITCH_ID_3, 5, 6, 100);
        addBidirectionalLink(network, SWITCH_ID_3, SWITCH_ID_5, 7, 8, 100);
        addBidirectionalLink(network, SWITCH_ID_4, SWITCH_ID_5, 9, 10, 100);

        network.reduceByWeight(WEIGHT_FUNCTION);
        return network;
    }

    @Test
    public void testForwardAndBackwardPathsEqualityEvenWhenReverseHasCheaperPath()
            throws  UnroutableFlowException {
        // since our ISLs are bidirectional and cost may vary, we need to be sure that cost on reverse ISL won't be
        // taken into account during searching of reverse path.
        AvailableNetwork network = buildNetworkWithCostInReversePathBiggerThanForward();

        BestWeightAndShortestPathFinder pathFinder = new BestWeightAndShortestPathFinder(ALLOWED_DEPTH);
        Pair<List<Edge>, List<Edge>> paths =
                pathFinder.findPathInNetwork(network, SWITCH_ID_1, SWITCH_ID_5, WEIGHT_FUNCTION);

        List<SwitchId> forwardSwitchPath = getSwitchIdsFlowPath(paths.getLeft());
        List<SwitchId> backwardSwitchPath = Lists.reverse(getSwitchIdsFlowPath(paths.getRight()));
        assertEquals(forwardSwitchPath, backwardSwitchPath);
    }

    private AvailableNetwork buildNetworkWithCostInReversePathBiggerThanForward() {
        /*
         *   Topology:
         *
         *   SW1---SW2---SW4
         *          |     |
         *         SW3---SW5
         *
         *   SW3---SW5 isl has lower cost then reverse one.
         */
        AvailableNetwork network = new AvailableNetwork();
        addBidirectionalLink(network, SWITCH_ID_1, SWITCH_ID_2, 1, 2, 100);
        addBidirectionalLink(network, SWITCH_ID_2, SWITCH_ID_4, 3, 4, 100);
        addBidirectionalLink(network, SWITCH_ID_2, SWITCH_ID_3, 5, 68, 100);
        addLink(network, SWITCH_ID_3, SWITCH_ID_5, 7, 8, 10, 100, null, false);
        addLink(network, SWITCH_ID_5, SWITCH_ID_3, 8, 7, 10000, 100, null, false);
        addLink(network, SWITCH_ID_4, SWITCH_ID_5, 9, 10, 100, 100, null, false);
        addLink(network, SWITCH_ID_5, SWITCH_ID_4, 10, 9, 100, 100, null, false);

        network.reduceByWeight(WEIGHT_FUNCTION);
        return network;
    }


    @Test
    public void shouldHandleVeryExpensiveLinks() throws  UnroutableFlowException {
        AvailableNetwork network = buildExpensiveNetwork();

        BestWeightAndShortestPathFinder pathFinder = new BestWeightAndShortestPathFinder(ALLOWED_DEPTH);
        Pair<List<Edge>, List<Edge>> paths =
                pathFinder.findPathInNetwork(network, SWITCH_ID_1, SWITCH_ID_3, WEIGHT_FUNCTION);

        List<SwitchId> forwardSwitchPath = getSwitchIdsFlowPath(paths.getLeft());
        List<SwitchId> reverseSwitchPath = Lists.reverse(getSwitchIdsFlowPath(paths.getRight()));
        assertEquals(forwardSwitchPath, reverseSwitchPath);
        assertEquals(forwardSwitchPath, Lists.newArrayList(SWITCH_ID_1, SWITCH_ID_3));
    }

    private AvailableNetwork buildExpensiveNetwork() {
        /*
         *   Triangle topology:
         *
         *   SW1---2 000 000 000---SW2---2 000 000 000---SW3
         *   |                                           |
         *   +---------------------1---------------------+
         */

        AvailableNetwork network = new AvailableNetwork();
        addBidirectionalLink(network, SWITCH_ID_1, SWITCH_ID_2, 1, 2, 2000000000); //cost near to MAX_INTEGER
        addBidirectionalLink(network, SWITCH_ID_2, SWITCH_ID_3, 3, 4, 2000000000); //cost near to MAX_INTEGER
        addBidirectionalLink(network, SWITCH_ID_1, SWITCH_ID_3, 5, 6, 1);

        network.reduceByWeight(WEIGHT_FUNCTION);
        return network;
    }

    private List<SwitchId> getSwitchIdsFlowPath(List<Edge> path) {
        List<SwitchId> switchIds = new ArrayList<>();
        if (!path.isEmpty()) {
            switchIds.add(path.get(0).getSrcSwitch().getSwitchId());
            for (Edge edge : path) {
                switchIds.add(edge.getDestSwitch().getSwitchId());
            }
        }
        return switchIds;
    }

    private void addBidirectionalLink(AvailableNetwork network, SwitchId firstSwitch, SwitchId secondSwitch,
                                      int srcPort, int dstPort, int cost) {
        addLink(network, firstSwitch, secondSwitch, srcPort, dstPort, cost, 1, null, false);
        addLink(network, secondSwitch, firstSwitch, dstPort, srcPort, cost, 1, null, false);
    }

    private AvailableNetwork buildTestNetwork() {
        /*
         *   Topology:
         *
         *   D---C---F---B---E
         *   |   |   |   |   |
         *   |   +---A---+   |
         *   |      / \      |
         *   +-----+   +-----+
         */
        AvailableNetwork network = new AvailableNetwork();
        addLink(network, SWITCH_ID_A, SWITCH_ID_F,
                7, 60, 0, 3, null, false);
        addLink(network, SWITCH_ID_A, SWITCH_ID_B,
                5, 32, 10, 18, null, false);
        addLink(network, SWITCH_ID_A, SWITCH_ID_D,
                2, 2, 10, 2, null, false);
        addLink(network, SWITCH_ID_A, SWITCH_ID_E,
                6, 16, 10, 15, null, false);
        addLink(network, SWITCH_ID_A, SWITCH_ID_C,
                1, 3, 40, 4, null, false);
        addLink(network, SWITCH_ID_D, SWITCH_ID_C,
                1, 1, 100, 7, null, false);
        addLink(network, SWITCH_ID_D, SWITCH_ID_A,
                2, 2, 10, 1, null, false);
        addLink(network, SWITCH_ID_C, SWITCH_ID_F,
                6, 19, 10, 3, null, false);
        addLink(network, SWITCH_ID_C, SWITCH_ID_D,
                1, 1, 100, 2, null, false);
        addLink(network, SWITCH_ID_C, SWITCH_ID_A,
                3, 1, 100, 2, null, false);
        addLink(network, SWITCH_ID_E, SWITCH_ID_B,
                52, 52, 10, 381, null, false);
        addLink(network, SWITCH_ID_E, SWITCH_ID_A,
                16, 6, 10, 18, null, false);
        addLink(network, SWITCH_ID_B, SWITCH_ID_F,
                48, 49, 10, 97, null, false);
        addLink(network, SWITCH_ID_B, SWITCH_ID_E,
                52, 52, 10, 1021, null, false);
        addLink(network, SWITCH_ID_B, SWITCH_ID_A,
                32, 5, 10, 16, null, false);
        addLink(network, SWITCH_ID_F, SWITCH_ID_B,
                49, 48, 10, 0, null, false);
        addLink(network, SWITCH_ID_F, SWITCH_ID_C,
                19, 6, 10, 3, null, false);
        addLink(network, SWITCH_ID_F, SWITCH_ID_A,
                50, 7, 0, 3, null, false);

        network.reduceByWeight(WEIGHT_FUNCTION);
        return network;
    }

    @Test
    public void shouldBuildPathDependsOnIslConfig() throws  UnroutableFlowException {
        // Network without unstable and under maintenance links.
        AvailableNetwork network = buildTestNetworkForVerifyIslConfig(false, false);

        BestWeightAndShortestPathFinder pathFinder = new BestWeightAndShortestPathFinder(ALLOWED_DEPTH);
        Pair<List<Edge>, List<Edge>> pairPath =
                pathFinder.findPathInNetwork(network, SWITCH_ID_A, SWITCH_ID_B, WEIGHT_FUNCTION);
        List<Edge> forwardPath = pairPath.getLeft();
        assertThat(forwardPath, Matchers.hasSize(1));
        assertEquals(SWITCH_ID_A, forwardPath.get(0).getSrcSwitch().getSwitchId());
        assertEquals(SWITCH_ID_B, forwardPath.get(0).getDestSwitch().getSwitchId());

        List<Edge> reversePath = pairPath.getRight();
        assertThat(reversePath, Matchers.hasSize(1));
        assertEquals(SWITCH_ID_B, reversePath.get(0).getSrcSwitch().getSwitchId());
        assertEquals(SWITCH_ID_A, reversePath.get(0).getDestSwitch().getSwitchId());

        // Network where shortest path has under maintenance link.
        network = buildTestNetworkForVerifyIslConfig(true, false);

        pairPath = pathFinder.findPathInNetwork(network, SWITCH_ID_A, SWITCH_ID_B, WEIGHT_FUNCTION);
        forwardPath = pairPath.getLeft();
        assertThat(forwardPath, Matchers.hasSize(2));
        assertEquals(SWITCH_ID_A, forwardPath.get(0).getSrcSwitch().getSwitchId());
        assertEquals(SWITCH_ID_C, forwardPath.get(0).getDestSwitch().getSwitchId());
        assertEquals(SWITCH_ID_C, forwardPath.get(1).getSrcSwitch().getSwitchId());
        assertEquals(SWITCH_ID_B, forwardPath.get(1).getDestSwitch().getSwitchId());

        reversePath = pairPath.getRight();
        assertThat(reversePath, Matchers.hasSize(2));
        assertEquals(SWITCH_ID_B, reversePath.get(0).getSrcSwitch().getSwitchId());
        assertEquals(SWITCH_ID_C, reversePath.get(0).getDestSwitch().getSwitchId());
        assertEquals(SWITCH_ID_C, reversePath.get(1).getSrcSwitch().getSwitchId());
        assertEquals(SWITCH_ID_A, reversePath.get(1).getDestSwitch().getSwitchId());

        // Network where shortest path has under maintenance link and another short path has unstable link.
        network = buildTestNetworkForVerifyIslConfig(true, true);

        pairPath = pathFinder.findPathInNetwork(network, SWITCH_ID_A, SWITCH_ID_B, WEIGHT_FUNCTION);
        forwardPath = pairPath.getLeft();
        assertThat(forwardPath, Matchers.hasSize(3));
        assertEquals(SWITCH_ID_A, forwardPath.get(0).getSrcSwitch().getSwitchId());
        assertEquals(SWITCH_ID_D, forwardPath.get(0).getDestSwitch().getSwitchId());
        assertEquals(SWITCH_ID_D, forwardPath.get(1).getSrcSwitch().getSwitchId());
        assertEquals(SWITCH_ID_E, forwardPath.get(1).getDestSwitch().getSwitchId());
        assertEquals(SWITCH_ID_E, forwardPath.get(2).getSrcSwitch().getSwitchId());
        assertEquals(SWITCH_ID_B, forwardPath.get(2).getDestSwitch().getSwitchId());

        reversePath = pairPath.getRight();
        assertThat(reversePath, Matchers.hasSize(3));
        assertEquals(SWITCH_ID_B, reversePath.get(0).getSrcSwitch().getSwitchId());
        assertEquals(SWITCH_ID_E, reversePath.get(0).getDestSwitch().getSwitchId());
        assertEquals(SWITCH_ID_E, reversePath.get(1).getSrcSwitch().getSwitchId());
        assertEquals(SWITCH_ID_D, reversePath.get(1).getDestSwitch().getSwitchId());
        assertEquals(SWITCH_ID_D, reversePath.get(2).getSrcSwitch().getSwitchId());
        assertEquals(SWITCH_ID_A, reversePath.get(2).getDestSwitch().getSwitchId());
    }

    private AvailableNetwork buildTestNetworkForVerifyIslConfig(boolean shortestPathHasUnderMaintenanceLink,
                                                                boolean shortPathHasUnstableLink) {
        /*
         *   Topology:
         *
         *   A-------B
         *   |\     /|
         *   | +-C-+ |
         *   D-------E
         */
        AvailableNetwork network = new AvailableNetwork();
        addLink(network, SWITCH_ID_A, SWITCH_ID_B,
                1, 1, 0, 0, null, shortestPathHasUnderMaintenanceLink);

        addLink(network, SWITCH_ID_A, SWITCH_ID_C,
                2, 1, 0, 0, null, false);
        addLink(network, SWITCH_ID_C, SWITCH_ID_B,
                2, 2, 0, 0, shortPathHasUnstableLink ? Instant.now() : null, false);

        addLink(network, SWITCH_ID_A, SWITCH_ID_D,
                3, 1, 0, 0, null, false);
        addLink(network, SWITCH_ID_D, SWITCH_ID_E,
                1, 1, 0, 0, null, false);
        addLink(network, SWITCH_ID_E, SWITCH_ID_B,
                2, 3, 0, 0, null, false);

        network.reduceByWeight(WEIGHT_FUNCTION);
        return network;
    }

    @Test
    public void shouldFindNPath() throws  UnroutableFlowException {
        AvailableNetwork network = buildTestNetworkForTestYensAlgorithm();
        BestWeightAndShortestPathFinder pathFinder = new BestWeightAndShortestPathFinder(ALLOWED_DEPTH);
        List<List<SwitchId>> expectedPaths = new ArrayList<>();

        expectedPaths.add(Lists.newArrayList(SWITCH_ID_A, SWITCH_ID_D, SWITCH_ID_C, SWITCH_ID_F));
        List<List<Edge>> paths =
                pathFinder.findNPathsBetweenSwitches(network, SWITCH_ID_A, SWITCH_ID_F, 1, WEIGHT_FUNCTION);
        assertEquals(expectedPaths, convertPaths(paths));

        expectedPaths.add(Lists.newArrayList(SWITCH_ID_A, SWITCH_ID_D, SWITCH_ID_E, SWITCH_ID_F));
        paths = pathFinder.findNPathsBetweenSwitches(network, SWITCH_ID_A, SWITCH_ID_F, 2, WEIGHT_FUNCTION);
        assertEquals(expectedPaths, convertPaths(paths));

        expectedPaths.add(Lists.newArrayList(SWITCH_ID_A, SWITCH_ID_B, SWITCH_ID_D, SWITCH_ID_C, SWITCH_ID_F));
        paths = pathFinder.findNPathsBetweenSwitches(network, SWITCH_ID_A, SWITCH_ID_F, 3, WEIGHT_FUNCTION);
        assertEquals(expectedPaths, convertPaths(paths));

        expectedPaths.add(Lists.newArrayList(SWITCH_ID_A, SWITCH_ID_D, SWITCH_ID_E, SWITCH_ID_C, SWITCH_ID_F));
        paths = pathFinder.findNPathsBetweenSwitches(network, SWITCH_ID_A, SWITCH_ID_F, 4, WEIGHT_FUNCTION);
        assertEquals(expectedPaths, convertPaths(paths));

        expectedPaths.add(Lists.newArrayList(SWITCH_ID_A, SWITCH_ID_D, SWITCH_ID_B, SWITCH_ID_C, SWITCH_ID_F));
        paths = pathFinder.findNPathsBetweenSwitches(network, SWITCH_ID_A, SWITCH_ID_F, 5, WEIGHT_FUNCTION);
        assertEquals(expectedPaths, convertPaths(paths));

        expectedPaths.add(Lists.newArrayList(SWITCH_ID_A, SWITCH_ID_D, SWITCH_ID_C, SWITCH_ID_E, SWITCH_ID_F));
        paths = pathFinder.findNPathsBetweenSwitches(network, SWITCH_ID_A, SWITCH_ID_F, 6, WEIGHT_FUNCTION);
        assertEquals(expectedPaths, convertPaths(paths));

        expectedPaths.add(Lists.newArrayList(SWITCH_ID_A, SWITCH_ID_B, SWITCH_ID_C, SWITCH_ID_F));
        paths = pathFinder.findNPathsBetweenSwitches(network, SWITCH_ID_A, SWITCH_ID_F, 7, WEIGHT_FUNCTION);
        assertEquals(expectedPaths, convertPaths(paths));

        expectedPaths.add(Lists.newArrayList(SWITCH_ID_A, SWITCH_ID_B, SWITCH_ID_D, SWITCH_ID_E, SWITCH_ID_F));
        paths = pathFinder.findNPathsBetweenSwitches(network, SWITCH_ID_A, SWITCH_ID_F, 8, WEIGHT_FUNCTION);
        assertEquals(expectedPaths, convertPaths(paths));

        expectedPaths
                .add(Lists.newArrayList(SWITCH_ID_A, SWITCH_ID_B, SWITCH_ID_D, SWITCH_ID_C, SWITCH_ID_E, SWITCH_ID_F));
        paths = pathFinder.findNPathsBetweenSwitches(network, SWITCH_ID_A, SWITCH_ID_F, 9, WEIGHT_FUNCTION);
        assertEquals(expectedPaths, convertPaths(paths));

        expectedPaths
                .add(Lists.newArrayList(SWITCH_ID_A, SWITCH_ID_B, SWITCH_ID_D, SWITCH_ID_E, SWITCH_ID_C, SWITCH_ID_F));
        paths = pathFinder.findNPathsBetweenSwitches(network, SWITCH_ID_A, SWITCH_ID_F, 10, WEIGHT_FUNCTION);
        assertEquals(expectedPaths, convertPaths(paths));

        expectedPaths
                .add(Lists.newArrayList(SWITCH_ID_A, SWITCH_ID_D, SWITCH_ID_B, SWITCH_ID_C, SWITCH_ID_E, SWITCH_ID_F));
        paths = pathFinder.findNPathsBetweenSwitches(network, SWITCH_ID_A, SWITCH_ID_F, 11, WEIGHT_FUNCTION);
        assertEquals(expectedPaths, convertPaths(paths));

        expectedPaths.add(Lists.newArrayList(SWITCH_ID_A, SWITCH_ID_B, SWITCH_ID_C, SWITCH_ID_E, SWITCH_ID_F));
        paths = pathFinder.findNPathsBetweenSwitches(network, SWITCH_ID_A, SWITCH_ID_F, 12, WEIGHT_FUNCTION);
        assertEquals(expectedPaths, convertPaths(paths));

        expectedPaths
                .add(Lists.newArrayList(SWITCH_ID_A, SWITCH_ID_B, SWITCH_ID_C, SWITCH_ID_D, SWITCH_ID_E, SWITCH_ID_F));
        paths = pathFinder.findNPathsBetweenSwitches(network, SWITCH_ID_A, SWITCH_ID_F, 13, WEIGHT_FUNCTION);
        assertEquals(expectedPaths, convertPaths(paths));

        paths = pathFinder.findNPathsBetweenSwitches(network, SWITCH_ID_A, SWITCH_ID_F, 500, WEIGHT_FUNCTION);
        assertEquals(expectedPaths, convertPaths(paths));
    }

    private AvailableNetwork buildTestNetworkForTestYensAlgorithm() {
        /*
         *   Topology:
         *
         *   A--B--C
         *    \ | /|\
         *     \|/ | \
         *      D--E--F
         */
        AvailableNetwork network = new AvailableNetwork();
        addBidirectionalLink(network, SWITCH_ID_A, SWITCH_ID_B, 1, 1, 3);
        addBidirectionalLink(network, SWITCH_ID_A, SWITCH_ID_D, 2, 1, 2);
        addBidirectionalLink(network, SWITCH_ID_B, SWITCH_ID_C, 2, 1, 4);
        addBidirectionalLink(network, SWITCH_ID_B, SWITCH_ID_D, 3, 2, 1);
        addBidirectionalLink(network, SWITCH_ID_C, SWITCH_ID_D, 2, 3, 2);
        addBidirectionalLink(network, SWITCH_ID_C, SWITCH_ID_E, 3, 1, 2);
        addBidirectionalLink(network, SWITCH_ID_C, SWITCH_ID_F, 4, 1, 1);
        addBidirectionalLink(network, SWITCH_ID_D, SWITCH_ID_E, 4, 2, 3);
        addBidirectionalLink(network, SWITCH_ID_E, SWITCH_ID_F, 3, 2, 2);

        network.reduceByWeight(WEIGHT_FUNCTION);
        return network;
    }

    private List<List<SwitchId>> convertPaths(List<List<Edge>> paths) {
        List<List<SwitchId>> convertedPaths = new ArrayList<>();
        for (List<Edge> path : paths) {
            List<SwitchId> convertedPath = new ArrayList<>();
            for (Edge edge : path) {
                convertedPath.add(edge.getSrcSwitch().getSwitchId());
            }
            convertedPath.add(path.get(path.size() - 1).getDestSwitch().getSwitchId());
            convertedPaths.add(convertedPath);
        }
        return convertedPaths;
    }

    private void addLink(AvailableNetwork network, SwitchId srcDpid, SwitchId dstDpid, int srcPort, int dstPort,
                         int cost, int latency, Instant timeUnstable, boolean isUnderMaintenance) {
        Switch srcSwitch = Switch.builder().switchId(srcDpid).build();
        Switch dstSwitch = Switch.builder().switchId(dstDpid).build();
        IslConfig islConfig = IslConfig.builder()
                .unstableIslTimeout(Duration.ofSeconds(120))
                .build();

        Isl isl = Isl.builder()
                .srcSwitch(srcSwitch)
                .destSwitch(dstSwitch)
                .srcPort(srcPort)
                .destPort(dstPort)
                .cost(cost)
                .latency(latency)
                .timeUnstable(timeUnstable)
                .underMaintenance(isUnderMaintenance)
                .build();
        isl.setIslConfig(islConfig);
        network.addLink(isl);
    }

}
