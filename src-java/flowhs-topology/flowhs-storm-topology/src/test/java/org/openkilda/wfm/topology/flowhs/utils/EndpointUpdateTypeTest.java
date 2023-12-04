/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.openkilda.wfm.topology.flowhs.utils.EndpointUpdateType.ALL;
import static org.openkilda.wfm.topology.flowhs.utils.EndpointUpdateType.DESTINATION;
import static org.openkilda.wfm.topology.flowhs.utils.EndpointUpdateType.NONE;
import static org.openkilda.wfm.topology.flowhs.utils.EndpointUpdateType.SHARED;
import static org.openkilda.wfm.topology.flowhs.utils.EndpointUpdateType.SOURCE;
import static org.openkilda.wfm.topology.flowhs.utils.EndpointUpdateType.SUBFLOWS;

import org.openkilda.messaging.command.haflow.HaFlowRequest;
import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.HaFlow;
import org.openkilda.wfm.HaFlowHelper;
import org.openkilda.wfm.topology.flowhs.mapper.HaFlowMapper;

import org.junit.jupiter.api.Test;

class EndpointUpdateTypeTest {

    @Test
    void whenNoDifference_updateNone() {
        HaFlow original = HaFlowHelper.createHaFlow();
        HaFlowRequest target = HaFlowMapper.INSTANCE.toHaFlowRequest(
                original, original.getDiverseGroupId(), HaFlowRequest.Type.UPDATE);

        assertEquals(NONE, EndpointUpdateType.determineUpdateType(original, target));
    }

    @Test
    void whenOnlySharedChanged_updateShared() {
        HaFlow original = HaFlowHelper.createHaFlow();
        HaFlowRequest target = HaFlowMapper.INSTANCE.toHaFlowRequest(
                original, original.getDiverseGroupId(), HaFlowRequest.Type.UPDATE);
        target.setSharedEndpoint(FlowEndpoint.builder()
                        .innerVlanId(HaFlowHelper.SHARED_INNER_VLAN + 1)
                        .outerVlanId(HaFlowHelper.SHARED_OUTER_VLAN)
                        .portNumber(HaFlowHelper.SHARED_PORT)
                        .switchId(HaFlowHelper.SHARED_SWITCH.getSwitchId())
                .build());

        assertEquals(SHARED, EndpointUpdateType.determineUpdateType(original, target));

        target.setSharedEndpoint(FlowEndpoint.builder()
                .innerVlanId(HaFlowHelper.SHARED_INNER_VLAN)
                .outerVlanId(HaFlowHelper.SHARED_OUTER_VLAN)
                .portNumber(HaFlowHelper.SHARED_PORT + 1)
                .switchId(HaFlowHelper.SHARED_SWITCH.getSwitchId())
                .build());

        assertEquals(SHARED, EndpointUpdateType.determineUpdateType(original, target));
    }

    @Test
    void whenOnlySubflowEndpointChanged_updateSubflows() {
        HaFlow original = HaFlowHelper.createHaFlow();
        HaFlowRequest target = HaFlowMapper.INSTANCE.toHaFlowRequest(
                original, original.getDiverseGroupId(), HaFlowRequest.Type.UPDATE);

        FlowEndpoint originalEndpointA = target.getSubFlows().get(0).getEndpoint();
        target.getSubFlows().get(0).setEndpoint(FlowEndpoint.builder()
                .innerVlanId(originalEndpointA.getInnerVlanId() + 1)
                .outerVlanId(originalEndpointA.getOuterVlanId())
                .portNumber(originalEndpointA.getPortNumber())
                .switchId(originalEndpointA.getSwitchId())
                .build());

        assertEquals(SUBFLOWS, EndpointUpdateType.determineUpdateType(original, target));

        target.getSubFlows().get(0).setEndpoint(FlowEndpoint.builder()
                .innerVlanId(originalEndpointA.getInnerVlanId())
                .outerVlanId(originalEndpointA.getOuterVlanId())
                .portNumber(originalEndpointA.getPortNumber() + 1)
                .switchId(originalEndpointA.getSwitchId())
                .build());

        assertEquals(SUBFLOWS, EndpointUpdateType.determineUpdateType(original, target));

        target.getSubFlows().get(0).setEndpoint(FlowEndpoint.builder()
                .innerVlanId(originalEndpointA.getInnerVlanId())
                .outerVlanId(originalEndpointA.getOuterVlanId())
                .portNumber(originalEndpointA.getPortNumber())
                .switchId(originalEndpointA.getSwitchId())
                .build());
        FlowEndpoint originalEndpointB = target.getSubFlows().get(1).getEndpoint();
        target.getSubFlows().get(1).setEndpoint(FlowEndpoint.builder()
                .innerVlanId(originalEndpointB.getInnerVlanId())
                .outerVlanId(originalEndpointB.getOuterVlanId())
                .portNumber(originalEndpointB.getPortNumber() + 1)
                .switchId(originalEndpointB.getSwitchId())
                .build());

        assertEquals(SUBFLOWS, EndpointUpdateType.determineUpdateType(original, target));
    }

    @Test
    void whenSharedAndSubflowEndpointsChanged_updateAll() {
        HaFlow original = HaFlowHelper.createHaFlow();
        HaFlowRequest target = HaFlowMapper.INSTANCE.toHaFlowRequest(
                original, original.getDiverseGroupId(), HaFlowRequest.Type.UPDATE);

        FlowEndpoint originalEndpointA = target.getSubFlows().get(0).getEndpoint();
        target.getSubFlows().get(0).setEndpoint(FlowEndpoint.builder()
                .innerVlanId(originalEndpointA.getInnerVlanId() + 1)
                .outerVlanId(originalEndpointA.getOuterVlanId())
                .portNumber(originalEndpointA.getPortNumber())
                .switchId(originalEndpointA.getSwitchId())
                .build());

        target.setSharedEndpoint(FlowEndpoint.builder()
                .innerVlanId(HaFlowHelper.SHARED_INNER_VLAN)
                .outerVlanId(HaFlowHelper.SHARED_OUTER_VLAN)
                .portNumber(HaFlowHelper.SHARED_PORT + 1)
                .switchId(HaFlowHelper.SHARED_SWITCH.getSwitchId())
                .build());

        assertEquals(ALL, EndpointUpdateType.determineUpdateType(original, target));
    }

    @Test
    void whenNotPartial_updateNone() {
        HaFlow original = HaFlowHelper.createHaFlow();
        HaFlowRequest target = HaFlowMapper.INSTANCE.toHaFlowRequest(
                original, original.getDiverseGroupId(), HaFlowRequest.Type.UPDATE);
        target.setAllocateProtectedPath(!target.isAllocateProtectedPath());

        assertEquals(NONE, EndpointUpdateType.determineUpdateType(original, target));
    }

    @Test
    void isPartialUpdateTest() {
        assertFalse(NONE.isPartialUpdate());

        assertTrue(ALL.isPartialUpdate());
        assertTrue(SUBFLOWS.isPartialUpdate());
        assertTrue(SOURCE.isPartialUpdate());
        assertTrue(DESTINATION.isPartialUpdate());
        assertTrue(SHARED.isPartialUpdate());
    }
}
