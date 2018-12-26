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
import org.openkilda.model.SwitchId;
import org.openkilda.pce.exception.SwitchNotFoundException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.pce.impl.AvailableNetwork;

import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.util.List;

public class BestCostAndShortestPathFinderTest extends PathFinderTest {

    @Test
    public void shouldChooseExpensiveOverTooDeep() throws SwitchNotFoundException, UnroutableFlowException {
        AvailableNetwork network = buildLongAndExpensivePathsNetwork();
        network.reduceByCost();

        Pair<List<Isl>, List<Isl>> pairPath = new BestCostAndShortestPathFinder(2, DEFAULT_COST)
                .findPathInNetwork(network, SWITCH_ID_1, SWITCH_ID_4);
        List<Isl> fpath = pairPath.getLeft();
        assertThat(fpath, Matchers.hasSize(2));
        assertEquals(SWITCH_ID_2, fpath.get(1).getSrcSwitch().getSwitchId());

        List<Isl> rpath = pairPath.getRight();
        assertThat(rpath, Matchers.hasSize(2));
        assertEquals(SWITCH_ID_2, rpath.get(0).getDestSwitch().getSwitchId());
    }

    @Test
    public void shouldChooseExpensiveOverTooDeepForReverseOrder()
            throws SwitchNotFoundException, UnroutableFlowException {
        AvailableNetwork network = buildLongAndExpensivePathsNetwork();
        network.reduceByCost();

        BestCostAndShortestPathFinder forward = new BestCostAndShortestPathFinder(2, DEFAULT_COST);
        Pair<List<Isl>, List<Isl>> pairPath = forward.findPathInNetwork(network, SWITCH_ID_4, SWITCH_ID_1);
        List<Isl> fpath = pairPath.getLeft();
        assertThat(fpath, Matchers.hasSize(2));
        assertEquals(SWITCH_ID_2, fpath.get(1).getSrcSwitch().getSwitchId());

        List<Isl> rpath = pairPath.getRight();
        assertThat(rpath, Matchers.hasSize(2));
        assertEquals(SWITCH_ID_2, rpath.get(0).getDestSwitch().getSwitchId());
    }

    @Test(expected = SwitchNotFoundException.class)
    public void failToFindASwitch() throws SwitchNotFoundException, UnroutableFlowException {
        AvailableNetwork network = buildTestNetwork();
        network.reduceByCost();

        SwitchId srcDpid = new SwitchId("00:00:00:00:00:00:00:ff");

        BestCostAndShortestPathFinder forward = new BestCostAndShortestPathFinder(ALLOWED_DEPTH, DEFAULT_COST);
        forward.findPathInNetwork(network, srcDpid, SWITCH_ID_F);
    }

    @Test(expected = UnroutableFlowException.class)
    public void failToFindReversePath() throws SwitchNotFoundException, UnroutableFlowException {
        AvailableNetwork network = buildNetworkWithoutReversePathAvailable();
        BestCostAndShortestPathFinder forward = new BestCostAndShortestPathFinder(ALLOWED_DEPTH, DEFAULT_COST);
        forward.findPathInNetwork(network, SWITCH_ID_1, SWITCH_ID_3);
    }

    private AvailableNetwork buildNetworkWithoutReversePathAvailable() {
        /*
         *   Topology:
         *
         *   SW1---SW2---SW3
         */
        AvailableNetwork network = new AvailableNetwork();
        addLink(network, SWITCH_ID_1, SWITCH_ID_2, 1, 2, 100, 100);
        addLink(network, SWITCH_ID_2, SWITCH_ID_3, 5, 6, 100, 100);
        return network;
    }

    @Override
    PathFinder getPathFinder() {
        return new BestCostAndShortestPathFinder(ALLOWED_DEPTH, DEFAULT_COST);
    }
}
