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

import static org.junit.Assert.assertEquals;

import org.openkilda.model.Flow;
import org.openkilda.model.FlowSegment;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.TestConfigurationProvider;
import org.openkilda.persistence.neo4j.Neo4jConfig;
import org.openkilda.persistence.neo4j.Neo4jTransactionManager;
import org.openkilda.persistence.repositories.FlowRepository;
import org.openkilda.persistence.repositories.FlowSegmentRepository;
import org.openkilda.persistence.repositories.SwitchRepository;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.neo4j.ogm.testutil.TestServer;

import java.io.IOException;
import java.util.Collection;

public class FlowSegmentRepositoryImplTest {
    static final SwitchId TEST_SWITCH_A_ID = new SwitchId(1L);
    static final SwitchId TEST_SWITCH_B_ID = new SwitchId(2L);
    static final SwitchId TEST_SWITCH_C_ID = new SwitchId(3L);
    static final long COOKIE_ID = 1L;
    static final String FLOW_ID = "12";

    static TestServer testServer;

    static FlowSegmentRepository flowSegmentRepository;
    static FlowRepository flowRepository;
    static SwitchRepository switchRepository;

    @BeforeClass
    public static void setUp() throws IOException {
        testServer = new TestServer(true, true, 5, 7687);
        Neo4jConfig neo4jConfig = new TestConfigurationProvider().getConfiguration(Neo4jConfig.class);
        Neo4jTransactionManager txManager = new Neo4jTransactionManager(neo4jConfig);
        flowRepository = new FlowRepositoryImpl(txManager);
        switchRepository = new SwitchRepositoryImpl(txManager);
        flowSegmentRepository = new FlowSegmentRepositoryImpl(txManager);
    }

    @AfterClass
    public static void tearDown() {
        testServer.shutdown();
    }

    @Test
    public void shouldCreateAndFindFlow() {
        Switch switchA = new Switch();
        switchA.setSwitchId(TEST_SWITCH_A_ID);

        Switch switchB = new Switch();
        switchB.setSwitchId(TEST_SWITCH_B_ID);

        Switch switchC = new Switch();
        switchC.setSwitchId(TEST_SWITCH_C_ID);

        Flow flow = new Flow();
        flow.setSrcSwitch(switchA);
        flow.setDestSwitch(switchC);
        flow.setFlowId(FLOW_ID);
        flow.setCookie(COOKIE_ID);
        flowRepository.createOrUpdate(flow);
        FlowSegment flowSegment = new FlowSegment();
        flowSegment.setCookieId(COOKIE_ID);
        flowSegment.setParentCookieId(COOKIE_ID);
        flowSegment.setFlowId(FLOW_ID);
        flowSegment.setSrcSwitch(switchA);
        flowSegment.setDestSwitch(switchB);
        flowSegmentRepository.createOrUpdate(flowSegment);
        flowSegment = new FlowSegment();
        flowSegment.setCookieId(COOKIE_ID);
        flowSegment.setParentCookieId(COOKIE_ID);
        flowSegment.setFlowId(FLOW_ID);
        flowSegment.setSrcSwitch(switchB);
        flowSegment.setDestSwitch(switchC);
        flowSegmentRepository.createOrUpdate(flowSegment);
        Collection<FlowSegment> allSegments = flowSegmentRepository.findAll();
        assertEquals(2, allSegments.size());
        long removedSegmentsCount = flowSegmentRepository.deleteFlowSegments(flow);
        assertEquals(2L, removedSegmentsCount);
        allSegments = flowSegmentRepository.findAll();
        assertEquals(0, allSegments.size());
    }

}
