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

package org.openkilda.persistence;

import org.openkilda.model.Cookie;
import org.openkilda.model.FlowCookie;
import org.openkilda.model.PathId;
import org.openkilda.model.Switch;
import org.openkilda.persistence.repositories.FlowCookieRepository;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.persistence.repositories.impl.Neo4jFlowCookieRepository;
import org.openkilda.persistence.repositories.impl.Neo4jSwitchRepository;

import org.junit.BeforeClass;
import org.junit.Test;

public class Neo4jConstraintsTest extends Neo4jBasedTest {
    static final Cookie TEST_COOKIE = new Cookie(1);
    static final Cookie TEST_COOKIE_2 = new Cookie(2);
    static final String TEST_FLOW_1_ID = "test_flow_1";
    static final String TEST_FLOW_2_ID = "test_flow_2";

    static SwitchRepository switchRepository;
    static FlowCookieRepository flowCookieRepository;

    @BeforeClass
    public static void setUp() {
        switchRepository = new Neo4jSwitchRepository(neo4jSessionFactory, txManager);
        flowCookieRepository = new Neo4jFlowCookieRepository(neo4jSessionFactory, txManager);
    }

    @Test(expected = ConstraintViolationException.class)
    public void throwConstraintErrorOnCreateForAnotherFlow() {
        // given
        Switch srcSwitch = buildTestSwitch(1);
        switchRepository.createOrUpdate(srcSwitch);
        Switch dstSwitch = buildTestSwitch(2);
        switchRepository.createOrUpdate(dstSwitch);

        FlowCookie flowCookie1 = FlowCookie.builder()
                .cookie(TEST_COOKIE)
                .flowId(TEST_FLOW_1_ID)
                .pathId(new PathId(TEST_FLOW_1_ID + "_path"))
                .build();
        flowCookieRepository.createOrUpdate(flowCookie1);

        // when
        FlowCookie flowCookie2 = FlowCookie.builder()
                .cookie(TEST_COOKIE)
                .flowId(TEST_FLOW_2_ID)
                .pathId(new PathId(TEST_FLOW_2_ID + "_path"))
                .build();
        flowCookieRepository.createOrUpdate(flowCookie2);
    }

    @Test(expected = ConstraintViolationException.class)
    public void throwConstraintErrorOnCreateForSamePath() {
        // given
        Switch srcSwitch = buildTestSwitch(1);
        switchRepository.createOrUpdate(srcSwitch);
        Switch dstSwitch = buildTestSwitch(2);
        switchRepository.createOrUpdate(dstSwitch);

        FlowCookie flowCookie1 = FlowCookie.builder()
                .cookie(TEST_COOKIE)
                .flowId(TEST_FLOW_1_ID)
                .pathId(new PathId(TEST_FLOW_1_ID + "_path"))
                .build();
        flowCookieRepository.createOrUpdate(flowCookie1);

        // when
        FlowCookie flowCookie2 = FlowCookie.builder()
                .cookie(TEST_COOKIE_2)
                .flowId(TEST_FLOW_1_ID)
                .pathId(new PathId(TEST_FLOW_1_ID + "_path"))
                .build();
        flowCookieRepository.createOrUpdate(flowCookie2);
    }

    @Test(expected = ConstraintViolationException.class)
    public void throwConstraintErrorOnCreateForSamePathAndCookie() {
        // given
        Switch srcSwitch = buildTestSwitch(1);
        switchRepository.createOrUpdate(srcSwitch);
        Switch dstSwitch = buildTestSwitch(2);
        switchRepository.createOrUpdate(dstSwitch);

        FlowCookie flowCookie1 = FlowCookie.builder()
                .cookie(TEST_COOKIE)
                .flowId(TEST_FLOW_1_ID)
                .pathId(new PathId(TEST_FLOW_1_ID + "_path"))
                .build();
        flowCookieRepository.createOrUpdate(flowCookie1);

        // when
        FlowCookie flowCookie2 = FlowCookie.builder()
                .cookie(TEST_COOKIE)
                .flowId(TEST_FLOW_1_ID)
                .pathId(new PathId(TEST_FLOW_1_ID + "_path"))
                .build();
        flowCookieRepository.createOrUpdate(flowCookie2);
    }
}

