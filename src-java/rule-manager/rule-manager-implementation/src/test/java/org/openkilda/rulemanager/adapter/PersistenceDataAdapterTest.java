/* Copyright 2021 Telstra Open Source
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

package org.openkilda.rulemanager.adapter;

import static java.util.Collections.singleton;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.openkilda.rulemanager.Utils.buildSwitch;
import static org.openkilda.rulemanager.Utils.buildSwitchProperties;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowTransitEncapsulation;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.TransitVlan;
import org.openkilda.model.Vxlan;
import org.openkilda.model.YFlow;
import org.openkilda.model.YFlow.SharedEndpoint;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.TransitVlanRepository;
import org.openkilda.persistence.repositories.VxlanRepository;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RunWith(MockitoJUnitRunner.class)
public class PersistenceDataAdapterTest {

    @Mock
    private PersistenceManager persistenceManager;
    @Mock
    private FlowRepository flowRepository;
    @Mock
    private FlowPathRepository flowPathRepository;
    @Mock
    private SwitchRepository switchRepository;
    @Mock
    private SwitchPropertiesRepository switchPropertiesRepository;
    @Mock
    private TransitVlanRepository transitVlanRepository;
    @Mock
    private VxlanRepository vxlanRepository;

    private PersistenceDataAdapter adapter;

    private static final SwitchId SWITCH_ID_1 = new SwitchId(1);
    private static final SwitchId SWITCH_ID_2 = new SwitchId(2);

    @Before
    public void setup() {
        RepositoryFactory repositoryFactory = mock(RepositoryFactory.class);
        when(repositoryFactory.createFlowRepository()).thenReturn(flowRepository);
        when(repositoryFactory.createFlowPathRepository()).thenReturn(flowPathRepository);
        when(repositoryFactory.createSwitchRepository()).thenReturn(switchRepository);
        when(repositoryFactory.createSwitchPropertiesRepository()).thenReturn(switchPropertiesRepository);
        when(repositoryFactory.createTransitVlanRepository()).thenReturn(transitVlanRepository);
        when(repositoryFactory.createVxlanRepository()).thenReturn(vxlanRepository);

        when(persistenceManager.getRepositoryFactory()).thenReturn(repositoryFactory);
    }

    @Test
    public void shouldProvideCorrectFlowPaths() {
        PathId pathId = new PathId("path1");
        Set<PathId> pathIds = Sets.newHashSet(pathId);
        FlowPath flowPath = FlowPath.builder()
                .pathId(pathId)
                .srcSwitch(buildSwitch(SWITCH_ID_1, Collections.emptySet()))
                .destSwitch(buildSwitch(SWITCH_ID_2, Collections.emptySet()))
                .build();
        Map<PathId, FlowPath> flowPaths = new HashMap<>();
        flowPaths.put(pathId, flowPath);
        when(flowPathRepository.findByIds(pathIds)).thenReturn(flowPaths);

        adapter = PersistenceDataAdapter.builder()
                .pathIds(pathIds)
                .persistenceManager(persistenceManager)
                .build();

        Map<PathId, FlowPath> actual = adapter.getCommonFlowPaths();

        assertEquals(1, actual.size());
        assertEquals(flowPath, actual.get(pathId));

        adapter.getCommonFlowPaths();

        verify(flowPathRepository).findByIds(pathIds);
        verifyNoMoreInteractions(flowPathRepository);
    }

    @Test
    public void shouldProvideCorrectFlows() {
        PathId pathId = new PathId("path1");
        Set<PathId> pathIds = Sets.newHashSet(pathId);
        Flow flow = Flow.builder()
                .flowId("flow")
                .srcSwitch(buildSwitch(SWITCH_ID_1, Collections.emptySet()))
                .destSwitch(buildSwitch(SWITCH_ID_2, Collections.emptySet()))
                .build();
        flow.setForwardPathId(pathId);
        Map<PathId, Flow> flows = new HashMap<>();
        flows.put(pathId, flow);
        when(flowPathRepository.findFlowsByPathIds(pathIds)).thenReturn(flows);

        adapter = PersistenceDataAdapter.builder()
                .pathIds(pathIds)
                .persistenceManager(persistenceManager)
                .build();

        Flow actual = adapter.getFlow(pathId);

        assertEquals(flow, actual);

        adapter.getFlow(new PathId("test"));

        verify(flowPathRepository).findFlowsByPathIds(pathIds);
        verifyNoMoreInteractions(flowPathRepository);
    }

    @Test
    public void shouldProvideCorrectSwitches() {
        Set<SwitchId> switchIds = Sets.newHashSet(SWITCH_ID_1, SWITCH_ID_2);
        Switch sw1 = buildSwitch(SWITCH_ID_1, Collections.emptySet());
        Switch sw2 = buildSwitch(SWITCH_ID_2, Collections.emptySet());
        Map<SwitchId, Switch> switches = new HashMap<>();
        switches.put(SWITCH_ID_1, sw1);
        switches.put(SWITCH_ID_2, sw2);
        when(switchRepository.findByIds(switchIds)).thenReturn(switches);

        adapter = PersistenceDataAdapter.builder()
                .switchIds(switchIds)
                .persistenceManager(persistenceManager)
                .build();

        assertEquals(sw1, adapter.getSwitch(SWITCH_ID_1));
        assertEquals(sw2, adapter.getSwitch(SWITCH_ID_2));

        verify(switchRepository).findByIds(switchIds);
        verifyNoMoreInteractions(switchRepository);
    }

    @Test
    public void shouldProvideCorrectSwitchProperties() {
        Set<SwitchId> switchIds = Sets.newHashSet(SWITCH_ID_1, SWITCH_ID_2);
        Switch sw1 = buildSwitch(SWITCH_ID_1, Collections.emptySet());
        SwitchProperties switchProperties1 = buildSwitchProperties(sw1, false);
        Switch sw2 = buildSwitch(SWITCH_ID_2, Collections.emptySet());
        SwitchProperties switchProperties2 = buildSwitchProperties(sw2, true);
        Map<SwitchId, SwitchProperties> switchProperties = new HashMap<>();
        switchProperties.put(SWITCH_ID_1, switchProperties1);
        switchProperties.put(SWITCH_ID_2, switchProperties2);
        when(switchPropertiesRepository.findBySwitchIds(switchIds)).thenReturn(switchProperties);

        adapter = PersistenceDataAdapter.builder()
                .switchIds(switchIds)
                .persistenceManager(persistenceManager)
                .build();

        assertEquals(switchProperties1, adapter.getSwitchProperties(SWITCH_ID_1));
        assertEquals(switchProperties2, adapter.getSwitchProperties(SWITCH_ID_2));

        verify(switchPropertiesRepository).findBySwitchIds(switchIds);
        verifyNoMoreInteractions(switchPropertiesRepository);
    }

    @Test
    public void shouldKeepMultitableForFlowInSwitchProperties() {
        Set<SwitchId> switchIds = Sets.newHashSet(SWITCH_ID_1, SWITCH_ID_2);
        Switch sw1 = buildSwitch(SWITCH_ID_1, singleton(SwitchFeature.MULTI_TABLE));
        SwitchProperties switchProperties1 = buildSwitchProperties(sw1, false);
        Switch sw2 = buildSwitch(SWITCH_ID_2, singleton(SwitchFeature.MULTI_TABLE));
        SwitchProperties switchProperties2 = buildSwitchProperties(sw2, true);
        Map<SwitchId, SwitchProperties> switchProperties = new HashMap<>();
        switchProperties.put(SWITCH_ID_1, switchProperties1);
        switchProperties.put(SWITCH_ID_2, switchProperties2);
        when(switchPropertiesRepository.findBySwitchIds(switchIds)).thenReturn(switchProperties);
        when(flowRepository.findByEndpointSwitchWithMultiTableSupport(SWITCH_ID_1))
                .thenReturn(singleton(mock(Flow.class)));

        adapter = PersistenceDataAdapter.builder()
                .switchIds(switchIds)
                .persistenceManager(persistenceManager)
                .keepMultitableForFlow(true)
                .build();

        assertTrue(adapter.getSwitchProperties(SWITCH_ID_1).isMultiTable());
        assertTrue(adapter.getSwitchProperties(SWITCH_ID_2).isMultiTable());
    }

    @Test
    public void shouldProvideCorrectVlanEncapsulation() {
        PathId pathId = new PathId("path1");
        Set<PathId> pathIds = Sets.newHashSet(pathId);
        TransitVlan transitVlan = TransitVlan.builder()
                .flowId("flowId")
                .pathId(pathId)
                .vlan(8)
                .build();
        when(transitVlanRepository.findByPathId(pathId, null)).thenReturn(Collections.singletonList(transitVlan));

        adapter = PersistenceDataAdapter.builder()
                .pathIds(pathIds)
                .persistenceManager(persistenceManager)
                .build();

        FlowTransitEncapsulation actual = adapter.getTransitEncapsulation(pathId, null);

        assertEquals(FlowEncapsulationType.TRANSIT_VLAN, actual.getType());
        assertEquals(transitVlan.getVlan(), actual.getId().intValue());

        adapter.getTransitEncapsulation(pathId, null);

        verify(transitVlanRepository).findByPathId(pathId, null);
        verifyNoMoreInteractions(transitVlanRepository);
        verifyNoInteractions(vxlanRepository);
    }

    @Test
    public void shouldProvideCorrectVxlanEncapsulation() {
        PathId pathId = new PathId("path1");
        Set<PathId> pathIds = Sets.newHashSet(pathId);
        Vxlan vxlan = Vxlan.builder()
                .flowId("flowId")
                .pathId(pathId)
                .vni(8)
                .build();
        when(vxlanRepository.findByPathId(pathId, null)).thenReturn(Collections.singletonList(vxlan));

        adapter = PersistenceDataAdapter.builder()
                .pathIds(pathIds)
                .persistenceManager(persistenceManager)
                .build();

        FlowTransitEncapsulation actual = adapter.getTransitEncapsulation(pathId, null);

        assertEquals(FlowEncapsulationType.VXLAN, actual.getType());
        assertEquals(vxlan.getVni(), actual.getId().intValue());

        adapter.getTransitEncapsulation(pathId, null);

        verify(transitVlanRepository).findByPathId(pathId, null);
        verify(vxlanRepository).findByPathId(pathId, null);
        verifyNoMoreInteractions(transitVlanRepository);
        verifyNoMoreInteractions(vxlanRepository);
    }

    @Test
    public void shouldProvideCorrectYFlows() {
        PathId pathId = new PathId("path1");
        Set<PathId> pathIds = Sets.newHashSet(pathId);
        YFlow yFlow = YFlow.builder()
                .yFlowId("flow")
                .sharedEndpoint(new SharedEndpoint(SWITCH_ID_1, 1))
                .build();
        Map<PathId, YFlow> yFlows = new HashMap<>();
        yFlows.put(pathId, yFlow);
        when(flowPathRepository.findYFlowsByPathIds(pathIds)).thenReturn(yFlows);

        adapter = PersistenceDataAdapter.builder()
                .pathIds(pathIds)
                .persistenceManager(persistenceManager)
                .build();

        YFlow actual = adapter.getYFlow(pathId);

        assertEquals(yFlow, actual);

        adapter.getYFlow(new PathId("test"));

        verify(flowPathRepository).findYFlowsByPathIds(pathIds);
        verifyNoMoreInteractions(flowPathRepository);
    }
}
