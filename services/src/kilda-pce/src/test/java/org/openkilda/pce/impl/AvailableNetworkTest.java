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

package org.openkilda.pce.impl;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.openkilda.model.Isl;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.impl.model.Edge;
import org.openkilda.pce.impl.model.Node;

import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class AvailableNetworkTest extends BasePathComputerTest {
    private static final SwitchId FIRST_SW = new SwitchId("00:00:00:00:00:00:00:01");
    private static final SwitchId SECOND_SW = new SwitchId("00:00:00:00:00:00:00:02");
    private static final SwitchId THIRD_SW = new SwitchId("00:00:00:00:00:00:00:03");
    private static final SwitchId FOURTH_SW = new SwitchId("00:00:00:00:00:00:00:04");
    private static final SwitchId LAST_SW = new SwitchId("00:00:00:00:00:00:00:05");

    @Test
    public void shouldRemoveSelfLoops() {
        AvailableNetwork network = new AvailableNetwork();
        addLink(network, FIRST_SW, FIRST_SW, 7, 60, 10, 3);

        network.removeSelfLoops();
        assertThat(network.switches.values(), Matchers.hasSize(1));

        Node loopSwitch = network.getSwitchNode(FIRST_SW);
        assertThat(loopSwitch.getOutboundEdges(), Matchers.empty());
    }

    @Test
    public void shouldNotAllowDuplicates() {
        AvailableNetwork network = new AvailableNetwork();
        addLink(network, FIRST_SW, SECOND_SW, 7, 60, 10, 3);
        addLink(network, FIRST_SW, SECOND_SW, 7, 60, 20, 5);

        assertThat(network.getSwitchNode(FIRST_SW).getOutboundEdges(), Matchers.hasSize(1));
    }

    @Test
    public void shouldSetEqualCostForPairedLinks() {
        AvailableNetwork network = new AvailableNetwork();
        addLink(network, FIRST_SW, SECOND_SW, 7, 60, 10, 3);
        addLink(network, SECOND_SW, FIRST_SW, 60, 7, 20, 3);

        network.reduceByCost();

        Node srcSwitch = network.getSwitchNode(FIRST_SW);
        Node dstSwitch = network.getSwitchNode(SECOND_SW);

        Set<Edge> outgoingLinks = srcSwitch.getOutboundEdges();
        assertThat(outgoingLinks, Matchers.hasSize(1));
        Edge outgoingIsl = outgoingLinks.iterator().next();
        assertEquals(outgoingIsl.getDestSwitch(), dstSwitch.getSwitchId());
        assertEquals(10, outgoingIsl.getCost());
    }

    @Test
    public void shouldExistWaysToAllSwitches() throws SwitchNotFoundException {
        AvailableNetwork network = buildTestNetwork();
        for (SwitchId sw : getDestSwitches()) {
            assertFalse(
                    String.format("Way to %s is not found", sw),
                    computePath(network, sw).getLeft().isEmpty());
        }
    }

    @Test
    public void filterConnectionIsl() throws SwitchNotFoundException {
        AvailableNetwork network = buildTestNetwork();
        Isl isl = buildLink(FIRST_SW, SECOND_SW, 1, 1, 1, 0);

        network.excludeIsls(singletonList(isl));

        for (SwitchId sw : getDestSwitches()) {
            assertTrue(
                    String.format("Way to %s is found, but shouldn't exist", sw),
                    computePath(network, sw).getLeft().isEmpty());
        }
    }

    @Test
    public void filterConnectionSwitch() throws SwitchNotFoundException {
        AvailableNetwork network = buildTestNetwork();
        Isl isl = buildLink(SECOND_SW, THIRD_SW, 2, 2, 1, 0);

        network.excludeSwitches(singletonList(isl));

        for (SwitchId sw : getDestSwitches()) {
            assertTrue(
                    String.format("Way to %s is found, but shouldn't exist", sw),
                    computePath(network, sw).getLeft().isEmpty());
        }
    }

    @Test
    public void shouldExcludeIsl() throws SwitchNotFoundException {
        AvailableNetwork network = buildTestNetwork();
        Isl isl = buildLink(SECOND_SW, THIRD_SW, 3, 3, 1, 0);
        network.excludeIsls(singletonList(isl));

        // alternate to THIRD_SW path exist
        assertFalse(computePath(network, THIRD_SW).getLeft().isEmpty());
        assertFalse(computePath(network, LAST_SW).getLeft().isEmpty());
    }

    @Test
    public void shouldExcludeSw() throws SwitchNotFoundException {
        AvailableNetwork network = buildTestNetwork();
        SwitchId exluded = THIRD_SW;
        Isl swIsl = buildLink(exluded, exluded, 1, 1, 1, 0);

        network.excludeSwitches(singletonList(swIsl));

        // links on excluded switch are empty
        assertTrue(network.getSwitchNode(exluded).getOutboundEdges().isEmpty());

        // no path to excluded switch exist
        assertTrue(computePath(network, exluded).getLeft().isEmpty());
        assertFalse(computePath(network, LAST_SW).getLeft().isEmpty());
    }

    private List<SwitchId> getDestSwitches() {
        return IntStream.rangeClosed(2, 5)
                .mapToObj(i -> new SwitchId("00:00:00:00:00:00:00:0" + i))
                .collect(Collectors.toList());
    }

    /**
     * Builds diamond test network.
     *        +---->+3
     *        |      ^
     * 1+---->+2     +---->5
     *        |      |
     *        +----->+4
     */
    private AvailableNetwork buildTestNetwork() {
        AvailableNetwork network = new AvailableNetwork();
        addLink(network, FIRST_SW, SECOND_SW, 1, 1, 1, 0);
        addLink(network, SECOND_SW, THIRD_SW, 2, 2, 1, 0);
        addLink(network, SECOND_SW, FOURTH_SW, 3, 3, 1, 0);
        addLink(network, THIRD_SW, LAST_SW, 4, 4, 1, 0);
        addLink(network, FOURTH_SW, LAST_SW, 5, 5, 1, 0);

        // reverse
        addLink(network, LAST_SW, THIRD_SW, 10, 10, 1, 0);
        return network;
    }

    private Pair<List<Edge>, List<Edge>> computePath(AvailableNetwork network, SwitchId dstDpid)
            throws SwitchNotFoundException {
        return new BestCostAndShortestPathFinder(ALLOWED_DEPTH, DEFAULT_COST)
                .findPathInNetwork(network, FIRST_SW, dstDpid);
    }
}
