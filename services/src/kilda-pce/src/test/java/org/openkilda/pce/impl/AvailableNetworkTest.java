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

import org.hamcrest.Matchers;
import org.junit.Test;

import java.util.List;

public class AvailableNetworkTest extends BasePathComputerTest {
    private static final SwitchId SRC_SWITCH = new SwitchId("00:00:00:22:3d:5a:04:87");
    private static final SwitchId DST_SWITCH = new SwitchId("00:00:b0:d2:f5:00:5a:b8");

    @Test
    public void shouldRemoveSelfLoops() {
        AvailableNetwork network = new AvailableNetwork();
        addLink(network, SRC_SWITCH, SRC_SWITCH,
                7, 60, 10, 3);

        network.removeSelfLoops();
        assertThat(network.switches.values(), Matchers.hasSize(1));

        Switch loopSwitch = network.switches.values().iterator().next();
        assertThat(loopSwitch.getIncomingLinks(), Matchers.empty());
        assertThat(loopSwitch.getOutgoingLinks(), Matchers.empty());
    }

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
    public void shouldSetEqualCostForPairedLinks() {
        AvailableNetwork network = new AvailableNetwork();
        addLink(network, SRC_SWITCH, DST_SWITCH,
                7, 60, 10, 3);
        addLink(network, DST_SWITCH, SRC_SWITCH,
                60, 7, 20, 3);

        network.reduceByCost();

        Switch srcSwitch = network.getSwitch(SRC_SWITCH);
        Switch dstSwitch = network.getSwitch(DST_SWITCH);

        List<Isl> outgoingLinks = srcSwitch.getOutgoingLinks();
        assertThat(outgoingLinks, Matchers.hasSize(1));
        Isl outgoingIsl = outgoingLinks.get(0);
        assertEquals(outgoingIsl.getDestSwitch(), dstSwitch);
        assertEquals(10, outgoingIsl.getCost());

        List<Isl> incomingLinks = srcSwitch.getIncomingLinks();
        assertThat(incomingLinks, Matchers.hasSize(1));
        Isl incomingIsl = incomingLinks.get(0);
        assertEquals(incomingIsl.getSrcSwitch(), dstSwitch);
        assertEquals(20, incomingIsl.getCost());
    }

    @Test
    public void shouldCreateSymmetricOutgoingAndIncomming() {
        AvailableNetwork network = new AvailableNetwork();
        addLink(network, SRC_SWITCH, DST_SWITCH,
                7, 60, 10, 3);

        Switch srcSwitch = network.getSwitch(SRC_SWITCH);
        Switch dstSwitch = network.getSwitch(DST_SWITCH);

        List<Isl> outgoingLinks = srcSwitch.getOutgoingLinks();
        assertThat(outgoingLinks, Matchers.hasSize(1));
        Isl outgoingIsl = outgoingLinks.get(0);
        assertEquals(dstSwitch, outgoingIsl.getDestSwitch());

        List<Isl> incomingLinks = dstSwitch.getIncomingLinks();
        assertThat(incomingLinks, Matchers.hasSize(1));
        Isl incomingIsl = incomingLinks.get(0);
        assertEquals(srcSwitch, incomingIsl.getSrcSwitch());
    }
}
