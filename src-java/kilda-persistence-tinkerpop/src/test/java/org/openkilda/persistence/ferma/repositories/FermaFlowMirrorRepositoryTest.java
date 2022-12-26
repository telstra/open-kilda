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

package org.openkilda.persistence.ferma.repositories;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.openkilda.model.FlowMirror;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.FlowMirrorRepository;
import org.openkilda.persistence.repositories.SwitchRepository;

import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FermaFlowMirrorRepositoryTest extends InMemoryGraphBasedTest {
    static final String TEST_FLOW_MIRROR_ID_1 = "flow_mirror_1";
    static final String TEST_FLOW_MIRROR_ID_2 = "flow_mirror_2";
    static final String TEST_FLOW_MIRROR_ID_3 = "flow_mirror_3";
    static final SwitchId TEST_SWITCH_A_ID = new SwitchId(1);
    static final SwitchId TEST_SWITCH_B_ID = new SwitchId(2);
    static final SwitchId TEST_SWITCH_C_ID = new SwitchId(3);

    FlowMirrorRepository flowMirrorRepository;
    SwitchRepository switchRepository;

    Switch switchA;
    Switch switchB;
    Switch switchC;

    @Before
    public void setUp() {
        flowMirrorRepository = repositoryFactory.createFlowMirrorRepository();
        switchRepository = repositoryFactory.createSwitchRepository();

        switchA = createTestSwitch(TEST_SWITCH_A_ID.getId());
        switchB = createTestSwitch(TEST_SWITCH_B_ID.getId());
        switchC = createTestSwitch(TEST_SWITCH_C_ID.getId());

        assertEquals(3, switchRepository.findAll().size());
    }

    @Test
    public void shouldCreateFlowMirror() {
        createTestFlowMirrors();

        Collection<FlowMirror> allMirrors = flowMirrorRepository.findAll();
        assertEquals(2, allMirrors.size());

        FlowMirror foundMirror = flowMirrorRepository.findById(TEST_FLOW_MIRROR_ID_1).get();
        assertEquals(switchA.getSwitchId(), foundMirror.getMirrorSwitchId());
        assertEquals(switchB.getSwitchId(), foundMirror.getEgressSwitchId());
    }

    @Test
    public void shouldExistFlowMirror() {
        createTestFlowMirrors();
        assertTrue(flowMirrorRepository.exists(TEST_FLOW_MIRROR_ID_1));
        assertFalse(flowMirrorRepository.exists(TEST_FLOW_MIRROR_ID_3));
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowExceptionIfTransactionIsOpenDuringRemove() {
        createTestFlowMirrors();
        transactionManager.doInTransaction(() -> flowMirrorRepository.remove(TEST_FLOW_MIRROR_ID_1));
    }

    @Test
    public void shouldFindByDestEndpoint() {
        createTestFlowMirrors();

        FlowMirror foundMirror = flowMirrorRepository.findByEgressEndpoint(switchC.getSwitchId(), 3, 4, 5).get();
        assertEquals(switchA.getSwitchId(), foundMirror.getMirrorSwitchId());
        assertEquals(switchC.getSwitchId(), foundMirror.getEgressSwitchId());
    }

    @Test
    public void shouldFindByEgressSwitchIdAndPort() {
        createFlowMirror(TEST_FLOW_MIRROR_ID_1, switchA, switchB, 2, 3, 4);
        createFlowMirror(TEST_FLOW_MIRROR_ID_2, switchA, switchC, 3, 4, 5);
        createFlowMirror(TEST_FLOW_MIRROR_ID_3, switchB, switchC, 3, 5, 6);

        Map<String, FlowMirror> foundMirrors =
                flowMirrorRepository.findByEgressSwitchIdAndPort(switchC.getSwitchId(), 3)
                        .stream().collect(Collectors.toMap(FlowMirror::getFlowMirrorId, Function.identity()));
        assertEquals(2, foundMirrors.size());
        assertEquals(switchA.getSwitchId(), foundMirrors.get(TEST_FLOW_MIRROR_ID_2).getMirrorSwitchId());
        assertEquals(switchC.getSwitchId(), foundMirrors.get(TEST_FLOW_MIRROR_ID_2).getEgressSwitchId());
        assertEquals(switchB.getSwitchId(), foundMirrors.get(TEST_FLOW_MIRROR_ID_3).getMirrorSwitchId());
        assertEquals(switchC.getSwitchId(), foundMirrors.get(TEST_FLOW_MIRROR_ID_3).getEgressSwitchId());
    }

    @Test
    public void shouldDeleteFlowMirror() {
        FlowMirror mirror = createFlowMirror(TEST_FLOW_MIRROR_ID_1, switchA, switchB, 2, 3, 4);
        transactionManager.doInTransaction(() -> flowMirrorRepository.remove(mirror));
        assertEquals(0, flowMirrorRepository.findAll().size());
    }

    @Test
    public void shouldDeleteFoundFlowMirror() {
        createTestFlowMirrors();
        transactionManager.doInTransaction(() -> flowMirrorRepository.findAll().forEach(flowMirrorRepository::remove));
        assertEquals(0, flowMirrorRepository.findAll().size());
    }

    @Test
    public void shouldUpdateFlowMirrorStatus() {
        createTestFlowMirrors();
        assertEquals(FlowPathStatus.ACTIVE, flowMirrorRepository.findById(TEST_FLOW_MIRROR_ID_1).get().getStatus());
        assertEquals(FlowPathStatus.ACTIVE, flowMirrorRepository.findById(TEST_FLOW_MIRROR_ID_2).get().getStatus());

        flowMirrorRepository.updateStatus(TEST_FLOW_MIRROR_ID_2, FlowPathStatus.DEGRADED);
        assertEquals(FlowPathStatus.ACTIVE, flowMirrorRepository.findById(TEST_FLOW_MIRROR_ID_1).get().getStatus());
        assertEquals(FlowPathStatus.DEGRADED, flowMirrorRepository.findById(TEST_FLOW_MIRROR_ID_2).get().getStatus());
    }

    @Test
    public void shouldRemoveFlowMirror() {
        createTestFlowMirrors();
        assertEquals(2, flowMirrorRepository.findAll().size());

        flowMirrorRepository.remove(TEST_FLOW_MIRROR_ID_1);
        Collection<FlowMirror> allFlowMirrors = flowMirrorRepository.findAll();
        assertEquals(1, allFlowMirrors.size());
        assertEquals(TEST_FLOW_MIRROR_ID_2, allFlowMirrors.iterator().next().getFlowMirrorId());
    }

    private void createTestFlowMirrors() {
        createFlowMirror(TEST_FLOW_MIRROR_ID_1, switchA, switchB, 2, 3, 4);
        createFlowMirror(TEST_FLOW_MIRROR_ID_2, switchA, switchC, 3, 4, 5);
    }

    private FlowMirror createFlowMirror(
            String mirrorPathId, Switch mirrorSwitch, Switch egressSwitch, int dstPort, int dstOuterVlan,
            int dstInnerVlan) {
        FlowMirror flowMirror = FlowMirror.builder()
                .flowMirrorId(mirrorPathId)
                .mirrorSwitch(mirrorSwitch)
                .egressSwitch(egressSwitch)
                .egressPort(dstPort)
                .egressOuterVlan(dstOuterVlan)
                .egressInnerVlan(dstInnerVlan)
                .status(FlowPathStatus.ACTIVE)
                .build();
        flowMirrorRepository.add(flowMirror);
        return flowMirror;
    }
}
