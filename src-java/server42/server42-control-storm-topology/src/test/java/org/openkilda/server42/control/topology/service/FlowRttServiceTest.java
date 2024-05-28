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

package org.openkilda.server42.control.topology.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.openkilda.persistence.ferma.repositories.FermaModelUtils.buildHaSubFlow;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.Switch;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.HaFlowRepository;
import org.openkilda.persistence.repositories.HaSubFlowRepository;

import com.google.common.collect.Sets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
public class FlowRttServiceTest extends InMemoryGraphBasedTest {

    private static final String SUB_FLOW_ID_A = "test_ha_flow_1-a";
    private static final String SUB_FLOW_ID_B = "test_ha_flow_1-b";
    private static final String FLOW_ID_1 = "test_flow_1";

    private Switch switch1;
    private Switch switch2;
    private Switch switch3;

    private static HaFlowRepository haFlowRepository;
    private static HaSubFlowRepository haSubFlowRepository;
    private static FlowRepository flowRepository;

    @Mock
    private IFlowCarrier carrier;

    private FlowRttService flowRttService;


    @BeforeAll
    public static void setupOnce() {
        haFlowRepository = persistenceManager.getRepositoryFactory().createHaFlowRepository();
        haSubFlowRepository = persistenceManager.getRepositoryFactory().createHaSubFlowRepository();
        flowRepository = persistenceManager.getRepositoryFactory().createFlowRepository();
    }

    @BeforeEach
    public void setUp() {
        flowRttService = new FlowRttService(carrier, persistenceManager);
        switch1 = createTestSwitch(SWITCH_ID_1);
        switch2 = createTestSwitch(SWITCH_ID_2);
        switch3 = createTestSwitch(SWITCH_ID_3);
        createHaFlow();
        createFlow();
    }

    @Test
    public void sendFlowListOnSwitchCommandHaFlow() {
        final Set<String> expectedFlowsOnSwitch = Sets.newHashSet(SUB_FLOW_ID_A, SUB_FLOW_ID_B, FLOW_ID_1);
        flowRttService.sendFlowListOnSwitchCommand(SWITCH_ID_1);
        verify(carrier).sendListOfFlowBySwitchId(SWITCH_ID_1, expectedFlowsOnSwitch);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    void activateFlowMonitoringForSwitchForward() {
        flowRttService.activateFlowMonitoringForSwitch(SWITCH_ID_1);
        verify(carrier).notifyActivateFlowMonitoring(SUB_FLOW_ID_A, SWITCH_ID_1, SWITCH_ID_2,
                PORT_1, VLAN_1, INNER_VLAN_1, true);
        verify(carrier).notifyActivateFlowMonitoring(SUB_FLOW_ID_B, SWITCH_ID_1, SWITCH_ID_3,
                PORT_1, VLAN_1, INNER_VLAN_1, true);
        verify(carrier).notifyActivateFlowMonitoring(FLOW_ID_1, SWITCH_ID_1, SWITCH_ID_2,
                PORT_1, VLAN_1, INNER_VLAN_1, true);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    void activateFlowMonitoringForSwitchReverse() {
        flowRttService.activateFlowMonitoringForSwitch(SWITCH_ID_2);
        verify(carrier).notifyActivateFlowMonitoring(SUB_FLOW_ID_A, SWITCH_ID_2, SWITCH_ID_1,
                PORT_1, VLAN_2, INNER_VLAN_2, false);
        verify(carrier).notifyActivateFlowMonitoring(FLOW_ID_1, SWITCH_ID_2, SWITCH_ID_1,
                PORT_2, VLAN_2, INNER_VLAN_2, false);
        verifyNoMoreInteractions(carrier);
    }

    @Test
    void activateFlowMonitoringNoRtt() {
        flowRttService.activateFlowMonitoring(SUB_FLOW_ID_A, SWITCH_ID_3, SWITCH_ID_2,
                PORT_1, 0, 0, true);
        verifyNoMoreInteractions(carrier);
    }

    private void createHaFlow() {
        final HaFlow haFlow = HaFlow.builder()
                .haFlowId(HA_FLOW_ID_1)
                .sharedSwitch(switch1)
                .sharedPort(PORT_1)
                .sharedOuterVlan(VLAN_1)
                .sharedInnerVlan(INNER_VLAN_1)
                .encapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .build();

        final Set<HaSubFlow> haSubFlows = new HashSet<>();

        HaSubFlow subFlowA = buildHaSubFlow(SUB_FLOW_ID_A, switch2, PORT_1, VLAN_2, INNER_VLAN_2, DESCRIPTION_1);
        haSubFlows.add(subFlowA);
        haSubFlowRepository.add(subFlowA);
        HaSubFlow subFlowB = buildHaSubFlow(SUB_FLOW_ID_B, switch3, PORT_2, VLAN_3, INNER_VLAN_3, DESCRIPTION_2);
        haSubFlows.add(subFlowB);
        haSubFlowRepository.add(subFlowB);

        haFlow.setHaSubFlows(haSubFlows);
        haFlowRepository.add(haFlow);
    }

    private void createFlow() {
        flowRepository.add(Flow.builder()
                .flowId(FLOW_ID_1)
                .srcSwitch(switch1)
                .srcPort(PORT_1)
                .srcVlan(VLAN_1)
                .srcInnerVlan(INNER_VLAN_1)
                .destSwitch(switch2)
                .destPort(PORT_2)
                .destVlan(VLAN_2)
                .destInnerVlan(INNER_VLAN_2)
                .build());
    }
}
