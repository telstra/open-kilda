/* Copyright 2020 Telstra Open Source
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.Isl;
import org.openkilda.model.IslStatus;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.model.SwitchStatus;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.persistence.exceptions.PersistenceException;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.IslRepository.IslImmutableView;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;

import com.google.common.collect.Lists;
import junit.framework.AssertionFailedError;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class FermaIslRepositoryTest extends InMemoryGraphBasedTest {
    static final SwitchId TEST_SWITCH_A_ID = new SwitchId(1);
    static final SwitchId TEST_SWITCH_B_ID = new SwitchId(2);
    static final SwitchId TEST_SWITCH_C_ID = new SwitchId(3);
    static final String TEST_FLOW_ID = "test_flow";

    IslRepository islRepository;
    SwitchRepository switchRepository;
    SwitchPropertiesRepository switchPropertiesRepository;
    FlowRepository flowRepository;
    FlowPathRepository flowPathRepository;

    Switch switchA;
    Switch switchB;
    Switch switchC;

    @Before
    public void setUp() {
        islRepository = repositoryFactory.createIslRepository();
        switchRepository = repositoryFactory.createSwitchRepository();
        flowRepository = repositoryFactory.createFlowRepository();
        flowPathRepository = repositoryFactory.createFlowPathRepository();
        switchPropertiesRepository = repositoryFactory.createSwitchPropertiesRepository();

        switchA = createTestSwitch(TEST_SWITCH_A_ID);
        SwitchProperties switchAFeatures = SwitchProperties.builder()
                .switchObj(switchA)
                .supportedTransitEncapsulation(SwitchProperties.DEFAULT_FLOW_ENCAPSULATION_TYPES)
                .build();
        switchPropertiesRepository.add(switchAFeatures);

        switchB = createTestSwitch(TEST_SWITCH_B_ID);
        SwitchProperties switchBFeatures = SwitchProperties.builder()
                .switchObj(switchB)
                .supportedTransitEncapsulation(SwitchProperties.DEFAULT_FLOW_ENCAPSULATION_TYPES)
                .build();
        switchPropertiesRepository.add(switchBFeatures);

        switchC = createTestSwitch(TEST_SWITCH_C_ID);
        SwitchProperties switchCFeatures = SwitchProperties.builder()
                .switchObj(switchC)
                .supportedTransitEncapsulation(SwitchProperties.DEFAULT_FLOW_ENCAPSULATION_TYPES)
                .build();
        switchPropertiesRepository.add(switchCFeatures);
        assertEquals(3, switchRepository.findAll().size());
    }

    @Test
    public void shouldCreateIsl() {
        createIsl(switchA, switchB);

        assertEquals(1, islRepository.findAll().size());
    }

    @Test
    public void shouldFindAllIsls() {
        Isl isl1 = createIsl(switchA, 111, switchB, 121, IslStatus.ACTIVE);
        Isl isl2 = createIsl(switchA, 112, switchB, 122, IslStatus.INACTIVE);
        Isl isl3 = createIsl(switchA, 113, switchB, 123, IslStatus.MOVED);

        List<Isl> foundIsls = Lists.newArrayList(islRepository.findAll());
        assertThat(foundIsls, Matchers.hasSize(3));
        assertThat(foundIsls, Matchers.containsInAnyOrder(isl1, isl2, isl3));
    }

    @Test
    public void shouldFindIslByEndpoint() {
        Isl isl = createIsl(switchA, 111, switchB, 112);

        assertTrue(islRepository.existsByEndpoint(TEST_SWITCH_A_ID, 111));

        List<Isl> foundIsls = Lists.newArrayList(islRepository.findByEndpoint(TEST_SWITCH_A_ID, 111));
        assertThat(foundIsls, Matchers.hasSize(1));
        assertThat(foundIsls, Matchers.contains(isl));

        foundIsls = Lists.newArrayList(islRepository.findByEndpoint(TEST_SWITCH_B_ID, 112));
        assertThat(foundIsls, Matchers.hasSize(1));
        assertThat(foundIsls, Matchers.contains(isl));
    }

    @Test
    public void shouldFindIslBySrcEndpoint() {
        Isl isl = createIsl(switchA, 111, switchB, 112);

        List<Isl> foundIsls = Lists.newArrayList(islRepository.findBySrcEndpoint(TEST_SWITCH_A_ID, 111));
        assertThat(foundIsls, Matchers.hasSize(1));
        assertThat(foundIsls, Matchers.contains(isl));
    }

    @Test
    public void shouldFindIslByDestEndpoint() {
        Isl isl = createIsl(switchA, 111, switchB, 112);

        List<Isl> foundIsls = Lists.newArrayList(islRepository.findByDestEndpoint(TEST_SWITCH_B_ID, 112));
        assertThat(foundIsls, Matchers.hasSize(1));
        assertThat(foundIsls, Matchers.contains(isl));
    }

    @Test
    public void shouldFindIslBySrcSwitch() {
        Isl isl1 = createIsl(switchA, 111, switchB, 112);
        Isl isl2 = createIsl(switchA, 112, switchB, 113);

        List<Isl> foundIsls = Lists.newArrayList(islRepository.findBySrcSwitch(TEST_SWITCH_A_ID));
        assertThat(foundIsls, Matchers.hasSize(2));
        assertThat(foundIsls, Matchers.containsInAnyOrder(isl1, isl2));
    }

    @Test
    public void shouldFindIslByDestSwitch() {
        Isl isl1 = createIsl(switchA, 111, switchB, 121);
        Isl isl2 = createIsl(switchA, 112, switchB, 122);

        List<Isl> foundIsls = Lists.newArrayList(islRepository.findByDestSwitch(TEST_SWITCH_B_ID));
        assertThat(foundIsls, Matchers.hasSize(2));
        assertThat(foundIsls, Matchers.containsInAnyOrder(isl1, isl2));
    }

    @Test
    public void shouldFindIslByEndpoints() {
        Isl isl = createIsl(switchA, 111, switchB, 112);

        Isl foundIsl = islRepository.findByEndpoints(TEST_SWITCH_A_ID, 111, TEST_SWITCH_B_ID, 112).get();
        assertEquals(isl, foundIsl);
    }

    @Test
    public void shouldFindIslByPartialEndpoints() {
        Isl isl1 = createIsl(switchA, 111, switchB, 121);
        Isl isl2 = createIsl(switchA, 112, switchC, 121);

        List<Isl> foundIsls = Lists.newArrayList(
                islRepository.findByPartialEndpoints(TEST_SWITCH_A_ID, 111, TEST_SWITCH_B_ID, 121));
        assertThat(foundIsls, Matchers.hasSize(1));
        assertThat(foundIsls, Matchers.contains(isl1));

        foundIsls = Lists.newArrayList(
                islRepository.findByPartialEndpoints(TEST_SWITCH_A_ID, 111, TEST_SWITCH_B_ID, null));
        assertThat(foundIsls, Matchers.hasSize(1));
        assertThat(foundIsls, Matchers.contains(isl1));

        foundIsls = Lists.newArrayList(
                islRepository.findByPartialEndpoints(TEST_SWITCH_A_ID, null, TEST_SWITCH_B_ID, 121));
        assertThat(foundIsls, Matchers.hasSize(1));
        assertThat(foundIsls, Matchers.contains(isl1));

        foundIsls = Lists.newArrayList(
                islRepository.findByPartialEndpoints(TEST_SWITCH_A_ID, null, TEST_SWITCH_B_ID, null));
        assertThat(foundIsls, Matchers.hasSize(1));
        assertThat(foundIsls, Matchers.contains(isl1));

        foundIsls = Lists.newArrayList(
                islRepository.findByPartialEndpoints(TEST_SWITCH_A_ID, 111, null, null));
        assertThat(foundIsls, Matchers.hasSize(1));
        assertThat(foundIsls, Matchers.contains(isl1));

        foundIsls = Lists.newArrayList(
                islRepository.findByPartialEndpoints(TEST_SWITCH_A_ID, null, null, 121));
        assertThat(foundIsls, Matchers.hasSize(2));
        assertThat(foundIsls, Matchers.containsInAnyOrder(isl1, isl2));

        foundIsls = Lists.newArrayList(
                islRepository.findByPartialEndpoints(TEST_SWITCH_A_ID, null, null, null));
        assertThat(foundIsls, Matchers.hasSize(2));
        assertThat(foundIsls, Matchers.containsInAnyOrder(isl1, isl2));

        foundIsls = Lists.newArrayList(
                islRepository.findByPartialEndpoints(new SwitchId(123456), null, null, null));
        assertThat(foundIsls, Matchers.empty());

        foundIsls = Lists.newArrayList(
                islRepository.findByPartialEndpoints(TEST_SWITCH_A_ID, null, new SwitchId(123456), null));
        assertThat(foundIsls, Matchers.empty());

        foundIsls = Lists.newArrayList(
                islRepository.findByPartialEndpoints(null, 111, TEST_SWITCH_B_ID, 121));
        assertThat(foundIsls, Matchers.hasSize(1));
        assertThat(foundIsls, Matchers.contains(isl1));

        foundIsls = Lists.newArrayList(
                islRepository.findByPartialEndpoints(null, 111, TEST_SWITCH_B_ID, null));
        assertThat(foundIsls, Matchers.hasSize(1));
        assertThat(foundIsls, Matchers.contains(isl1));

        foundIsls = Lists.newArrayList(
                islRepository.findByPartialEndpoints(null, null, TEST_SWITCH_B_ID, 121));
        assertThat(foundIsls, Matchers.hasSize(1));
        assertThat(foundIsls, Matchers.contains(isl1));

        foundIsls = Lists.newArrayList(
                islRepository.findByPartialEndpoints(null, 111, null, null));
        assertThat(foundIsls, Matchers.hasSize(1));
        assertThat(foundIsls, Matchers.contains(isl1));

        foundIsls = Lists.newArrayList(
                islRepository.findByPartialEndpoints(null, null, null, 121));
        assertThat(foundIsls, Matchers.hasSize(2));
        assertThat(foundIsls, Matchers.containsInAnyOrder(isl1, isl2));
    }

    @Test
    public void shouldFindIslByPathId() {
        Isl isl = createIsl(switchA, 111, switchB, 112);

        PathId pathId = new PathId("test_path_id");
        PathSegment segment = PathSegment.builder()
                .pathId(pathId)
                .srcSwitch(isl.getSrcSwitch()).srcPort(isl.getSrcPort())
                .destSwitch(isl.getDestSwitch()).destPort(isl.getDestPort())
                .build();
        FlowPath path = FlowPath.builder()
                .pathId(pathId)
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .segments(Collections.singletonList(segment))
                .build();
        flowPathRepository.add(path);

        List<Isl> foundIsls = Lists.newArrayList(islRepository.findByPathIds(Collections.singletonList(pathId)));
        assertThat(foundIsls, Matchers.hasSize(1));
        assertThat(foundIsls, Matchers.contains(isl));
    }

    @Test
    public void shouldSkipInactiveIsl() {
        createIsl(switchA, 111, switchB, 112, IslStatus.INACTIVE);

        List<IslImmutableView> foundIsl = Lists.newArrayList(islRepository.findAllActive());
        assertThat(foundIsl, Matchers.empty());
    }

    @Test
    public void shouldSkipInactiveIslSwitch() {
        switchA.setStatus(SwitchStatus.INACTIVE);

        createIsl(switchA, 111, switchB, 112, IslStatus.ACTIVE);

        List<IslImmutableView> foundIsl = Lists.newArrayList(islRepository.findAllActive());
        assertThat(foundIsl, Matchers.empty());
    }

    @Test
    public void shouldFindActiveIsl() {
        createIsl(switchA, 111, switchB, 112, IslStatus.ACTIVE);

        List<IslImmutableView> foundIsls = Lists.newArrayList(islRepository.findAllActive());
        assertThat(foundIsls, Matchers.hasSize(1));
    }

    @Test
    public void shouldFindActiveIslWithAvailableBandwidth() {
        Isl isl = createIsl(switchA, 111, switchB, 112, IslStatus.ACTIVE, 101L);

        List<IslImmutableView> foundIsls =
                Lists.newArrayList(islRepository.findActiveByBandwidthAndEncapsulationType(100,
                        FlowEncapsulationType.TRANSIT_VLAN));
        assertThat(foundIsls, Matchers.hasSize(1));
        assertEquals(isl.getAvailableBandwidth(), foundIsls.get(0).getAvailableBandwidth());
    }

    @Test
    public void shouldSkipIslWithNoEnoughBandwidth() {
        createIsl(switchA, 111, switchB, 112, IslStatus.ACTIVE, 99L);

        List<IslImmutableView> foundIsls = Lists.newArrayList(
                islRepository.findActiveByBandwidthAndEncapsulationType(100,
                        FlowEncapsulationType.TRANSIT_VLAN));
        assertThat(foundIsls, Matchers.hasSize(0));
    }

    @Test
    public void shouldFindIslOccupiedByFlowWithAvailableBandwidth() {
        Isl isl = createIsl(switchA, 1, switchB, 2, IslStatus.ACTIVE, 101L);

        Flow flow = createFlowWithPath(0, 0);
        PathId pathId = flow.getPaths().stream()
                .filter(path -> path.getSegments().get(0).getSrcPort() == isl.getSrcPort())
                .findAny().map(FlowPath::getPathId).orElseThrow(AssertionFailedError::new);

        List<IslImmutableView> foundIsls = Lists.newArrayList(
                islRepository.findActiveByPathAndBandwidthAndEncapsulationType(
                        pathId, 100, FlowEncapsulationType.TRANSIT_VLAN));
        assertThat(foundIsls, Matchers.hasSize(1));
    }

    @Test
    public void shouldSkipIslOccupiedByFlowWithNoEnoughBandwidth() {
        createIsl(switchA, 1, switchB, 2, IslStatus.ACTIVE, 99L);

        Flow flow = createFlowWithPath(0, 0);

        List<IslImmutableView> foundIsls = Lists.newArrayList(
                islRepository.findActiveByPathAndBandwidthAndEncapsulationType(
                        flow.getPathIds().iterator().next(), 100, FlowEncapsulationType.TRANSIT_VLAN));
        assertThat(foundIsls, Matchers.hasSize(0));
    }

    @Ignore("Need to fix merging of bandwidth values.")
    @Test
    public void shouldGetUsedBandwidth() {
        createFlowWithPath(59, 99);

        assertEquals(59, flowPathRepository.getUsedBandwidthBetweenEndpoints(
                TEST_SWITCH_A_ID, 1, TEST_SWITCH_B_ID, 2));

        assertEquals(99, flowPathRepository.getUsedBandwidthBetweenEndpoints(
                TEST_SWITCH_B_ID, 2, TEST_SWITCH_A_ID, 1));
    }

    @Test
    public void shouldDeleteIsl() {
        Isl isl = createIsl(switchA, switchB);

        transactionManager.doInTransaction(() ->
                islRepository.remove(isl));

        assertThat(islRepository.findAll(), Matchers.empty());
    }

    @Test
    public void shouldDeleteFoundIsl() {
        Isl isl1 = createIsl(switchA, 1, switchB, 1);
        createIsl(switchA, 2, switchB, 2);

        assertThat(islRepository.findAll(), Matchers.hasSize(2));

        transactionManager.doInTransaction(() ->
                islRepository.remove(isl1));

        assertThat(islRepository.findAll(), Matchers.hasSize(1));
    }

    @Test
    public void shouldCreateAndFindIslByEndpoint() {
        Isl isl = createIsl(switchA, 1, switchB, 1);

        assertThat(islRepository.findAll(), Matchers.hasSize(1));

        List<Isl> foundIsls = Lists.newArrayList(islRepository.findBySrcEndpoint(TEST_SWITCH_A_ID, 1));

        assertThat(foundIsls, Matchers.hasSize(1));
        assertThat(foundIsls, Matchers.contains(isl));
    }

    @Test
    public void shouldCreateAndFindIslByEndpoints() {
        Isl isl = createIsl(switchA, 1, switchB, 1);

        assertThat(islRepository.findAll(), Matchers.hasSize(1));

        Isl foundIsl = islRepository.findByEndpoints(TEST_SWITCH_A_ID, 1, TEST_SWITCH_B_ID, 1).get();

        assertEquals(isl, foundIsl);
    }

    @Test
    public void shouldReturnSymmetricIslsWithRequiredBandwidth() {
        long availableBandwidth = 100L;

        createIsl(switchA, 1, switchB, 2, IslStatus.ACTIVE, availableBandwidth);
        createIsl(switchB, 2, switchA, 1, IslStatus.ACTIVE, availableBandwidth);

        assertThat(islRepository.findSymmetricActiveByBandwidthAndEncapsulationType(availableBandwidth,
                FlowEncapsulationType.TRANSIT_VLAN), Matchers.hasSize(2));
    }

    @Test
    public void shouldNotReturnIslIfOneDirectionDoesntHaveEnoughBandwidth() {
        long availableBandwidth = 100L;

        createIsl(switchA, 1, switchB, 2, IslStatus.ACTIVE, availableBandwidth);
        createIsl(switchB, 2, switchA, 1, IslStatus.ACTIVE, availableBandwidth - 1);

        assertThat(islRepository.findSymmetricActiveByBandwidthAndEncapsulationType(availableBandwidth,
                FlowEncapsulationType.TRANSIT_VLAN), Matchers.empty());
    }

    @Test
    public void shouldFindActiveIslsByFlowEncapsulationType() {
        createIsl(switchA, 1, switchB, 2, IslStatus.ACTIVE, 100L);

        List<IslImmutableView> allIsls = Lists.newArrayList(islRepository.findAllActive());
        assertThat(allIsls, Matchers.hasSize(1));

        List<IslImmutableView> foundIsls = Lists.newArrayList(
                islRepository.findActiveByEncapsulationType(FlowEncapsulationType.TRANSIT_VLAN));
        assertThat(foundIsls, Matchers.hasSize(1));
    }

    @Test
    public void shouldUpdateAvailableBandwidth() {
        Isl isl = createIsl(switchA, 1, switchB, 2, IslStatus.ACTIVE, 100L);
        isl.setMaxBandwidth(100L);

        createPathWithSegment(TEST_FLOW_ID, switchA, 1, switchB, 2, 33L);

        islRepository.updateAvailableBandwidth(TEST_SWITCH_A_ID, 1, TEST_SWITCH_B_ID, 2);

        Isl islAfter = islRepository.findByEndpoints(TEST_SWITCH_A_ID, 1, TEST_SWITCH_B_ID, 2).get();
        assertEquals(67, islAfter.getAvailableBandwidth());
    }

    @Test
    public void shouldUpdateAvailableBandwidthForSameSharedBandwidthGroup() {
        Isl isl = createIsl(switchA, 1, switchB, 2, IslStatus.ACTIVE, 100L);
        isl.setMaxBandwidth(100L);

        createPathWithSegment("path1", switchA, 1, switchB, 2, 60L, TEST_FLOW_ID);
        createPathWithSegment("path2", switchA, 1, switchB, 2, 70L, TEST_FLOW_ID);

        islRepository.updateAvailableBandwidth(TEST_SWITCH_A_ID, 1, TEST_SWITCH_B_ID, 2);

        Isl islAfter = islRepository.findByEndpoints(TEST_SWITCH_A_ID, 1, TEST_SWITCH_B_ID, 2).get();
        assertEquals(30, islAfter.getAvailableBandwidth());
    }

    @Test
    public void shouldUpdateAvailableBandwidthForSameSharedBandwidthGroupAndIgnoredBandwidth() {
        Isl isl = createIsl(switchA, 1, switchB, 2, IslStatus.ACTIVE, 100L);
        isl.setMaxBandwidth(100L);

        createPathWithSegment("path1", switchA, 1, switchB, 2, 30L, TEST_FLOW_ID);
        FlowPath path2 = createPathWithSegment("path2", switchA, 1, switchB, 2, 70L, TEST_FLOW_ID);
        path2.setIgnoreBandwidth(true);

        islRepository.updateAvailableBandwidth(TEST_SWITCH_A_ID, 1, TEST_SWITCH_B_ID, 2);

        Isl islAfter = islRepository.findByEndpoints(TEST_SWITCH_A_ID, 1, TEST_SWITCH_B_ID, 2).get();
        assertEquals(70, islAfter.getAvailableBandwidth());
    }

    @Test
    public void shouldUpdateAvailableBandwidthForDifferentSharedBandwidthGroups() {
        Isl isl = createIsl(switchA, 1, switchB, 2, IslStatus.ACTIVE, 100L);
        isl.setMaxBandwidth(100L);

        createPathWithSegment("path1", switchA, 1, switchB, 2, 30L, "path1group");
        createPathWithSegment("path2", switchA, 1, switchB, 2, 40L, "path2group");

        islRepository.updateAvailableBandwidth(TEST_SWITCH_A_ID, 1, TEST_SWITCH_B_ID, 2);

        Isl islAfter = islRepository.findByEndpoints(TEST_SWITCH_A_ID, 1, TEST_SWITCH_B_ID, 2).get();
        assertEquals(30, islAfter.getAvailableBandwidth());
    }

    @Test
    public void shouldUpdateAvailableBandwidthForAllKindsOfSharedBandwidthGroups() {
        Isl isl = createIsl(switchA, 1, switchB, 2, IslStatus.ACTIVE, 100L);
        isl.setMaxBandwidth(100L);

        createPathWithSegment("path1", switchA, 1, switchB, 2, 10L, "path12group");
        createPathWithSegment("path2", switchA, 1, switchB, 2, 30L, "path12group");
        createPathWithSegment("path3", switchA, 1, switchB, 2, 50L, "path3group");
        createPathWithSegment("path4", switchA, 1, switchB, 2, 5L);

        islRepository.updateAvailableBandwidth(TEST_SWITCH_A_ID, 1, TEST_SWITCH_B_ID, 2);

        Isl islAfter = islRepository.findByEndpoints(TEST_SWITCH_A_ID, 1, TEST_SWITCH_B_ID, 2).get();
        assertEquals(15, islAfter.getAvailableBandwidth());
    }

    @Test
    public void shouldNotUpdateAvailableBandwidthIfEndpointDoesntMatch() {
        Isl isl = createIsl(switchA, 1, switchB, 2, IslStatus.ACTIVE, 100L);
        isl.setMaxBandwidth(100L);

        try {
            createPathWithSegment(TEST_FLOW_ID + "_1", switchA, 1, switchB, 3, 33L);
            islRepository.updateAvailableBandwidth(TEST_SWITCH_A_ID, 1, TEST_SWITCH_B_ID, 3);
            fail();
        } catch (PersistenceException ex) {
            // expected
        }

        Isl islAfter = islRepository.findByEndpoints(TEST_SWITCH_A_ID, 1, TEST_SWITCH_B_ID, 2).get();
        assertEquals(100, islAfter.getAvailableBandwidth());

        try {
            createPathWithSegment(TEST_FLOW_ID + "_2", switchA, 2, switchB, 2, 33L);
            islRepository.updateAvailableBandwidth(TEST_SWITCH_A_ID, 2, TEST_SWITCH_B_ID, 2);
            fail();
        } catch (PersistenceException ex) {
            // expected
        }

        islAfter = islRepository.findByEndpoints(TEST_SWITCH_A_ID, 1, TEST_SWITCH_B_ID, 2).get();
        assertEquals(100, islAfter.getAvailableBandwidth());

        try {
            createPathWithSegment(TEST_FLOW_ID + "_3", switchC, 1, switchB, 2, 33L);
            islRepository.updateAvailableBandwidth(TEST_SWITCH_C_ID, 1, TEST_SWITCH_B_ID, 2);
            fail();
        } catch (PersistenceException ex) {
            // expected
        }

        islAfter = islRepository.findByEndpoints(TEST_SWITCH_A_ID, 1, TEST_SWITCH_B_ID, 2).get();
        assertEquals(100, islAfter.getAvailableBandwidth());

        try {
            createPathWithSegment(TEST_FLOW_ID + "_4", switchA, 1, switchC, 2, 33L);
            islRepository.updateAvailableBandwidth(TEST_SWITCH_A_ID, 1, TEST_SWITCH_C_ID, 2);
            fail();
        } catch (PersistenceException ex) {
            // expected
        }

        islAfter = islRepository.findByEndpoints(TEST_SWITCH_A_ID, 1, TEST_SWITCH_B_ID, 2).get();
        assertEquals(100, islAfter.getAvailableBandwidth());
    }

    @Test
    public void shouldUpdateAvailableBandwidthByPath() {
        Isl isl = createIsl(switchA, 1, switchB, 2, IslStatus.ACTIVE, 100L);
        isl.setMaxBandwidth(100L);

        createPathWithSegment(TEST_FLOW_ID, switchA, 1, switchB, 2, 33L);

        islRepository.updateAvailableBandwidthOnIslsOccupiedByPath(new PathId(TEST_FLOW_ID));

        Isl islAfter = islRepository.findByEndpoints(TEST_SWITCH_A_ID, 1, TEST_SWITCH_B_ID, 2).get();
        assertEquals(67, islAfter.getAvailableBandwidth());
    }

    @Test
    public void shouldNotUpdateAvailableBandwidthByPathIfDoesntMatch() {
        Isl isl = createIsl(switchA, 1, switchB, 2, IslStatus.ACTIVE, 100L);
        isl.setMaxBandwidth(100L);

        createPathWithSegment(TEST_FLOW_ID + "_1", switchA, 1, switchB, 2, 33L);

        Collection<?> updatedIsls = islRepository.updateAvailableBandwidthOnIslsOccupiedByPath(
                new PathId(TEST_FLOW_ID + "_faked")).values();
        assertThat(updatedIsls, Matchers.empty());

        Isl islAfter = islRepository.findByEndpoints(TEST_SWITCH_A_ID, 1, TEST_SWITCH_B_ID, 2).get();
        assertEquals(100, islAfter.getAvailableBandwidth());
    }

    private Isl createIsl(Switch srcSwitch, Switch destSwitch) {
        return createIsl(srcSwitch, null, destSwitch, null, null, null);
    }

    private Isl createIsl(Switch srcSwitch, int srcPort, Switch destSwitch, int destPort) {
        return createIsl(srcSwitch, srcPort, destSwitch, destPort, null, null);
    }

    private Isl createIsl(Switch srcSwitch, int srcPort, Switch destSwitch, int destPort, IslStatus status) {
        return createIsl(srcSwitch, srcPort, destSwitch, destPort, status, null);
    }

    private Isl createIsl(Switch srcSwitch, Integer srcPort, Switch destSwitch, Integer destPort, IslStatus status,
                          Long availableBandwidth) {
        Isl.IslBuilder islBuilder = Isl.builder()
                .srcSwitch(srcSwitch)
                .destSwitch(destSwitch)
                .status(status);
        if (srcPort != null) {
            islBuilder.srcPort(srcPort);
        }
        if (destPort != null) {
            islBuilder.destPort(destPort);
        }
        if (availableBandwidth != null) {
            islBuilder.availableBandwidth(availableBandwidth);
        }
        Isl isl = islBuilder.build();
        islRepository.add(isl);
        return isl;
    }

    private FlowPath createPathWithSegment(String pathId, Switch srcSwitch, Integer srcPort,
                                           Switch destSwitch, Integer destPort, long bandwidth) {
        return createPathWithSegment(pathId, srcSwitch, srcPort, destSwitch, destPort, bandwidth, null);
    }

    private FlowPath createPathWithSegment(String pathId, Switch srcSwitch, Integer srcPort,
                                           Switch destSwitch, Integer destPort, long bandwidth,
                                           String sharedBandwidthGroupId) {
        PathId pathIdAsObj = new PathId(pathId);
        FlowPath path = FlowPath.builder()
                .srcSwitch(srcSwitch)
                .destSwitch(destSwitch)
                .pathId(pathIdAsObj)
                .bandwidth(bandwidth)
                .sharedBandwidthGroupId(sharedBandwidthGroupId)
                .segments(Collections.singletonList(PathSegment.builder()
                        .pathId(pathIdAsObj)
                        .srcSwitch(srcSwitch)
                        .srcPort(srcPort)
                        .destSwitch(destSwitch)
                        .destPort(destPort)
                        .build()))
                .build();
        flowPathRepository.add(path);
        return path;
    }

    private Flow createFlowWithPath(int forwardBandwidth, int reverseBandwidth) {
        Flow flow = Flow.builder()
                .flowId(TEST_FLOW_ID)
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .status(FlowStatus.UP)
                .build();

        FlowPath forwardPath = FlowPath.builder()
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .pathId(new PathId(TEST_FLOW_ID + "_forward_path"))
                .cookie(new FlowSegmentCookie(1))
                .meterId(new MeterId(1))
                .status(FlowPathStatus.ACTIVE)
                .bandwidth(forwardBandwidth)
                .ignoreBandwidth(false)
                .build();
        flow.setForwardPath(forwardPath);

        PathSegment forwardSegment = PathSegment.builder()
                .pathId(forwardPath.getPathId())
                .srcSwitch(switchA)
                .srcPort(1)
                .destSwitch(switchB)
                .destPort(2)
                .build();
        forwardPath.setSegments(Collections.singletonList(forwardSegment));

        FlowPath reversePath = FlowPath.builder()
                .srcSwitch(switchB)
                .destSwitch(switchA)
                .pathId(new PathId(TEST_FLOW_ID + "_reverse_path"))
                .cookie(new FlowSegmentCookie(2))
                .meterId(new MeterId(2))
                .status(FlowPathStatus.ACTIVE)
                .bandwidth(reverseBandwidth)
                .ignoreBandwidth(false)
                .build();
        flow.setReversePath(reversePath);

        PathSegment reverseSegment = PathSegment.builder()
                .pathId(reversePath.getPathId())
                .srcSwitch(switchB)
                .srcPort(2)
                .destSwitch(switchA)
                .destPort(1)
                .build();
        reversePath.setSegments(Collections.singletonList(reverseSegment));

        flowRepository.add(flow);
        return flow;
    }
}
