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
import static org.junit.Assert.assertThat;

import org.openkilda.model.Isl;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.exception.SwitchNotFoundException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.pce.impl.AvailableNetwork;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public abstract class PathFinderTest {
    static final int ALLOWED_DEPTH = 35;
    static final int DEFAULT_COST = 700;

    static final SwitchId SWITCH_ID_A = new SwitchId("00:00:00:22:3d:5a:04:87");
    static final SwitchId SWITCH_ID_B = new SwitchId("00:00:70:72:cf:d2:48:6c");
    static final SwitchId SWITCH_ID_C = new SwitchId("00:00:00:22:3d:6c:00:b8");
    static final SwitchId SWITCH_ID_D = new SwitchId("00:00:00:22:3d:6b:00:04");
    static final SwitchId SWITCH_ID_E = new SwitchId("00:00:70:72:cf:d2:47:a6");
    static final SwitchId SWITCH_ID_F = new SwitchId("00:00:b0:d2:f5:00:5a:b8");

    static final SwitchId SWITCH_ID_1 = new SwitchId("00:00:00:00:00:00:00:01");
    static final SwitchId SWITCH_ID_2 = new SwitchId("00:00:00:00:00:00:00:02");
    static final SwitchId SWITCH_ID_3 = new SwitchId("00:00:00:00:00:00:00:03");
    static final SwitchId SWITCH_ID_4 = new SwitchId("00:00:00:00:00:00:00:04");
    static final SwitchId SWITCH_ID_5 = new SwitchId("00:00:00:00:00:00:00:05");

    abstract PathFinder getPathFinder();

    @Test
    public void shouldChooseDeeperOverExpensive() throws SwitchNotFoundException, UnroutableFlowException {
        AvailableNetwork network = buildLongAndExpensivePathsNetwork();
        network.reduceByCost();

        Pair<List<Isl>, List<Isl>> pairPath = getPathFinder().findPathInNetwork(network, SWITCH_ID_1, SWITCH_ID_4);
        List<Isl> fpath = pairPath.getLeft();
        assertThat(fpath, Matchers.hasSize(4));
        assertEquals(SWITCH_ID_5, fpath.get(3).getSrcSwitch().getSwitchId());

        List<Isl> rpath = pairPath.getRight();
        assertThat(rpath, Matchers.hasSize(4));
        assertEquals(SWITCH_ID_5, rpath.get(0).getDestSwitch().getSwitchId());
    }

    @Test
    public void shouldChooseCheaperWithSameDepth() throws SwitchNotFoundException, UnroutableFlowException {
        AvailableNetwork network = buildLongAndExpensivePathsNetwork();
        network.reduceByCost();

        Pair<List<Isl>, List<Isl>> pairPath = getPathFinder().findPathInNetwork(network, SWITCH_ID_1, SWITCH_ID_5);
        List<Isl> fpath = pairPath.getLeft();
        assertThat(fpath, Matchers.hasSize(3));
        assertEquals(SWITCH_ID_3, fpath.get(2).getSrcSwitch().getSwitchId());

        List<Isl> rpath = pairPath.getRight();
        assertThat(rpath, Matchers.hasSize(3));
        assertEquals(SWITCH_ID_3, rpath.get(0).getDestSwitch().getSwitchId());
    }

    @Test
    public void testForwardAndBackwardPathsEquality() throws SwitchNotFoundException, UnroutableFlowException {
        AvailableNetwork network = buildEqualCostsNetwork();
        network.reduceByCost();
        Pair<List<Isl>, List<Isl>> paths = getPathFinder().findPathInNetwork(network, SWITCH_ID_1, SWITCH_ID_5);

        List<SwitchId> forwardSwitchPath = getSwitchIdsFlowPath(paths.getLeft());
        List<SwitchId> backwardSwitchPath = Lists.reverse(getSwitchIdsFlowPath(paths.getRight()));
        assertEquals(forwardSwitchPath, backwardSwitchPath);
    }

    @Test
    public void shouldHandleVeryExpensiveLinks() throws SwitchNotFoundException, UnroutableFlowException {
        AvailableNetwork network = buildExpensiveNetwork();
        network.reduceByCost();
        Pair<List<Isl>, List<Isl>> paths = getPathFinder().findPathInNetwork(network, SWITCH_ID_1, SWITCH_ID_3);

        List<SwitchId> forwardSwitchPath = getSwitchIdsFlowPath(paths.getLeft());
        List<SwitchId> reverseSwitchPath = Lists.reverse(getSwitchIdsFlowPath(paths.getRight()));
        assertEquals(forwardSwitchPath, reverseSwitchPath);
        assertEquals(forwardSwitchPath, Lists.newArrayList(SWITCH_ID_1, SWITCH_ID_3));
    }

    @Test
    public void shouldReturnTheShortestPath() throws SwitchNotFoundException, UnroutableFlowException {
        AvailableNetwork network = buildTestNetwork();
        network.reduceByCost();

        Pair<List<Isl>, List<Isl>> pairPath = getPathFinder().findPathInNetwork(network, SWITCH_ID_E, SWITCH_ID_F);
        List<Isl> fpath = pairPath.getLeft();
        assertThat(fpath, Matchers.hasSize(2));
        assertEquals(SWITCH_ID_E, fpath.get(0).getSrcSwitch().getSwitchId());
        assertEquals(SWITCH_ID_F, fpath.get(1).getDestSwitch().getSwitchId());

        List<Isl> rpath = pairPath.getRight();
        assertThat(rpath, Matchers.hasSize(2));
        assertEquals(SWITCH_ID_F, rpath.get(0).getSrcSwitch().getSwitchId());
        assertEquals(SWITCH_ID_E, rpath.get(1).getDestSwitch().getSwitchId());
    }

    @Test
    public void testForwardAndBackwardPathsEqualityEvenWhenReverseHasCheaperPath()
            throws SwitchNotFoundException, UnroutableFlowException {
        // since our ISLs are bidirectional and cost may vary, we need to be sure that cost on reverse ISL won't be
        // taken into account during searching of reverse path.
        AvailableNetwork network = buildNetworkWithBandwidthInReversePathBiggerThanForward();
        network.reduceByCost();
        Pair<List<Isl>, List<Isl>> paths = getPathFinder().findPathInNetwork(network, SWITCH_ID_1, SWITCH_ID_5);

        List<SwitchId> forwardSwitchPath = getSwitchIdsFlowPath(paths.getLeft());
        List<SwitchId> backwardSwitchPath = Lists.reverse(getSwitchIdsFlowPath(paths.getRight()));
        assertEquals(forwardSwitchPath, backwardSwitchPath);
    }

    void addLink(AvailableNetwork network, SwitchId srcDpid, SwitchId dstDpid, int srcPort, int dstPort,
                           int cost, int latency) {
        Switch srcSwitch = Switch.builder().switchId(srcDpid).build();
        Switch dstSwitch = Switch.builder().switchId(dstDpid).build();

        Isl isl = Isl.builder()
                .srcSwitch(srcSwitch)
                .destSwitch(dstSwitch)
                .srcPort(srcPort)
                .destPort(dstPort)
                .cost(cost)
                .latency(latency)
                .build();
        network.addLink(isl);
    }

    AvailableNetwork buildLongAndExpensivePathsNetwork() {
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
        return network;
    }

    AvailableNetwork buildEqualCostsNetwork() {
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
        return network;
    }

    AvailableNetwork buildExpensiveNetwork() {
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
        return network;
    }

    AvailableNetwork buildTestNetwork() {
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
                7, 50, 0, 3);
        addLink(network, SWITCH_ID_A, SWITCH_ID_B,
                5, 32, 10, 18);
        addLink(network, SWITCH_ID_A, SWITCH_ID_D,
                2, 2, 10, 2);
        addLink(network, SWITCH_ID_A, SWITCH_ID_E,
                6, 16, 10, 15);
        addLink(network, SWITCH_ID_A, SWITCH_ID_C,
                1, 3, 40, 4);
        addLink(network, SWITCH_ID_D, SWITCH_ID_C,
                1, 1, 100, 7);
        addLink(network, SWITCH_ID_D, SWITCH_ID_A,
                2, 2, 10, 1);
        addLink(network, SWITCH_ID_C, SWITCH_ID_F,
                6, 19, 10, 3);
        addLink(network, SWITCH_ID_C, SWITCH_ID_D,
                1, 1, 100, 2);
        addLink(network, SWITCH_ID_C, SWITCH_ID_A,
                3, 1, 100, 2);
        addLink(network, SWITCH_ID_E, SWITCH_ID_B,
                52, 52, 10, 381);
        addLink(network, SWITCH_ID_E, SWITCH_ID_A,
                16, 6, 10, 18);
        addLink(network, SWITCH_ID_B, SWITCH_ID_F,
                48, 49, 10, 97);
        addLink(network, SWITCH_ID_B, SWITCH_ID_E,
                52, 52, 10, 1021);
        addLink(network, SWITCH_ID_B, SWITCH_ID_A,
                32, 5, 10, 16);
        addLink(network, SWITCH_ID_F, SWITCH_ID_B,
                49, 48, 10, 0);
        addLink(network, SWITCH_ID_F, SWITCH_ID_C,
                19, 6, 10, 3);
        addLink(network, SWITCH_ID_F, SWITCH_ID_A,
                50, 7, 0, 3);
        return network;
    }

    AvailableNetwork buildNetworkWithBandwidthInReversePathBiggerThanForward() {
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
        addBidirectionalLink(network, SWITCH_ID_2, SWITCH_ID_3, 5, 68, 100);
        addLink(network, SWITCH_ID_3, SWITCH_ID_5, 7, 8, 10, 100);
        addLink(network, SWITCH_ID_5, SWITCH_ID_3, 8, 7, 10000, 100);
        addLink(network, SWITCH_ID_4, SWITCH_ID_5, 9, 10, 100, 100);
        addLink(network, SWITCH_ID_5, SWITCH_ID_4, 10, 9, 100, 100);

        return network;
    }

    void addBidirectionalLink(AvailableNetwork network, SwitchId firstSwitch, SwitchId secondSwitch,
                                      int srcPort, int dstPort, int cost) {
        addLink(network, firstSwitch, secondSwitch, srcPort, dstPort, cost, 1);
        addLink(network, secondSwitch, firstSwitch, dstPort, srcPort, cost, 1);
    }

    List<SwitchId> getSwitchIdsFlowPath(List<Isl> path) {
        List<SwitchId> switchIds = new ArrayList<>();
        if (!path.isEmpty()) {
            switchIds.add(path.get(0).getSrcSwitch().getSwitchId());
            for (Isl isl : path) {
                switchIds.add(isl.getDestSwitch().getSwitchId());
            }
        }
        return switchIds;
    }
}
