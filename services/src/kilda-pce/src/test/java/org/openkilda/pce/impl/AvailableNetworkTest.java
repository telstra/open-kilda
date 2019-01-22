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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import org.openkilda.model.Isl;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.pce.model.Edge;
import org.openkilda.pce.model.Node;

import org.hamcrest.Matchers;
import org.junit.Test;

import java.util.Set;

public class AvailableNetworkTest {
    private static final SwitchId SRC_SWITCH = new SwitchId("00:00:00:22:3d:5a:04:87");
    private static final SwitchId DST_SWITCH = new SwitchId("00:00:b0:d2:f5:00:5a:b8");

    @Test
    public void shouldNotAllowDuplicates() {
        AvailableNetwork network = new AvailableNetwork();
        addLink(network, SRC_SWITCH, DST_SWITCH,
                7, 60, 10, 3);
        addLink(network, SRC_SWITCH, DST_SWITCH,
                7, 60, 20, 5);

        assertThat(network.getSwitch(SRC_SWITCH).getOutgoingLinks(), Matchers.hasSize(1));
        assertThat(network.getSwitch(SRC_SWITCH).getIncomingLinks(), Matchers.empty());
        assertThat(network.getSwitch(DST_SWITCH).getOutgoingLinks(), Matchers.empty());
        assertThat(network.getSwitch(DST_SWITCH).getIncomingLinks(), Matchers.hasSize(1));
    }

    @Test
    public void shouldReduceByWeight() {
        int cost = 1;
        AvailableNetwork network = new AvailableNetwork();
        addLink(network, SRC_SWITCH, DST_SWITCH, 1, 1, 20, 5);
        addLink(network, SRC_SWITCH, DST_SWITCH, 5, 5, cost, 3);
        addLink(network, DST_SWITCH, SRC_SWITCH, 1, 1, 20, 5);
        addLink(network, DST_SWITCH, SRC_SWITCH, 5, 5, cost, 3);

        network.reduceByWeight(edge -> (long) edge.getCost());

        Node srcNode = network.getSwitch(SRC_SWITCH);
        Node dstNode = network.getSwitch(DST_SWITCH);
        assertThat(srcNode.getOutgoingLinks(), Matchers.hasSize(1));
        assertThat(srcNode.getIncomingLinks(), Matchers.hasSize(1));
        assertThat(dstNode.getOutgoingLinks(), Matchers.hasSize(1));
        assertThat(dstNode.getIncomingLinks(), Matchers.hasSize(1));
        assertEquals(cost, srcNode.getIncomingLinks().iterator().next().getCost());
        assertEquals(cost, srcNode.getOutgoingLinks().iterator().next().getCost());
    }

    @Test
    public void shouldKeepLinksWithOtherSwitchesAfterReducing() {
        AvailableNetwork network = new AvailableNetwork();
        addLink(network, SRC_SWITCH, new SwitchId("00:00"), 1, 1, 20, 5);
        addLink(network, SRC_SWITCH, DST_SWITCH, 2, 2, 10, 3);
        addLink(network, DST_SWITCH, SRC_SWITCH, 1, 1, 20, 5);
        addLink(network, new SwitchId("00:00"), SRC_SWITCH, 2, 2, 10, 3);

        network.reduceByWeight(edge -> (long) edge.getCost());

        assertThat(network.getSwitch(SRC_SWITCH).getOutgoingLinks(), Matchers.hasSize(2));
        assertThat(network.getSwitch(SRC_SWITCH).getIncomingLinks(), Matchers.hasSize(2));
    }

    @Test
    public void shouldSetEqualCostForPairedLinks() {
        AvailableNetwork network = new AvailableNetwork();
        addLink(network, SRC_SWITCH, DST_SWITCH,
                7, 60, 10, 3);
        addLink(network, DST_SWITCH, SRC_SWITCH,
                60, 7, 20, 3);

        Node srcSwitch = network.getSwitch(SRC_SWITCH);
        Node dstSwitch = network.getSwitch(DST_SWITCH);

        Set<Edge> outgoingLinks = srcSwitch.getOutgoingLinks();
        assertThat(outgoingLinks, Matchers.hasSize(1));
        Edge outgoingIsl = outgoingLinks.iterator().next();
        assertEquals(outgoingIsl.getDestSwitch(), dstSwitch);
        assertEquals(10, outgoingIsl.getCost());

        Set<Edge> incomingLinks = srcSwitch.getIncomingLinks();
        assertThat(incomingLinks, Matchers.hasSize(1));
        Edge incomingIsl = incomingLinks.iterator().next();
        assertEquals(incomingIsl.getSrcSwitch(), dstSwitch);
        assertEquals(20, incomingIsl.getCost());
    }

    @Test
    public void shouldCreateSymmetricOutgoingAndIncomming() {
        AvailableNetwork network = new AvailableNetwork();
        addLink(network, SRC_SWITCH, DST_SWITCH,
                7, 60, 10, 3);

        Node srcSwitch = network.getSwitch(SRC_SWITCH);
        Node dstSwitch = network.getSwitch(DST_SWITCH);

        Set<Edge> outgoingLinks = srcSwitch.getOutgoingLinks();
        assertThat(outgoingLinks, Matchers.hasSize(1));
        Edge outgoingIsl = outgoingLinks.iterator().next();
        assertEquals(dstSwitch, outgoingIsl.getDestSwitch());

        Set<Edge> incomingLinks = dstSwitch.getIncomingLinks();
        assertThat(incomingLinks, Matchers.hasSize(1));
        Edge incomingIsl = incomingLinks.iterator().next();
        assertEquals(srcSwitch, incomingIsl.getSrcSwitch());
    }

    private void addLink(AvailableNetwork network, SwitchId srcDpid, SwitchId dstDpid, int srcPort, int dstPort,
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
}
