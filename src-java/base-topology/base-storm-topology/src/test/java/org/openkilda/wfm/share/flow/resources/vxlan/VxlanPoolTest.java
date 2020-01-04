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

package org.openkilda.wfm.share.flow.resources.vxlan;

import static java.lang.String.format;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.openkilda.model.Flow;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.Neo4jBasedTest;
import org.openkilda.wfm.share.flow.resources.ResourceNotAvailableException;

import org.junit.Before;
import org.junit.Test;

public class VxlanPoolTest extends Neo4jBasedTest {

    private VxlanPool vxlanPool;

    private Switch switch1 = Switch.builder().switchId(new SwitchId("ff:00")).build();
    private Switch switch2 = Switch.builder().switchId(new SwitchId("ff:01")).build();

    @Before
    public void setUp() {
        vxlanPool = new VxlanPool(persistenceManager, 100, 110);

        SwitchRepository switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
        switchRepository.createOrUpdate(switch1);
        switchRepository.createOrUpdate(switch2);
    }

    @Test
    public void vxlanIdPool() {
        Flow flow = Flow.builder().flowId("flow_1").srcSwitch(switch1).destSwitch(switch2).build();
        int first = vxlanPool.allocate(flow, new PathId("path_1"), new PathId("path_2")).getVxlan().getVni();
        assertEquals(100, first);

        PathId path3 = new PathId("path_3");
        flow.setFlowId("flow_2");
        int second = vxlanPool.allocate(flow, path3, new PathId("path_4")).getVxlan().getVni();
        assertEquals(101, second);

        flow.setFlowId("flow_3");
        int third = vxlanPool.allocate(flow, new PathId("path_5"), new PathId("path_6")).getVxlan().getVni();
        assertEquals(102, third);

        vxlanPool.deallocate(path3);

        flow.setFlowId("flow_4");
        int fourth = vxlanPool.allocate(flow, new PathId("path_7"), new PathId("path_8")).getVxlan().getVni();
        assertEquals(101, fourth);

        flow.setFlowId("flow_5");
        int fifth = vxlanPool.allocate(flow, new PathId("path_9"), new PathId("path_10")).getVxlan().getVni();
        assertEquals(103, fifth);
    }


    @Test(expected = ResourceNotAvailableException.class)
    public void vxlanPoolFullTest() {
        for (int i = 100; i <= 111; i++) {
            Flow flow = Flow.builder().flowId(format("flow_%d", i)).srcSwitch(switch1).destSwitch(switch2).build();
            assertTrue(vxlanPool.allocate(flow, new PathId(format("path_%d", i)),
                    new PathId(format("op_path_%d", i))).getVxlan().getVni() > 0);
        }
    }
}
