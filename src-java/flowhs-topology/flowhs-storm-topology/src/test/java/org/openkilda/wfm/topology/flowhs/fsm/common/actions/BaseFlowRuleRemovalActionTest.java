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

package org.openkilda.wfm.topology.flowhs.fsm.common.actions;

import static com.google.common.collect.Lists.newArrayList;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.openkilda.model.FlowEndpoint;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.wfm.share.flow.resources.FlowResourcesManager;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowProcessingFsm;
import org.openkilda.wfm.topology.flowhs.model.DetectConnectedDevices;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;
import org.openkilda.wfm.topology.flowhs.service.speaker.SpeakerRequestBuildContext;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.squirrelframework.foundation.fsm.StateMachine;

import java.util.Optional;

@RunWith(MockitoJUnitRunner.class)
public class BaseFlowRuleRemovalActionTest extends InMemoryGraphBasedTest {

    public static final String FLOW_ID_1 = "flow1";
    public static final String FLOW_ID_2 = "flow2";
    public static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    public static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    public static final RequestedFlow MULTI_SWITCH_FLOW = RequestedFlow.builder()
            .flowId(FLOW_ID_1).srcSwitch(SWITCH_ID_1).destSwitch(SWITCH_ID_2).build();
    public static final RequestedFlow ONE_SWITCH_FLOW = RequestedFlow.builder()
            .flowId(FLOW_ID_1).srcSwitch(SWITCH_ID_1).destSwitch(SWITCH_ID_1).build();
    public static final int PORT_1 = 1;
    public static final int PORT_2 = 2;
    public static final int PORT_3 = 3;
    public static final int VLAN_1 = 4;
    public static final int VLAN_2 = 5;

    @Mock
    private FlowRepository flowRepository;

    @Mock
    private SwitchPropertiesRepository switchPropertiesRepository;
    TestClass testClass;

    @Before
    public void setup() {
        flowRepository = spy(persistenceManager.getRepositoryFactory().createFlowRepository());
        switchPropertiesRepository = spy(persistenceManager.getRepositoryFactory().createSwitchPropertiesRepository());
        when(repositoryFactory.createFlowRepository()).thenReturn(flowRepository);
        when(repositoryFactory.createSwitchPropertiesRepository()).thenReturn(switchPropertiesRepository);
        testClass = new TestClass(persistenceManager, null);
        when(switchPropertiesRepository.findBySwitchId(any()))
                .thenReturn(Optional.of(SwitchProperties.builder().build()));
    }

    @Test
    public void buildSpeakerContextForRemovalIngressAndSharedTest() {
        RequestedFlow oldFLow = RequestedFlow.builder()
                .flowId(FLOW_ID_1)
                .srcSwitch(SWITCH_ID_1)
                .destSwitch(SWITCH_ID_2)
                .srcPort(PORT_1)
                .destPort(PORT_3)
                .detectConnectedDevices(
                        new DetectConnectedDevices(true, true, false, false, false, false, false, false))
                .build();
        RequestedFlow newFlow = RequestedFlow.builder()
                .flowId(FLOW_ID_1)
                .srcSwitch(SWITCH_ID_1)
                .destSwitch(SWITCH_ID_2)
                .srcPort(PORT_2)
                .destPort(PORT_1)
                .detectConnectedDevices(
                        new DetectConnectedDevices(false, true, true, false, false, false, false, false))
                .build();

        SpeakerRequestBuildContext context = testClass.buildSpeakerContextForRemovalIngressAndShared(
                oldFLow, newFlow, false);
        assertTrue(context.getForward().isRemoveCustomerPortLldpRule());
        assertTrue(context.getForward().isRemoveCustomerPortArpRule());
        assertFalse(context.getReverse().isRemoveCustomerPortLldpRule());
        assertFalse(context.getReverse().isRemoveCustomerPortArpRule());
    }

    @Test
    public void turnOffArpAndLldpTest() {
        FlowEndpoint oldEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1, 0, 0, true, true);
        FlowEndpoint newEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1, 0, 0, false, false);
        FlowEndpoint oppositeNewEndpoint = new FlowEndpoint(SWITCH_ID_2, PORT_3, 0, 0, false, false);
        assertTrue(testClass.removeSharedLldpRule(FLOW_ID_1, oldEndpoint, newEndpoint, oppositeNewEndpoint));
        assertTrue(testClass.removeSharedArpRule(FLOW_ID_1, oldEndpoint, newEndpoint, oppositeNewEndpoint));
    }

    @Test
    public void turnOnArpAndLldpTest() {
        FlowEndpoint oldEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1, 0, 0, false, false);
        FlowEndpoint newEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1, 0, 0, true, true);
        FlowEndpoint oppositeNewEndpoint = new FlowEndpoint(SWITCH_ID_2, PORT_3, 0, 0, false, false);
        assertFalse(testClass.removeSharedLldpRule(FLOW_ID_1, oldEndpoint, newEndpoint, oppositeNewEndpoint));
        assertFalse(testClass.removeSharedArpRule(FLOW_ID_1, oldEndpoint, newEndpoint, oppositeNewEndpoint));
    }

    @Test
    public void doNotChangeArpAndLldpTest() {
        FlowEndpoint oldEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1, 0, 0, false, true);
        FlowEndpoint newEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1, 0, 0, false, true);
        FlowEndpoint oppositeNewEndpoint = new FlowEndpoint(SWITCH_ID_2, PORT_3, 0, 0, false, false);
        assertFalse(testClass.removeSharedLldpRule(FLOW_ID_1, oldEndpoint, newEndpoint, oppositeNewEndpoint));
        assertFalse(testClass.removeSharedArpRule(FLOW_ID_1, oldEndpoint, newEndpoint, oppositeNewEndpoint));
    }

    @Test
    public void changePortForOnArpAndLldpTest() {
        FlowEndpoint oldEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1, 0, 0, true, true);
        FlowEndpoint newEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_2, 0, 0, true, true);
        FlowEndpoint oppositeNewEndpoint = new FlowEndpoint(SWITCH_ID_2, PORT_3, 0, 0, false, false);
        assertTrue(testClass.removeSharedLldpRule(FLOW_ID_1, oldEndpoint, newEndpoint, oppositeNewEndpoint));
        assertTrue(testClass.removeSharedArpRule(FLOW_ID_1, oldEndpoint, newEndpoint, oppositeNewEndpoint));
    }

    @Test
    public void changePortForOffArpAndLldpTest() {
        FlowEndpoint oldEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1, 0, 0, false, false);
        FlowEndpoint newEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_2, 0, 0, false, false);
        FlowEndpoint oppositeNewEndpoint = new FlowEndpoint(SWITCH_ID_2, PORT_3, 0, 0, false, false);
        assertFalse(testClass.removeSharedLldpRule(FLOW_ID_1, oldEndpoint, newEndpoint, oppositeNewEndpoint));
        assertFalse(testClass.removeSharedArpRule(FLOW_ID_1, oldEndpoint, newEndpoint, oppositeNewEndpoint));
    }

    @Test
    public void changeSwitchForOnArpAndLldpTest() {
        FlowEndpoint oldEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1, 0, 0, true, true);
        FlowEndpoint newEndpoint = new FlowEndpoint(SWITCH_ID_2, PORT_1, 0, 0, true, true);
        FlowEndpoint oppositeNewEndpoint = new FlowEndpoint(SWITCH_ID_2, PORT_3, 0, 0, false, false);
        assertTrue(testClass.removeSharedLldpRule(FLOW_ID_1, oldEndpoint, newEndpoint, oppositeNewEndpoint));
        assertTrue(testClass.removeSharedArpRule(FLOW_ID_1, oldEndpoint, newEndpoint, oppositeNewEndpoint));
    }

    @Test
    public void changeSwitchForOffArpAndLldpTest() {
        FlowEndpoint oldEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1, 0, 0, false, false);
        FlowEndpoint newEndpoint = new FlowEndpoint(SWITCH_ID_2, PORT_1, 0, 0, false, false);
        FlowEndpoint oppositeNewEndpoint = new FlowEndpoint(SWITCH_ID_2, PORT_3, 0, 0, false, false);
        assertFalse(testClass.removeSharedLldpRule(FLOW_ID_1, oldEndpoint, newEndpoint, oppositeNewEndpoint));
        assertFalse(testClass.removeSharedArpRule(FLOW_ID_1, oldEndpoint, newEndpoint, oppositeNewEndpoint));
    }

    @Test
    public void swapEndpointOnArpAndLldpTest() {
        FlowEndpoint oldEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1, 0, 0, true, true);
        FlowEndpoint newEndpoint = new FlowEndpoint(SWITCH_ID_2, PORT_2, 0, 0, true, true);
        FlowEndpoint oppositeNewEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1, 0, 0, true, true);
        assertFalse(testClass.removeSharedLldpRule(FLOW_ID_1, oldEndpoint, newEndpoint, oppositeNewEndpoint));
        assertFalse(testClass.removeSharedArpRule(FLOW_ID_1, oldEndpoint, newEndpoint, oppositeNewEndpoint));
    }

    @Test
    public void swapEndpointTurnOffArpAndLldpTest() {
        FlowEndpoint oldEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1, 0, 0, true, true);
        FlowEndpoint newEndpoint = new FlowEndpoint(SWITCH_ID_2, PORT_2, 0, 0, true, true);
        FlowEndpoint oppositeNewEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1, 0, 0, false, false);
        assertTrue(testClass.removeSharedLldpRule(FLOW_ID_1, oldEndpoint, newEndpoint, oppositeNewEndpoint));
        assertTrue(testClass.removeSharedArpRule(FLOW_ID_1, oldEndpoint, newEndpoint, oppositeNewEndpoint));
    }

    @Test
    public void sameEndpointRemoveSharedServer42InputRuleTest() {
        FlowEndpoint oldEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1);
        FlowEndpoint newEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1);
        FlowEndpoint oppositeNewEndpoint = new FlowEndpoint(SWITCH_ID_2, PORT_3);
        assertFalse(testClass.removeSharedServer42InputRule(
                oldEndpoint, newEndpoint, oppositeNewEndpoint, true, false));
        assertFalse(testClass.removeSharedServer42InputRule(
                oldEndpoint, newEndpoint, oppositeNewEndpoint, false, false));
    }

    @Test
    public void changeSwitchRemoveSharedServer42InputRuleTest() {
        FlowEndpoint oldEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1);
        FlowEndpoint newEndpoint = new FlowEndpoint(SWITCH_ID_2, PORT_1);
        FlowEndpoint oppositeNewEndpoint = new FlowEndpoint(SWITCH_ID_2, PORT_3);
        assertTrue(testClass.removeSharedServer42InputRule(oldEndpoint, newEndpoint, oppositeNewEndpoint, true, false));
        assertTrue(testClass.removeSharedServer42InputRule(oldEndpoint, newEndpoint, oppositeNewEndpoint, true, true));
        assertFalse(testClass.removeSharedServer42InputRule(
                oldEndpoint, newEndpoint, oppositeNewEndpoint, false, false));
    }

    @Test
    public void changePortRemoveSharedServer42InputRuleTest() {
        FlowEndpoint oldEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1);
        FlowEndpoint newEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_2);
        FlowEndpoint oppositeNewEndpoint = new FlowEndpoint(SWITCH_ID_2, PORT_3);
        assertTrue(testClass.removeSharedServer42InputRule(oldEndpoint, newEndpoint, oppositeNewEndpoint, true, false));
        assertTrue(testClass.removeSharedServer42InputRule(oldEndpoint, newEndpoint, oppositeNewEndpoint, true, true));
        assertFalse(testClass.removeSharedServer42InputRule(
                oldEndpoint, newEndpoint, oppositeNewEndpoint, false, false));
    }

    @Test
    public void swapEndpointMultiSwitchRemoveSharedServer42InputRuleTest() {
        FlowEndpoint oldEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1);
        FlowEndpoint newEndpoint = new FlowEndpoint(SWITCH_ID_2, PORT_2);
        FlowEndpoint oppositeNewEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1);
        assertFalse(testClass.removeSharedServer42InputRule(
                oldEndpoint, newEndpoint, oppositeNewEndpoint, true, false));
        assertFalse(testClass.removeSharedServer42InputRule(
                oldEndpoint, newEndpoint, oppositeNewEndpoint, false, false));
    }

    @Test
    public void swapEndpointSingleSwitchRemoveSharedServer42InputRuleTest() {
        FlowEndpoint oldEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1);
        FlowEndpoint newEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_2);
        FlowEndpoint oppositeNewEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1);
        assertTrue(testClass.removeSharedServer42InputRule(oldEndpoint, newEndpoint, oppositeNewEndpoint, true, true));
        assertFalse(testClass.removeSharedServer42InputRule(
                oldEndpoint, newEndpoint, oppositeNewEndpoint, false, true));
    }

    @Test
    public void hasOtherFlowRemoveSharedServer42InputRuleTest() {
        when(flowRepository.findFlowIdsForMultiSwitchFlowsByEndpointWithMultiTableSupport(eq(SWITCH_ID_1), eq(PORT_1)))
                .thenReturn(newArrayList(FLOW_ID_2));
        FlowEndpoint oldEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1);
        FlowEndpoint sameEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1);
        FlowEndpoint changedSwitchEndpoint = new FlowEndpoint(SWITCH_ID_2, PORT_1);
        FlowEndpoint changedPortEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_2);
        FlowEndpoint changedSwitchOppositeEndpoint = new FlowEndpoint(SWITCH_ID_2, PORT_1);
        FlowEndpoint changedPortOppositeEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_3);

        assertFalse(testClass.removeSharedServer42InputRule(
                oldEndpoint, sameEndpoint, changedSwitchOppositeEndpoint, true, false));
        assertFalse(testClass.removeSharedServer42InputRule(
                oldEndpoint, sameEndpoint, changedPortOppositeEndpoint, true, true));
        assertFalse(testClass.removeSharedServer42InputRule(
                oldEndpoint, sameEndpoint, changedSwitchOppositeEndpoint, false, false));
        assertFalse(testClass.removeSharedServer42InputRule(
                oldEndpoint, changedSwitchEndpoint, changedSwitchOppositeEndpoint, true, false));
        assertFalse(testClass.removeSharedServer42InputRule(
                oldEndpoint, changedSwitchEndpoint, changedPortOppositeEndpoint, true, true));
        assertFalse(testClass.removeSharedServer42InputRule(
                oldEndpoint, changedSwitchEndpoint, changedSwitchOppositeEndpoint, false, false));
        assertFalse(testClass.removeSharedServer42InputRule(
                oldEndpoint, changedPortEndpoint, changedSwitchOppositeEndpoint, true, false));
        assertFalse(testClass.removeSharedServer42InputRule(
                oldEndpoint, changedPortEndpoint, changedPortOppositeEndpoint, true, true));
        assertFalse(testClass.removeSharedServer42InputRule(
                oldEndpoint, changedPortEndpoint, changedSwitchOppositeEndpoint, false, false));
        assertFalse(testClass.removeSharedServer42InputRule(
                oldEndpoint, changedPortEndpoint, sameEndpoint, true, false));
    }

    @Test
    public void sameEndpointRemoveServer42OuterVlanMatchSharedRuleTest() {
        FlowEndpoint oldEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1);
        FlowEndpoint newEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1);
        FlowEndpoint changedSwitchOppositeEndpoint = new FlowEndpoint(SWITCH_ID_2, PORT_3);
        FlowEndpoint sameSwitchOppositeEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_3);
        assertFalse(testClass.removeServer42OuterVlanMatchSharedRule(
                ONE_SWITCH_FLOW, oldEndpoint, newEndpoint, changedSwitchOppositeEndpoint, true, false));
        assertFalse(testClass.removeServer42OuterVlanMatchSharedRule(
                ONE_SWITCH_FLOW, oldEndpoint, newEndpoint, changedSwitchOppositeEndpoint, false, false));
        assertFalse(testClass.removeServer42OuterVlanMatchSharedRule(
                MULTI_SWITCH_FLOW, oldEndpoint, newEndpoint, changedSwitchOppositeEndpoint, false, false));
        assertFalse(testClass.removeServer42OuterVlanMatchSharedRule(
                MULTI_SWITCH_FLOW, oldEndpoint, newEndpoint, sameSwitchOppositeEndpoint, false, true));
        assertFalse(testClass.removeServer42OuterVlanMatchSharedRule(
                MULTI_SWITCH_FLOW, oldEndpoint, newEndpoint, changedSwitchOppositeEndpoint, true, false));
        assertTrue(testClass.removeServer42OuterVlanMatchSharedRule(
                MULTI_SWITCH_FLOW, oldEndpoint, newEndpoint, sameSwitchOppositeEndpoint, true, true));
    }

    @Test
    public void changeSwitchRemoveServer42OuterVlanMatchSharedRuleTest() {
        FlowEndpoint oldEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1);
        FlowEndpoint newEndpoint = new FlowEndpoint(SWITCH_ID_2, PORT_1);
        FlowEndpoint changedSwitchOppositeEndpoint = new FlowEndpoint(SWITCH_ID_2, PORT_3);
        FlowEndpoint sameSwitchOppositeEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_3);

        assertFalse(testClass.removeServer42OuterVlanMatchSharedRule(
                ONE_SWITCH_FLOW, oldEndpoint, newEndpoint, changedSwitchOppositeEndpoint, true, false));
        assertFalse(testClass.removeServer42OuterVlanMatchSharedRule(
                ONE_SWITCH_FLOW, oldEndpoint, newEndpoint, changedSwitchOppositeEndpoint, false, false));
        assertFalse(testClass.removeServer42OuterVlanMatchSharedRule(
                MULTI_SWITCH_FLOW, oldEndpoint, newEndpoint, changedSwitchOppositeEndpoint, false, false));
        assertFalse(testClass.removeServer42OuterVlanMatchSharedRule(
                MULTI_SWITCH_FLOW, oldEndpoint, newEndpoint, sameSwitchOppositeEndpoint, false, true));
        assertTrue(testClass.removeServer42OuterVlanMatchSharedRule(
                MULTI_SWITCH_FLOW, oldEndpoint, newEndpoint, changedSwitchOppositeEndpoint, true, false));
        assertTrue(testClass.removeServer42OuterVlanMatchSharedRule(
                MULTI_SWITCH_FLOW, oldEndpoint, newEndpoint, sameSwitchOppositeEndpoint, true, true));
    }

    @Test
    public void changeOuterVlanRemoveServer42OuterVlanMatchSharedRuleTest() {
        FlowEndpoint oldEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1, VLAN_1);
        FlowEndpoint newEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1, VLAN_2);
        FlowEndpoint changedSwitchOppositeEndpoint = new FlowEndpoint(SWITCH_ID_2, PORT_3);
        FlowEndpoint sameSwitchOppositeEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_3);

        assertFalse(testClass.removeServer42OuterVlanMatchSharedRule(
                ONE_SWITCH_FLOW, oldEndpoint, newEndpoint, changedSwitchOppositeEndpoint, true, false));
        assertFalse(testClass.removeServer42OuterVlanMatchSharedRule(
                ONE_SWITCH_FLOW, oldEndpoint, newEndpoint, changedSwitchOppositeEndpoint, false, false));
        assertFalse(testClass.removeServer42OuterVlanMatchSharedRule(
                MULTI_SWITCH_FLOW, oldEndpoint, newEndpoint, changedSwitchOppositeEndpoint, false, false));
        assertFalse(testClass.removeServer42OuterVlanMatchSharedRule(
                MULTI_SWITCH_FLOW, oldEndpoint, newEndpoint, sameSwitchOppositeEndpoint, false, true));
        assertTrue(testClass.removeServer42OuterVlanMatchSharedRule(
                MULTI_SWITCH_FLOW, oldEndpoint, newEndpoint, changedSwitchOppositeEndpoint, true, false));
        assertTrue(testClass.removeServer42OuterVlanMatchSharedRule(
                MULTI_SWITCH_FLOW, oldEndpoint, newEndpoint, sameSwitchOppositeEndpoint, true, true));
    }

    @Test
    public void changePortRemoveServer42OuterVlanMatchSharedRuleTest() {
        FlowEndpoint oldEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1, VLAN_1);
        FlowEndpoint newEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_2, VLAN_1);
        FlowEndpoint changedSwitchOppositeEndpoint = new FlowEndpoint(SWITCH_ID_2, PORT_3);
        FlowEndpoint sameSwitchOppositeEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_3);

        assertFalse(testClass.removeServer42OuterVlanMatchSharedRule(
                ONE_SWITCH_FLOW, oldEndpoint, newEndpoint, changedSwitchOppositeEndpoint, true, false));
        assertFalse(testClass.removeServer42OuterVlanMatchSharedRule(
                ONE_SWITCH_FLOW, oldEndpoint, newEndpoint, changedSwitchOppositeEndpoint, false, false));
        assertFalse(testClass.removeServer42OuterVlanMatchSharedRule(
                MULTI_SWITCH_FLOW, oldEndpoint, newEndpoint, changedSwitchOppositeEndpoint, false, false));
        assertFalse(testClass.removeServer42OuterVlanMatchSharedRule(
                MULTI_SWITCH_FLOW, oldEndpoint, newEndpoint, sameSwitchOppositeEndpoint, false, true));
        assertFalse(testClass.removeServer42OuterVlanMatchSharedRule(
                MULTI_SWITCH_FLOW, oldEndpoint, newEndpoint, changedSwitchOppositeEndpoint, true, false));
        assertTrue(testClass.removeServer42OuterVlanMatchSharedRule(
                MULTI_SWITCH_FLOW, oldEndpoint, newEndpoint, sameSwitchOppositeEndpoint, true, true));
    }

    @Test
    public void swapEndpointsRemoveServer42OuterVlanMatchSharedRuleTest() {
        FlowEndpoint oldEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1, VLAN_1);
        FlowEndpoint newEndpointMultiSwitch = new FlowEndpoint(SWITCH_ID_2, PORT_2, VLAN_2);
        FlowEndpoint newEndpointOneSwitch = new FlowEndpoint(SWITCH_ID_1, PORT_2, VLAN_2);

        assertFalse(testClass.removeServer42OuterVlanMatchSharedRule(
                ONE_SWITCH_FLOW, oldEndpoint, newEndpointOneSwitch, oldEndpoint, true, false));
        assertFalse(testClass.removeServer42OuterVlanMatchSharedRule(
                ONE_SWITCH_FLOW, oldEndpoint, newEndpointOneSwitch, oldEndpoint, false, false));
        assertFalse(testClass.removeServer42OuterVlanMatchSharedRule(
                MULTI_SWITCH_FLOW, oldEndpoint, newEndpointMultiSwitch, oldEndpoint, false, false));
        assertFalse(testClass.removeServer42OuterVlanMatchSharedRule(
                MULTI_SWITCH_FLOW, oldEndpoint, newEndpointOneSwitch, oldEndpoint, false, true));
        assertFalse(testClass.removeServer42OuterVlanMatchSharedRule(
                MULTI_SWITCH_FLOW, oldEndpoint, newEndpointMultiSwitch, oldEndpoint, true, false));
        assertTrue(testClass.removeServer42OuterVlanMatchSharedRule(
                MULTI_SWITCH_FLOW, oldEndpoint, newEndpointOneSwitch, oldEndpoint, true, true));
    }

    @Test
    public void hasOtherFlowRemoveServer42OuterVlanMatchSharedRuleTest() {
        FlowEndpoint oldEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1, VLAN_1);
        FlowEndpoint newEndpoint = new FlowEndpoint(SWITCH_ID_2, PORT_2, VLAN_2);
        FlowEndpoint changedSwitchOppositeEndpoint = new FlowEndpoint(SWITCH_ID_2, PORT_3);
        FlowEndpoint sameSwitchOppositeEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_3);
        BaseFlowRuleRemovalAction mockTestClass = Mockito.mock(BaseFlowRuleRemovalAction.class);
        when(mockTestClass.findServer42OuterVlanMatchSharedRuleUsage(any()))
                .thenReturn(newArrayList(FLOW_ID_2));
        when(mockTestClass.removeServer42OuterVlanMatchSharedRule(
                any(), any(), any(), any(), anyBoolean(), anyBoolean())).thenCallRealMethod();

        assertFalse(mockTestClass.removeServer42OuterVlanMatchSharedRule(
                ONE_SWITCH_FLOW, oldEndpoint, newEndpoint, changedSwitchOppositeEndpoint, true, false));
        assertFalse(mockTestClass.removeServer42OuterVlanMatchSharedRule(
                ONE_SWITCH_FLOW, oldEndpoint, newEndpoint, changedSwitchOppositeEndpoint, false, false));
        assertFalse(mockTestClass.removeServer42OuterVlanMatchSharedRule(
                MULTI_SWITCH_FLOW, oldEndpoint, newEndpoint, changedSwitchOppositeEndpoint, false, false));
        assertFalse(mockTestClass.removeServer42OuterVlanMatchSharedRule(
                MULTI_SWITCH_FLOW, oldEndpoint, newEndpoint, sameSwitchOppositeEndpoint, false, true));
        assertFalse(mockTestClass.removeServer42OuterVlanMatchSharedRule(
                MULTI_SWITCH_FLOW, oldEndpoint, newEndpoint, changedSwitchOppositeEndpoint, true, false));
        assertFalse(mockTestClass.removeServer42OuterVlanMatchSharedRule(
                MULTI_SWITCH_FLOW, oldEndpoint, newEndpoint, sameSwitchOppositeEndpoint, true, true));
    }

    @Test
    public void removeOuterVlanMatchSharedRuleTest() {
        FlowEndpoint oldEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1, VLAN_1);
        FlowEndpoint changeSwitchEndpoint = new FlowEndpoint(SWITCH_ID_2, PORT_1, VLAN_1);
        FlowEndpoint changeVlanEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1, VLAN_2);
        FlowEndpoint changePortEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_2, VLAN_1);
        FlowEndpoint changedSwitchOppositeEndpoint = new FlowEndpoint(SWITCH_ID_2, PORT_1, VLAN_1);
        FlowEndpoint sameSwitchOppositeEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_3, VLAN_1);

        assertFalse(testClass.removeOuterVlanMatchSharedRule(
                FLOW_ID_1, oldEndpoint, oldEndpoint, changedSwitchOppositeEndpoint));
        assertFalse(testClass.removeOuterVlanMatchSharedRule(
                FLOW_ID_1, oldEndpoint, oldEndpoint, sameSwitchOppositeEndpoint));

        assertTrue(testClass.removeOuterVlanMatchSharedRule(
                FLOW_ID_1, oldEndpoint, changeSwitchEndpoint, changedSwitchOppositeEndpoint));
        assertTrue(testClass.removeOuterVlanMatchSharedRule(
                FLOW_ID_1, oldEndpoint, changeVlanEndpoint, changedSwitchOppositeEndpoint));
        assertTrue(testClass.removeOuterVlanMatchSharedRule(
                FLOW_ID_1, oldEndpoint, changePortEndpoint, changedSwitchOppositeEndpoint));

        assertTrue(testClass.removeOuterVlanMatchSharedRule(
                FLOW_ID_1, oldEndpoint, changeSwitchEndpoint, sameSwitchOppositeEndpoint));
        assertTrue(testClass.removeOuterVlanMatchSharedRule(
                FLOW_ID_1, oldEndpoint, changeVlanEndpoint, sameSwitchOppositeEndpoint));
        assertTrue(testClass.removeOuterVlanMatchSharedRule(
                FLOW_ID_1, oldEndpoint, changePortEndpoint, sameSwitchOppositeEndpoint));

        assertFalse(testClass.removeOuterVlanMatchSharedRule(
                FLOW_ID_1, oldEndpoint, changeSwitchEndpoint, oldEndpoint));
        assertFalse(testClass.removeOuterVlanMatchSharedRule(
                FLOW_ID_1, oldEndpoint, changeVlanEndpoint, oldEndpoint));
        assertFalse(testClass.removeOuterVlanMatchSharedRule(
                FLOW_ID_1, oldEndpoint, changePortEndpoint, oldEndpoint));
    }

    private static class TestClass extends BaseFlowRuleRemovalAction {

        protected TestClass(PersistenceManager persistenceManager, FlowResourcesManager resourcesManager) {
            super(persistenceManager, resourcesManager);
        }

        @Override
        protected void perform(Object from, Object to, Object event, Object context, FlowProcessingFsm stateMachine) {
        }

        @Override
        public void execute(Object from, Object to, Object event, Object context, StateMachine stateMachine) {
        }
    }
}
