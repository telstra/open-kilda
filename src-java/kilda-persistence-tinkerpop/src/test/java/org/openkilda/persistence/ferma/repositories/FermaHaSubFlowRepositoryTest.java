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

package org.openkilda.persistence.ferma.repositories;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.openkilda.persistence.ferma.repositories.FermaModelUtils.buildHaSubFlow;

import org.openkilda.model.FlowStatus;
import org.openkilda.model.HaFlow;
import org.openkilda.model.HaFlow.HaSharedEndpoint;
import org.openkilda.model.HaSubFlow;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.HaFlowRepository;
import org.openkilda.persistence.repositories.HaSubFlowRepository;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FermaHaSubFlowRepositoryTest extends InMemoryGraphBasedTest {
    private HaSubFlowRepository haSubFlowRepository;
    private HaFlowRepository haFlowRepository;

    private HaFlow haFlow;

    @Before
    public void setUp() {
        haFlowRepository = repositoryFactory.createHaFlowRepository();
        haSubFlowRepository = repositoryFactory.createHaSubFlowRepository();

        haFlow = HaFlow.builder()
                .haFlowId(HA_FLOW_ID_1)
                .sharedEndpoint(new HaSharedEndpoint(SWITCH_ID_3, 0, 0, 0))
                .build();
        haFlowRepository.add(haFlow);
    }

    @Test
    public void createSubFlowTest() {
        HaSubFlow sub1 = createSubFlow(SUB_FLOW_ID_1, SWITCH_ID_1, PORT_1, VLAN_1, INNER_VLAN_1, DESCRIPTION_1);
        HaSubFlow sub2 = createSubFlow(SUB_FLOW_ID_2, SWITCH_ID_2, PORT_2, VLAN_2, INNER_VLAN_2, DESCRIPTION_2);
        haFlow.setSubFlows(Sets.newHashSet(sub1, sub2));
        createSubFlow(SUB_FLOW_ID_3, SWITCH_ID_3, PORT_3, VLAN_3, INNER_VLAN_3, DESCRIPTION_3);

        Map<String, HaSubFlow> subFlowMap = haSubFlowsToMap(haSubFlowRepository.findAll());

        assertEquals(3, subFlowMap.size());
        assertSubFlow(SUB_FLOW_ID_1, SWITCH_ID_1, PORT_1, VLAN_1, INNER_VLAN_1, haFlow, subFlowMap.get(SUB_FLOW_ID_1),
                DESCRIPTION_1);
        assertSubFlow(SUB_FLOW_ID_2, SWITCH_ID_2, PORT_2, VLAN_2, INNER_VLAN_2, haFlow, subFlowMap.get(SUB_FLOW_ID_2),
                DESCRIPTION_2);
        assertSubFlow(SUB_FLOW_ID_3, SWITCH_ID_3, PORT_3, VLAN_3, INNER_VLAN_3, null, subFlowMap.get(SUB_FLOW_ID_3),
                DESCRIPTION_3);
    }

    @Test
    public void removeSubFlowTest() {
        HaSubFlow sub1 = createSubFlow(SUB_FLOW_ID_1, SWITCH_ID_1, PORT_1, VLAN_1, INNER_VLAN_1, DESCRIPTION_1);
        HaSubFlow sub2 = createSubFlow(SUB_FLOW_ID_2, SWITCH_ID_2, PORT_2, VLAN_2, INNER_VLAN_2, DESCRIPTION_2);
        HaSubFlow sub3 = createSubFlow(SUB_FLOW_ID_3, SWITCH_ID_3, PORT_3, VLAN_3, INNER_VLAN_3, DESCRIPTION_3);
        haFlow.setSubFlows(Sets.newHashSet(sub1, sub2));

        assertEquals(3, haSubFlowRepository.findAll().size());

        transactionManager.doInTransaction(() -> {
            haSubFlowRepository.remove(sub1);
            haSubFlowRepository.remove(sub3);
        });

        Collection<HaSubFlow> subFlows = haSubFlowRepository.findAll();
        assertEquals(1, subFlows.size());
        assertSubFlow(SUB_FLOW_ID_2, SWITCH_ID_2, PORT_2, VLAN_2, INNER_VLAN_2, haFlow, subFlows.iterator().next(),
                DESCRIPTION_2);
    }

    @Test
    public void findSubFlowByFlowIdTest() {
        HaSubFlow sub1 = createSubFlow(SUB_FLOW_ID_1, SWITCH_ID_1, PORT_1, VLAN_1, INNER_VLAN_1, DESCRIPTION_1);
        HaSubFlow sub2 = createSubFlow(SUB_FLOW_ID_2, SWITCH_ID_2, PORT_2, VLAN_2, INNER_VLAN_2, DESCRIPTION_2);
        haFlow.setSubFlows(Sets.newHashSet(sub1, sub2));
        createSubFlow(SUB_FLOW_ID_3, SWITCH_ID_3, PORT_3, VLAN_3, INNER_VLAN_3, DESCRIPTION_3);

        Optional<HaSubFlow> subFlow1 = haSubFlowRepository.findById(SUB_FLOW_ID_1);
        assertTrue(subFlow1.isPresent());
        assertSubFlow(SUB_FLOW_ID_1, SWITCH_ID_1, PORT_1, VLAN_1, INNER_VLAN_1, haFlow, subFlow1.get(), DESCRIPTION_1);

        Optional<HaSubFlow> subFlow2 = haSubFlowRepository.findById(SUB_FLOW_ID_2);
        assertTrue(subFlow2.isPresent());
        assertSubFlow(SUB_FLOW_ID_2, SWITCH_ID_2, PORT_2, VLAN_2, INNER_VLAN_2, haFlow, subFlow2.get(), DESCRIPTION_2);

        Optional<HaSubFlow> subFlow3 = haSubFlowRepository.findById(SUB_FLOW_ID_3);
        assertTrue(subFlow3.isPresent());
        assertSubFlow(SUB_FLOW_ID_3, SWITCH_ID_3, PORT_3, VLAN_3, INNER_VLAN_3, null, subFlow3.get(), DESCRIPTION_3);

        assertFalse(haSubFlowRepository.findById(SUB_FLOW_ID_4).isPresent());
    }

    @Test
    public void haSubFlowsExistTest() {
        assertFalse(haSubFlowRepository.exists(SUB_FLOW_ID_1));
        assertFalse(haSubFlowRepository.exists(SUB_FLOW_ID_2));

        createSubFlow(SUB_FLOW_ID_1, SWITCH_ID_1, PORT_1, VLAN_1, INNER_VLAN_1, DESCRIPTION_1);
        createSubFlow(SUB_FLOW_ID_2, SWITCH_ID_2, PORT_2, VLAN_2, INNER_VLAN_2, DESCRIPTION_2);

        assertTrue(haSubFlowRepository.exists(SUB_FLOW_ID_1));
        assertTrue(haSubFlowRepository.exists(SUB_FLOW_ID_2));
        assertFalse(haSubFlowRepository.exists(SUB_FLOW_ID_3));
    }

    private void assertSubFlow(
            String subFlowId, SwitchId switchId, int port, int vlan, int innerVLan, HaFlow haFlow,
            HaSubFlow actualSubFlow, String description) {
        assertEquals(subFlowId, actualSubFlow.getHaSubFlowId());
        assertEquals(switchId, actualSubFlow.getEndpointSwitchId());
        assertEquals(port, actualSubFlow.getEndpointPort());
        assertEquals(vlan, actualSubFlow.getEndpointVlan());
        assertEquals(innerVLan, actualSubFlow.getEndpointInnerVlan());
        assertEquals(description, actualSubFlow.getDescription());
        assertEquals(haFlow == null ? null : haFlow.getHaFlowId(), actualSubFlow.getHaFlowId());
        assertEquals(FlowStatus.UP, actualSubFlow.getStatus());
        assertEquals(haFlow, actualSubFlow.getHaFlow());
    }

    private HaSubFlow createSubFlow(
            String subFlowId, SwitchId switchId, int port, int vlan, int innerVlan, String description) {
        HaSubFlow subFlow = buildHaSubFlow(subFlowId, switchId, port, vlan, innerVlan, description);
        haSubFlowRepository.add(subFlow);
        return subFlow;
    }

    private static Map<String, HaSubFlow> haSubFlowsToMap(Collection<HaSubFlow> subFlows) {
        return subFlows.stream().collect(Collectors.toMap(HaSubFlow::getHaSubFlowId, Function.identity()));
    }
}
