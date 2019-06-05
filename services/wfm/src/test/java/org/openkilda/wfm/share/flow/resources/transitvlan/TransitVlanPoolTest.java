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

package org.openkilda.wfm.share.flow.resources.transitvlan;

import static java.lang.String.format;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.openkilda.model.Flow;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.TransitVlan;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.Neo4jBasedTest;
import org.openkilda.wfm.share.flow.resources.ResourceNotAvailableException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TransitVlanPoolTest extends Neo4jBasedTest {

    private TransitVlanPool transitVlanPool;

    private Switch switch1 = Switch.builder().switchId(new SwitchId("ff:00")).build();
    private Switch switch2 = Switch.builder().switchId(new SwitchId("ff:01")).build();

    @Before
    public void setUp() {
        transitVlanPool = new TransitVlanPool(persistenceManager, 100, 110);

        SwitchRepository switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        switchRepository.createOrUpdate(switch1);
        switchRepository.createOrUpdate(switch2);
    }

    @Test
    public void vlanIdPool() {
        Flow flow = Flow.builder().flowId("flow_1").srcSwitch(switch1).destSwitch(switch2).build();
        int first = transitVlanPool.allocate(flow, new PathId("path_1"), new PathId("opposit_1"))
                .getTransitVlan().getVlan();
        assertEquals(100, first);

        PathId path2 = new PathId("path_2");

        flow.setFlowId("flow_2");
        int second = transitVlanPool.allocate(flow, path2, new PathId("opposit_2")).getTransitVlan().getVlan();
        assertEquals(101, second);

        flow.setFlowId("flow_3");
        int third = transitVlanPool.allocate(flow, new PathId("path_3"), new PathId("opposit_3"))
                .getTransitVlan().getVlan();
        assertEquals(102, third);

        transitVlanPool.deallocate(path2);

        flow.setFlowId("flow_4");
        int fourth = transitVlanPool.allocate(flow, new PathId("path_4"), new PathId("opposit_4"))
                .getTransitVlan().getVlan();
        assertEquals(101, fourth);

        flow.setFlowId("flow_5");
        int fifth = transitVlanPool.allocate(flow, new PathId("path_5"), new PathId("opposit_5"))
                .getTransitVlan().getVlan();
        assertEquals(103, fifth);
    }

    @Test(expected = ResourceNotAvailableException.class)
    public void vlanPoolFullTest() {
        for (int i = 100; i <= 111; i++) {
            Flow flow = Flow.builder().flowId(format("flow_%d", i)).srcSwitch(switch1).destSwitch(switch2).build();
            assertTrue(transitVlanPool.allocate(
                    flow,
                    new PathId(format("path_%d", i)),
                    new PathId(format("opposite_dummy_%d", i))).getTransitVlan().getVlan() > 0);
        }
    }

    @Test
    public void gotSameVlanForOppositePath() {
        Flow flow = Flow.builder().flowId("flow_1").srcSwitch(switch1).destSwitch(switch2).build();

        final PathId forwardPathId = new PathId("forward");
        final PathId reversePathId = new PathId("reverse");
        TransitVlan forward = transitVlanPool.allocate(flow, forwardPathId, reversePathId)
                .getTransitVlan();

        TransitVlan reverse = transitVlanPool.allocate(flow, reversePathId, forwardPathId)
                .getTransitVlan();

        Assert.assertEquals(forward, reverse);
    }
}
