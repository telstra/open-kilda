/* Copyright 2018 Telstra Open Source
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

package org.openkilda.persistence.repositories.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import org.openkilda.model.Cookie;
import org.openkilda.model.FlowPath;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.Isl;
import org.openkilda.model.IslStatus;
import org.openkilda.model.MeterId;
import org.openkilda.model.PathId;
import org.openkilda.model.PathSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.Neo4jBasedTest;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.SwitchRepository;

import com.google.common.collect.Lists;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

public class Neo4jIslRepositoryTest extends Neo4jBasedTest {
    static final SwitchId TEST_SWITCH_A_ID = new SwitchId(1);
    static final SwitchId TEST_SWITCH_B_ID = new SwitchId(2);
    static final String TEST_FLOW_ID = "test_flow";

    static IslRepository islRepository;
    static SwitchRepository switchRepository;
    static FlowPathRepository flowPathRepository;

    private Switch switchA;
    private Switch switchB;

    @BeforeClass
    public static void setUp() {
        islRepository = new Neo4jIslRepository(neo4jSessionFactory, txManager);
        switchRepository = new Neo4jSwitchRepository(neo4jSessionFactory, txManager);
        flowPathRepository = new Neo4jFlowPathRepository(neo4jSessionFactory, txManager);
    }

    @Before
    public void createSwitches() {
        switchA = buildTestSwitch(1);
        switchRepository.createOrUpdate(switchA);

        switchB = buildTestSwitch(2);
        switchRepository.createOrUpdate(switchB);

        assertEquals(2, switchRepository.findAll().size());
    }

    @Test
    public void shouldCreateIsl() {
        Isl isl = new Isl();
        isl.setSrcSwitch(switchA);
        isl.setDestSwitch(switchB);

        islRepository.createOrUpdate(isl);

        assertEquals(1, islRepository.findAll().size());
    }

    @Test
    public void shouldCreateSwitchAlongWithIsl() {
        Isl isl = new Isl();
        isl.setSrcSwitch(switchA);
        isl.setDestSwitch(switchB);

        islRepository.createOrUpdate(isl);

        assertEquals(2, switchRepository.findAll().size());
    }

    @Test
    public void shouldFindIslByEndpoint() {
        Isl isl = new Isl();
        isl.setSrcSwitch(switchA);
        isl.setSrcPort(111);
        isl.setDestSwitch(switchB);
        isl.setDestPort(112);

        islRepository.createOrUpdate(isl);

        List<Isl> foundIsls = Lists.newArrayList(islRepository.findByEndpoint(TEST_SWITCH_A_ID, 111));
        assertEquals(1, foundIsls.size());
        assertEquals(switchA.getSwitchId(), foundIsls.get(0).getSrcSwitch().getSwitchId());
        assertEquals(switchB.getSwitchId(), foundIsls.get(0).getDestSwitch().getSwitchId());

        foundIsls = Lists.newArrayList(islRepository.findByEndpoint(TEST_SWITCH_B_ID, 112));
        assertEquals(1, foundIsls.size());
        assertEquals(switchA.getSwitchId(), foundIsls.get(0).getSrcSwitch().getSwitchId());
        assertEquals(switchB.getSwitchId(), foundIsls.get(0).getDestSwitch().getSwitchId());
    }

    @Test
    public void shouldFindIslBySrcEndpoint() {
        Isl isl = new Isl();
        isl.setSrcSwitch(switchA);
        isl.setSrcPort(111);
        isl.setDestSwitch(switchB);

        islRepository.createOrUpdate(isl);

        List<Isl> foundIsls = Lists.newArrayList(islRepository.findBySrcEndpoint(TEST_SWITCH_A_ID, 111));
        assertEquals(1, foundIsls.size());
        assertEquals(switchA.getSwitchId(), foundIsls.get(0).getSrcSwitch().getSwitchId());
        assertEquals(switchB.getSwitchId(), foundIsls.get(0).getDestSwitch().getSwitchId());
    }

    @Test
    public void shouldFindIslByDestEndpoint() {
        Isl isl = new Isl();
        isl.setSrcSwitch(switchA);
        isl.setSrcPort(111);
        isl.setDestSwitch(switchB);
        isl.setDestPort(112);

        islRepository.createOrUpdate(isl);

        List<Isl> foundIsls = Lists.newArrayList(islRepository.findByDestEndpoint(TEST_SWITCH_B_ID, 112));
        assertEquals(1, foundIsls.size());
        assertEquals(switchA.getSwitchId(), foundIsls.get(0).getSrcSwitch().getSwitchId());
        assertEquals(switchB.getSwitchId(), foundIsls.get(0).getDestSwitch().getSwitchId());
    }

    @Test
    public void shouldFindIslByEndpoints() {
        Isl isl = new Isl();
        isl.setSrcSwitch(switchA);
        isl.setSrcPort(111);
        isl.setDestSwitch(switchB);
        isl.setDestPort(112);

        islRepository.createOrUpdate(isl);

        Isl foundIsl = islRepository.findByEndpoints(TEST_SWITCH_A_ID, 111, TEST_SWITCH_B_ID, 112).get();
        assertEquals(switchA.getSwitchId(), foundIsl.getSrcSwitch().getSwitchId());
        assertEquals(switchB.getSwitchId(), foundIsl.getDestSwitch().getSwitchId());
    }

    @Test
    public void shouldSkipInactiveIsl() {
        Isl isl = new Isl();
        isl.setSrcSwitch(switchA);
        isl.setSrcPort(111);
        isl.setDestSwitch(switchB);
        isl.setStatus(IslStatus.INACTIVE);

        islRepository.createOrUpdate(isl);

        List<Isl> foundIsl = Lists.newArrayList(islRepository.findAllActive());
        assertThat(foundIsl, Matchers.empty());
    }

    @Test
    public void shouldSkipInactiveIslSwitch() {
        switchA.setStatus(SwitchStatus.INACTIVE);
        switchRepository.createOrUpdate(switchA);

        Isl isl = new Isl();
        isl.setSrcSwitch(switchA);
        isl.setSrcPort(111);
        isl.setDestSwitch(switchB);
        isl.setStatus(IslStatus.ACTIVE);

        islRepository.createOrUpdate(isl);

        List<Isl> foundIsl = Lists.newArrayList(islRepository.findAllActive());
        assertThat(foundIsl, Matchers.empty());
    }

    @Test
    public void shouldFindActiveIsl() {
        Isl isl = new Isl();
        isl.setSrcSwitch(switchA);
        isl.setSrcPort(111);
        isl.setDestSwitch(switchB);
        isl.setStatus(IslStatus.ACTIVE);

        islRepository.createOrUpdate(isl);

        List<Isl> foundIsl = Lists.newArrayList(islRepository.findAllActive());
        assertThat(foundIsl, Matchers.hasSize(1));
    }

    @Test
    public void shouldFindActiveIslWithAvailableBandwidth() {
        Isl isl = new Isl();
        isl.setSrcSwitch(switchA);
        isl.setDestSwitch(switchB);
        isl.setStatus(IslStatus.ACTIVE);
        isl.setAvailableBandwidth(101);

        islRepository.createOrUpdate(isl);

        List<Isl> foundIsl = Lists.newArrayList(islRepository.findActiveWithAvailableBandwidth(100));
        assertThat(foundIsl, Matchers.hasSize(1));
    }

    @Test
    public void shouldSkipIslWithNoEnoughBandwidth() {
        Isl isl = new Isl();
        isl.setSrcSwitch(switchA);
        isl.setDestSwitch(switchB);
        isl.setStatus(IslStatus.ACTIVE);
        isl.setAvailableBandwidth(99);

        islRepository.createOrUpdate(isl);

        List<Isl> foundIsl = Lists.newArrayList(islRepository.findActiveWithAvailableBandwidth(100));
        assertThat(foundIsl, Matchers.hasSize(0));
    }

    @Test
    public void shouldFindIslOccupiedByFlowWithAvailableBandwidth() {
        Isl isl = new Isl();
        isl.setSrcSwitch(switchA);
        isl.setDestSwitch(switchB);
        isl.setStatus(IslStatus.ACTIVE);
        isl.setAvailableBandwidth(101);

        islRepository.createOrUpdate(isl);

        PathSegment segment = PathSegment.builder()
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .pathId(new PathId(TEST_FLOW_ID + "_path"))
                .build();

        FlowPath path = FlowPath.builder()
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .pathId(new PathId(TEST_FLOW_ID + "_path"))
                .cookie(new Cookie(1))
                .flowId(TEST_FLOW_ID)
                .meterId(new MeterId(1))
                .segments(Collections.singletonList(segment))
                .status(FlowPathStatus.ACTIVE)
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();

        flowPathRepository.createOrUpdate(path);

        List<Isl> foundIsls = Lists.newArrayList(
                islRepository.findActiveAndOccupiedByFlowWithAvailableBandwidth(TEST_FLOW_ID, 100));
        assertThat(foundIsls, Matchers.hasSize(1));
    }

    @Test
    public void shouldSkipIslOccupiedByFlowWithNoEnoughBandwidth() {
        Isl isl = new Isl();
        isl.setSrcSwitch(switchA);
        isl.setDestSwitch(switchB);
        isl.setStatus(IslStatus.ACTIVE);
        isl.setAvailableBandwidth(99);

        islRepository.createOrUpdate(isl);

        PathSegment segment = PathSegment.builder()
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .pathId(new PathId(TEST_FLOW_ID + "_path"))
                .build();
        FlowPath path = FlowPath.builder()
                .pathId(new PathId(TEST_FLOW_ID + "_path"))
                .flowId(TEST_FLOW_ID)
                .cookie(new Cookie(1))
                .meterId(new MeterId(1))
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .status(FlowPathStatus.ACTIVE)
                .segments(Collections.singletonList(segment))
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();
        flowPathRepository.createOrUpdate(path);

        List<Isl> foundIsls = Lists.newArrayList(
                islRepository.findActiveAndOccupiedByFlowWithAvailableBandwidth(TEST_FLOW_ID, 100));
        assertThat(foundIsls, Matchers.hasSize(0));
    }

    @Ignore("Need to fix merging of bandwidth values.")
    @Test
    public void shouldGetUsedBandwidth() {
        PathSegment forwardSegment = PathSegment.builder()
                .srcSwitch(switchA)
                .srcPort(1)
                .destSwitch(switchB)
                .destPort(2)
                .pathId(new PathId(TEST_FLOW_ID + "_path1"))
                .build();

        FlowPath forwardPath = FlowPath.builder()
                .srcSwitch(switchA)
                .destSwitch(switchB)
                .pathId(new PathId(TEST_FLOW_ID + "_path1"))
                .cookie(new Cookie(1))
                .flowId(TEST_FLOW_ID)
                .meterId(new MeterId(1))
                .segments(Collections.singletonList(forwardSegment))
                .status(FlowPathStatus.ACTIVE)
                .bandwidth(59)
                .ignoreBandwidth(false)
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();
        flowPathRepository.createOrUpdate(forwardPath);

        PathSegment reverseSegment = PathSegment.builder()
                .srcSwitch(switchB)
                .srcPort(2)
                .destSwitch(switchA)
                .destPort(1)
                .pathId(new PathId(TEST_FLOW_ID + "_path2"))
                .build();

        FlowPath reversePath = FlowPath.builder()
                .srcSwitch(switchB)
                .destSwitch(switchA)
                .pathId(new PathId(TEST_FLOW_ID + "_path2"))
                .cookie(new Cookie(2))
                .flowId(TEST_FLOW_ID)
                .meterId(new MeterId(2))
                .segments(Collections.singletonList(reverseSegment))
                .status(FlowPathStatus.ACTIVE)
                .bandwidth(99)
                .ignoreBandwidth(false)
                .timeCreate(Instant.now())
                .timeModify(Instant.now())
                .build();
        flowPathRepository.createOrUpdate(reversePath);

        assertEquals(59, flowPathRepository.getUsedBandwidthBetweenEndpoints(
                TEST_SWITCH_A_ID, 1, TEST_SWITCH_B_ID, 2));

        assertEquals(99, flowPathRepository.getUsedBandwidthBetweenEndpoints(
                TEST_SWITCH_B_ID, 2, TEST_SWITCH_A_ID, 1));
    }

    @Test
    public void shouldDeleteIsl() {
        Isl isl = new Isl();
        isl.setSrcSwitch(switchA);
        isl.setDestSwitch(switchB);

        islRepository.createOrUpdate(isl);
        islRepository.delete(isl);

        assertEquals(0, islRepository.findAll().size());
    }

    @Test
    public void shouldDeleteFoundIsl() {
        Isl isl1 = new Isl();
        isl1.setSrcSwitch(switchA);
        isl1.setSrcPort(1);
        isl1.setDestSwitch(switchB);
        isl1.setDestPort(1);

        Isl isl2 = new Isl();
        isl2.setSrcSwitch(switchA);
        isl2.setSrcPort(2);
        isl2.setDestSwitch(switchB);
        isl2.setDestPort(2);

        islRepository.createOrUpdate(isl1);
        islRepository.createOrUpdate(isl2);

        assertEquals(2, islRepository.findAll().size());

        islRepository.delete(isl1);

        assertEquals(1, islRepository.findAll().size());
    }

    @Test
    public void shouldCreateAndFindIslByEndpoint() {
        Isl isl = new Isl();
        isl.setSrcSwitch(switchA);
        isl.setSrcPort(1);
        isl.setDestSwitch(switchB);
        isl.setDestPort(1);

        islRepository.createOrUpdate(isl);

        assertEquals(1, islRepository.findAll().size());
        assertEquals(2, switchRepository.findAll().size());

        List<Isl> foundIsls = Lists.newArrayList(islRepository.findBySrcEndpoint(TEST_SWITCH_A_ID, 1));

        assertEquals(1, foundIsls.size());
        assertEquals(switchB.getSwitchId(), foundIsls.get(0).getDestSwitch().getSwitchId());
    }

    @Test
    public void shouldCreateAndFindIslByEndpoints() {
        Isl isl = new Isl();
        isl.setSrcSwitch(switchA);
        isl.setSrcPort(1);
        isl.setDestSwitch(switchB);
        isl.setDestPort(1);

        islRepository.createOrUpdate(isl);

        assertEquals(1, islRepository.findAll().size());
        assertEquals(2, switchRepository.findAll().size());

        Isl foundIsl = islRepository.findByEndpoints(TEST_SWITCH_A_ID, 1, TEST_SWITCH_B_ID, 1).get();

        assertEquals(switchB.getSwitchId(), foundIsl.getDestSwitch().getSwitchId());
    }

    @Test
    public void shouldReturnSymmetricIslsWithRequiredBandwidth() {
        long availableBandwidth = 100L;

        Isl forwardIsl = new Isl();
        forwardIsl.setSrcSwitch(switchA);
        forwardIsl.setSrcPort(1);
        forwardIsl.setDestSwitch(switchB);
        forwardIsl.setDestPort(2);
        forwardIsl.setStatus(IslStatus.ACTIVE);
        forwardIsl.setAvailableBandwidth(availableBandwidth);
        islRepository.createOrUpdate(forwardIsl);

        Isl reverseIsl = new Isl();
        reverseIsl.setDestSwitch(switchA);
        reverseIsl.setDestPort(1);
        reverseIsl.setSrcSwitch(switchB);
        reverseIsl.setSrcPort(2);
        reverseIsl.setStatus(IslStatus.ACTIVE);
        reverseIsl.setAvailableBandwidth(availableBandwidth);
        islRepository.createOrUpdate(reverseIsl);

        assertEquals(2, islRepository.findSymmetricActiveWithAvailableBandwidth(availableBandwidth).size());
    }

    @Test
    public void shouldNotReturnIslIfOneDirectionDoesntHaveEnoughBandwidth() {
        long availableBandwidth = 100L;

        Isl forwardIsl = new Isl();
        forwardIsl.setSrcSwitch(switchA);
        forwardIsl.setSrcPort(1);
        forwardIsl.setDestSwitch(switchB);
        forwardIsl.setDestPort(2);
        forwardIsl.setStatus(IslStatus.ACTIVE);
        forwardIsl.setAvailableBandwidth(availableBandwidth);
        islRepository.createOrUpdate(forwardIsl);

        Isl reverseIsl = new Isl();
        reverseIsl.setDestSwitch(switchA);
        reverseIsl.setDestPort(1);
        reverseIsl.setSrcSwitch(switchB);
        reverseIsl.setSrcPort(2);
        reverseIsl.setStatus(IslStatus.ACTIVE);
        reverseIsl.setAvailableBandwidth(availableBandwidth - 1);
        islRepository.createOrUpdate(reverseIsl);

        assertEquals(0, islRepository.findSymmetricActiveWithAvailableBandwidth(availableBandwidth).size());
    }
}
