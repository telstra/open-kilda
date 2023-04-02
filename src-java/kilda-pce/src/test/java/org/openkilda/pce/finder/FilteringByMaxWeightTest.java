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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import org.openkilda.model.SwitchId;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.pce.impl.AvailableNetwork;
import org.openkilda.pce.model.Edge;
import org.openkilda.pce.model.FindOneDirectionPathResult;
import org.openkilda.pce.model.PathWeight;
import org.openkilda.pce.model.WeightFunction;

import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class FilteringByMaxWeightTest {
    private static final int ALLOWED_DEPTH = 35;
    private static final WeightFunction WEIGHT_FUNCTION = edge -> new PathWeight(edge.getCost());
    private static final int SHORTEST_PATH_WEIGHT = 6;
    private static final int LONGEST_PATH_WEIGHT = 10;
    public static final String IS_BACKUP_PATH_PARAM = "backUpPathComputationWayUsed";

    private final AvailableNetwork network = new AvailableNetwork();
    private final BestWeightAndShortestPathFinder pathFinder = new BestWeightAndShortestPathFinder(ALLOWED_DEPTH);

    private static final SwitchId SWITCH_ID_1 = new SwitchId("00:00:00:00:00:00:00:01");
    private static final SwitchId SWITCH_ID_2 = new SwitchId("00:00:00:00:00:00:00:02");
    private static final SwitchId SWITCH_ID_3 = new SwitchId("00:00:00:00:00:00:00:03");
    private static final SwitchId SWITCH_ID_4 = new SwitchId("00:00:00:00:00:00:00:04");
    private static final SwitchId SWITCH_ID_5 = new SwitchId("00:00:00:00:00:00:00:05");


    @Before
    public void setUp() {
        /*
         *   Topology:
         *
         *   SW1--1--SW2--4--SW4
         *           2|      |5
         *         SW3--3--SW5
         *
         *   All ISLs have equal cost.
         */
        addBidirectionalLink(network, SWITCH_ID_1, SWITCH_ID_2, 1, 2, 1);
        addBidirectionalLink(network, SWITCH_ID_2, SWITCH_ID_3, 5, 6, 2);
        addBidirectionalLink(network, SWITCH_ID_3, SWITCH_ID_5, 7, 8, 3);
        addBidirectionalLink(network, SWITCH_ID_2, SWITCH_ID_4, 3, 4, 4);
        addBidirectionalLink(network, SWITCH_ID_4, SWITCH_ID_5, 9, 10, 5);

    }

    @Test
    public void shouldFindBothPaths() throws UnroutableFlowException {
        List<FindOneDirectionPathResult> paths = pathFinder
                .findNPathsBetweenSwitches(network, SWITCH_ID_1, SWITCH_ID_5, 5, WEIGHT_FUNCTION,
                        SHORTEST_PATH_WEIGHT + 1, LONGEST_PATH_WEIGHT + 1);

        assertThat(paths.size(), equalTo(2));
        assertThat(paths, hasItem(
                Matchers.<FindOneDirectionPathResult>hasProperty(IS_BACKUP_PATH_PARAM, equalTo(false))));
        assertThat(paths, hasItem(
                Matchers.<FindOneDirectionPathResult>hasProperty(IS_BACKUP_PATH_PARAM, equalTo(true))));

    }

    @Test
    public void shouldFindOnlyBackupPaths() throws UnroutableFlowException {
        List<FindOneDirectionPathResult> paths = pathFinder
                .findNPathsBetweenSwitches(network, SWITCH_ID_1, SWITCH_ID_5, 5, WEIGHT_FUNCTION,
                        SHORTEST_PATH_WEIGHT - 1, LONGEST_PATH_WEIGHT + 1);

        assertThat(paths.size(), equalTo(2));
        assertThat(paths, hasItem(
                Matchers.<FindOneDirectionPathResult>hasProperty(IS_BACKUP_PATH_PARAM, not(equalTo(false)))));
        assertThat(paths, hasItem(
                Matchers.<FindOneDirectionPathResult>hasProperty(IS_BACKUP_PATH_PARAM, equalTo(true))));

    }

    @Test
    public void shouldFindOnlyOneBackupPath() throws UnroutableFlowException {
        List<FindOneDirectionPathResult> paths = pathFinder
                .findNPathsBetweenSwitches(network, SWITCH_ID_1, SWITCH_ID_5, 5, WEIGHT_FUNCTION,
                        SHORTEST_PATH_WEIGHT - 1, LONGEST_PATH_WEIGHT - 1);

        assertThat(paths.size(), equalTo(1));
        assertThat(paths, hasItem(
                Matchers.<FindOneDirectionPathResult>hasProperty(IS_BACKUP_PATH_PARAM, equalTo(true))));

    }

    @Test
    public void shouldFindOnlyOneNotBackupPath() throws UnroutableFlowException {
        List<FindOneDirectionPathResult> paths = pathFinder
                .findNPathsBetweenSwitches(network, SWITCH_ID_1, SWITCH_ID_5, 5, WEIGHT_FUNCTION,
                        SHORTEST_PATH_WEIGHT + 1, LONGEST_PATH_WEIGHT - 1);

        assertThat(paths.size(), equalTo(1));
        assertThat(paths, hasItem(
                Matchers.<FindOneDirectionPathResult>hasProperty(IS_BACKUP_PATH_PARAM, equalTo(false))));

    }

    @Test
    public void shouldFindTwoBackupPaths() throws UnroutableFlowException {
        List<FindOneDirectionPathResult> paths = pathFinder
                .findNPathsBetweenSwitches(network, SWITCH_ID_1, SWITCH_ID_5, 5, WEIGHT_FUNCTION,
                        SHORTEST_PATH_WEIGHT - 1, LONGEST_PATH_WEIGHT + 1);

        assertThat(paths.size(), equalTo(2));
        assertThat(paths, hasItem(
                Matchers.<FindOneDirectionPathResult>hasProperty(IS_BACKUP_PATH_PARAM, not(equalTo(false)))));

    }

    @Test
    public void shouldFindTwoNotBackupPaths() throws UnroutableFlowException {
        List<FindOneDirectionPathResult> paths = pathFinder
                .findNPathsBetweenSwitches(network, SWITCH_ID_1, SWITCH_ID_5, 5, WEIGHT_FUNCTION,
                        LONGEST_PATH_WEIGHT + 1, Long.MAX_VALUE);

        assertThat(paths.size(), equalTo(2));
        assertThat(paths, hasItem(
                Matchers.<FindOneDirectionPathResult>hasProperty(IS_BACKUP_PATH_PARAM, not(equalTo(true)))));

    }


    private void addBidirectionalLink(AvailableNetwork network, SwitchId firstSwitch, SwitchId secondSwitch,
                                      int srcPort, int dstPort, int cost) {
        addLink(network, firstSwitch, secondSwitch, srcPort, dstPort, cost);
        addLink(network, secondSwitch, firstSwitch, dstPort, srcPort, cost);
    }

    private void addLink(AvailableNetwork network, SwitchId srcDpid, SwitchId dstDpid, int srcPort, int dstPort,
                         int cost) {
        Edge edge = Edge.builder()
                .srcSwitch(network.getOrAddNode(srcDpid, null))
                .srcPort(srcPort)
                .destSwitch(network.getOrAddNode(dstDpid, null))
                .destPort(dstPort)
                .latency(1)
                .cost(cost)
                .availableBandwidth(500000)
                .underMaintenance(false)
                .unstable(false)
                .build();
        network.addEdge(edge);
    }
}
