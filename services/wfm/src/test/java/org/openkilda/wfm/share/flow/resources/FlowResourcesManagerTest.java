/* Copyright 2017 Telstra Open Source
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

package org.openkilda.wfm.share.flow.resources;

public class FlowResourcesManagerTest {
    /*
    private final FlowResourcesManager resourcesManager = new FlowResourcesManager(new ResourceCache());

    private final FlowDto firstFlow = new FlowDto("first-flow", 1, false, "first-flow",
            new SwitchId("ff:01"), 11, 100,
            new SwitchId("ff:03"), 11, 200);
    private final FlowDto secondFlow = new FlowDto("second-flow", 1, false, "second-flow",
            new SwitchId("ff:05"), 12, 100,
            new SwitchId("ff:03"), 12, 200);
    private final FlowDto thirdFlow = new FlowDto("third-flow", 0, true, "third-flow",
            new SwitchId("ff:03"), 21, 100,
            new SwitchId("ff:03"), 22, 200);
    private final FlowDto fourthFlow = new FlowDto("fourth-flow", 0, true, "fourth-flow",
            new SwitchId("ff:04"), 21, 100,
            new SwitchId("ff:05"), 22, 200);

    @Before
    public void before() {
        resourcesManager.clear();
    }

    @Test
    public void shouldAllocateForFlow() {
        UnidirectionalFlow flow = FlowMapper.INSTANCE.map(firstFlow);
        FlowPair newFlow = resourcesManager.allocateFlow(FlowPair.buildPair(flow));

        UnidirectionalFlow forward = newFlow.getForward();
        assertEquals(1 | Cookie.FORWARD_FLOW_COOKIE_MASK, forward.getCookie());
        assertEquals(2, forward.getTransitVlan());
        assertEquals(Integer.valueOf(MIN_FLOW_METER_ID), forward.getMeterId());

        UnidirectionalFlow reverse = newFlow.getReverse();
        assertEquals(1 | Cookie.REVERSE_FLOW_COOKIE_MASK, reverse.getCookie());
        assertEquals(3, reverse.getTransitVlan());
        assertEquals(Integer.valueOf(MIN_FLOW_METER_ID), reverse.getMeterId());
    }

    @Test
    public void shouldNotImmediatelyReuseResources() {
        UnidirectionalFlow flow = FlowMapper.INSTANCE.map(firstFlow);
        FlowPair flowPairToDealloc = resourcesManager.allocateFlow(FlowPair.buildPair(flow));
        resourcesManager.deallocateFlow(flowPairToDealloc);

        FlowPair lastFlow = resourcesManager.allocateFlow(FlowPair.buildPair(flow));

        UnidirectionalFlow forward = lastFlow.getForward();
        assertEquals(2 | Cookie.FORWARD_FLOW_COOKIE_MASK, forward.getCookie());
        assertEquals(4, forward.getTransitVlan());
        assertEquals(Integer.valueOf(MIN_FLOW_METER_ID + 1), forward.getMeterId());

        UnidirectionalFlow reverse = lastFlow.getReverse();
        assertEquals(2 | Cookie.REVERSE_FLOW_COOKIE_MASK, reverse.getCookie());
        assertEquals(5, reverse.getTransitVlan());
        assertEquals(Integer.valueOf(MIN_FLOW_METER_ID + 1), reverse.getMeterId());
    }

    @Test
    public void shouldAllocateForNoBandwidthFlow() {
        UnidirectionalFlow flow = FlowMapper.INSTANCE.map(fourthFlow);
        FlowPair newFlow = resourcesManager.allocateFlow(FlowPair.buildPair(flow));

        UnidirectionalFlow forward = newFlow.getForward();
        assertEquals(1 | Cookie.FORWARD_FLOW_COOKIE_MASK, forward.getCookie());
        assertEquals(2, forward.getTransitVlan());
        assertNull(forward.getMeterId());

        UnidirectionalFlow reverse = newFlow.getReverse();
        assertEquals(1 | Cookie.REVERSE_FLOW_COOKIE_MASK, reverse.getCookie());
        assertEquals(3, reverse.getTransitVlan());
        assertNull(reverse.getMeterId());
    }

    @Test
    public void shouldNotConsumeVlansForSingleSwitchFlows() {
        /*
         * This is to validate that single switch flows don't consume transit vlans.
         * /

        // for forward and reverse flows 2 t-vlans are allocated, so just try max / 2 + 1 attempts
        final int attemps = (ResourceCache.MAX_VLAN_ID - ResourceCache.MIN_VLAN_ID) / 2 + 1;

        for (int i = 0; i < attemps; i++) {
            thirdFlow.setFlowId(format("third-flow-%d", i));

            UnidirectionalFlow flow3 = FlowMapper.INSTANCE.map(thirdFlow);
            resourcesManager.allocateFlow(FlowPair.buildPair(flow3));
        }
    }

    @Test
    public void shouldNotConsumeMetersForUnmeteredFlows() {
        // for forward and reverse flows 2 meters are allocated, so just try max / 2 + 1 attempts
        final int attemps = (MAX_FLOW_METER_ID - MIN_FLOW_METER_ID) / 2 + 1;

        for (int i = 0; i < attemps; i++) {
            fourthFlow.setFlowId(format("fourth-flow-%d", i));

            UnidirectionalFlow flow4 = FlowMapper.INSTANCE.map(fourthFlow);
            resourcesManager.allocateFlow(FlowPair.buildPair(flow4));
        }
    }
    */
}
