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

package org.openkilda.wfm.topology.flow.service;

import static java.lang.String.format;
import static org.junit.Assert.assertEquals;

import org.openkilda.messaging.model.FlowDto;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPair;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.share.cache.ResourceCache;
import org.openkilda.wfm.share.mappers.FlowMapper;

import org.junit.Before;
import org.junit.Test;

public class FlowResourcesManagerTest {
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
        Flow flow = FlowMapper.INSTANCE.map(firstFlow);
        FlowPair newFlow = resourcesManager.allocateFlow(FlowPair.builder().forward(flow).reverse(flow).build());

        Flow forward = newFlow.getForward();
        assertEquals(1 | Flow.FORWARD_FLOW_COOKIE_MASK, forward.getCookie());
        assertEquals(2, forward.getTransitVlan());
        assertEquals(11, forward.getMeterId());

        Flow reverse = newFlow.getReverse();
        assertEquals(1 | Flow.REVERSE_FLOW_COOKIE_MASK, reverse.getCookie());
        assertEquals(3, reverse.getTransitVlan());
        assertEquals(12, reverse.getMeterId());
    }

    @Test
    public void shouldNotImmediatelyReuseResources() {
        Flow flow = FlowMapper.INSTANCE.map(firstFlow);
        FlowPair flowPairToDealloc = resourcesManager.allocateFlow(
                FlowPair.builder().forward(flow).reverse(flow).build());
        resourcesManager.deallocateFlow(flowPairToDealloc);

        FlowPair lastFlow = resourcesManager.allocateFlow(FlowPair.builder().forward(flow).reverse(flow).build());

        Flow forward = lastFlow.getForward();
        assertEquals(2 | Flow.FORWARD_FLOW_COOKIE_MASK, forward.getCookie());
        assertEquals(4, forward.getTransitVlan());
        assertEquals(13, forward.getMeterId());

        Flow reverse = lastFlow.getReverse();
        assertEquals(2 | Flow.REVERSE_FLOW_COOKIE_MASK, reverse.getCookie());
        assertEquals(5, reverse.getTransitVlan());
        assertEquals(14, reverse.getMeterId());
    }

    @Test
    public void shouldAllocateForNoBandwidthFlow() {
        Flow flow = FlowMapper.INSTANCE.map(fourthFlow);
        FlowPair newFlow = resourcesManager.allocateFlow(FlowPair.builder().forward(flow).reverse(flow).build());

        Flow forward = newFlow.getForward();
        assertEquals(1 | Flow.FORWARD_FLOW_COOKIE_MASK, forward.getCookie());
        assertEquals(2, forward.getTransitVlan());
        assertEquals(0, forward.getMeterId());

        Flow reverse = newFlow.getReverse();
        assertEquals(1 | Flow.REVERSE_FLOW_COOKIE_MASK, reverse.getCookie());
        assertEquals(3, reverse.getTransitVlan());
        assertEquals(0, reverse.getMeterId());
    }

    @Test
    public void shouldNotConsumeVlansForSingleSwitchFlows() {
        /*
         * This is to validate that single switch flows don't consume transit vlans.
         */

        // for forward and reverse flows 2 t-vlans are allocated, so just try max / 2 + 1 attempts
        final int attemps = (ResourceCache.MAX_VLAN_ID - ResourceCache.MIN_VLAN_ID) / 2 + 1;

        for (int i = 0; i < attemps; i++) {
            thirdFlow.setFlowId(format("third-flow-%d", i));

            Flow flow3 = FlowMapper.INSTANCE.map(thirdFlow);
            resourcesManager.allocateFlow(FlowPair.builder().forward(flow3).reverse(flow3).build());
        }
    }

    @Test
    public void shouldNotConsumeMetersForUnmeteredFlows() {
        // for forward and reverse flows 2 meters are allocated, so just try max / 2 + 1 attempts
        final int attemps = (ResourceCache.MAX_METER_ID - ResourceCache.MIN_METER_ID) / 2 + 1;

        for (int i = 0; i < attemps; i++) {
            fourthFlow.setFlowId(format("fourth-flow-%d", i));

            Flow flow4 = FlowMapper.INSTANCE.map(fourthFlow);
            resourcesManager.allocateFlow(FlowPair.builder().forward(flow4).reverse(flow4).build());
        }
    }
}
