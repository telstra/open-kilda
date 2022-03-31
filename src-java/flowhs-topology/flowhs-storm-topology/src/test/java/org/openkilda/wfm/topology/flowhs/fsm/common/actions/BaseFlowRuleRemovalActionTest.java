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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import org.openkilda.wfm.share.model.SpeakerRequestBuildContext;
import org.openkilda.wfm.topology.flowhs.fsm.common.FlowProcessingFsm;
import org.openkilda.wfm.topology.flowhs.model.DetectConnectedDevices;
import org.openkilda.wfm.topology.flowhs.model.RequestedFlow;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.squirrelframework.foundation.fsm.StateMachine;

import java.util.Optional;

@RunWith(MockitoJUnitRunner.class)
public class BaseFlowRuleRemovalActionTest extends InMemoryGraphBasedTest {

    public static final String FLOW_ID = "flow1";
    public static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    public static final SwitchId SWITCH_ID_2 = new SwitchId(2);
    public static final int PORT_1 = 1;
    public static final int PORT_2 = 2;
    public static final int PORT_3 = 3;

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
                .flowId(FLOW_ID)
                .srcSwitch(SWITCH_ID_1)
                .destSwitch(SWITCH_ID_2)
                .srcPort(PORT_1)
                .destPort(PORT_3)
                .detectConnectedDevices(
                        new DetectConnectedDevices(true, true, false, false, false, false, false, false))
                .build();
        RequestedFlow newFlow = RequestedFlow.builder()
                .flowId(FLOW_ID)
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
        assertTrue(testClass.removeSharedLldpRule(FLOW_ID, oldEndpoint, newEndpoint));
        assertTrue(testClass.removeSharedArpRule(FLOW_ID, oldEndpoint, newEndpoint));
    }

    @Test
    public void turnOnArpAndLldpTest() {
        FlowEndpoint oldEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1, 0, 0, false, false);
        FlowEndpoint newEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1, 0, 0, true, true);
        assertFalse(testClass.removeSharedLldpRule(FLOW_ID, oldEndpoint, newEndpoint));
        assertFalse(testClass.removeSharedArpRule(FLOW_ID, oldEndpoint, newEndpoint));
    }

    @Test
    public void doNotChangeArpAndLldpTest() {
        FlowEndpoint oldEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1, 0, 0, false, true);
        FlowEndpoint newEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1, 0, 0, false, true);
        assertFalse(testClass.removeSharedLldpRule(FLOW_ID, oldEndpoint, newEndpoint));
        assertFalse(testClass.removeSharedArpRule(FLOW_ID, oldEndpoint, newEndpoint));
    }

    @Test
    public void changePortForOnArpAndLldpTest() {
        FlowEndpoint oldEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1, 0, 0, true, true);
        FlowEndpoint newEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_2, 0, 0, true, true);
        assertTrue(testClass.removeSharedLldpRule(FLOW_ID, oldEndpoint, newEndpoint));
        assertTrue(testClass.removeSharedArpRule(FLOW_ID, oldEndpoint, newEndpoint));
    }

    @Test
    public void changePortForOffArpAndLldpTest() {
        FlowEndpoint oldEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1, 0, 0, false, false);
        FlowEndpoint newEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_2, 0, 0, false, false);
        assertFalse(testClass.removeSharedLldpRule(FLOW_ID, oldEndpoint, newEndpoint));
        assertFalse(testClass.removeSharedArpRule(FLOW_ID, oldEndpoint, newEndpoint));
    }


    @Test
    public void changeSwitchForOnArpAndLldpTest() {
        FlowEndpoint oldEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1, 0, 0, true, true);
        FlowEndpoint newEndpoint = new FlowEndpoint(SWITCH_ID_2, PORT_1, 0, 0, true, true);
        assertTrue(testClass.removeSharedLldpRule(FLOW_ID, oldEndpoint, newEndpoint));
        assertTrue(testClass.removeSharedArpRule(FLOW_ID, oldEndpoint, newEndpoint));
    }


    @Test
    public void changeSwitchForOffArpAndLldpTest() {
        FlowEndpoint oldEndpoint = new FlowEndpoint(SWITCH_ID_1, PORT_1, 0, 0, false, false);
        FlowEndpoint newEndpoint = new FlowEndpoint(SWITCH_ID_2, PORT_1, 0, 0, false, false);
        assertFalse(testClass.removeSharedLldpRule(FLOW_ID, oldEndpoint, newEndpoint));
        assertFalse(testClass.removeSharedArpRule(FLOW_ID, oldEndpoint, newEndpoint));
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
