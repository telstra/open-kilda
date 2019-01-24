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

import org.openkilda.model.Cookie;
import org.openkilda.model.FlowCookie;
import org.openkilda.model.PathId;
import org.openkilda.persistence.Neo4jBasedTest;
import org.openkilda.persistence.repositories.FlowCookieRepository;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collection;

public class Neo4jFlowCookieRepositoryTest extends Neo4jBasedTest {
    static final String TEST_FLOW_ID = "test_flow";
    static final Cookie TEST_COOKIE = new Cookie(1);

    static FlowCookieRepository flowCookieRepository;

    @BeforeClass
    public static void setUp() {
        flowCookieRepository = new Neo4jFlowCookieRepository(neo4jSessionFactory, txManager);
    }

    @Test
    public void shouldCreateFlowCookie() {
        FlowCookie cookie = FlowCookie.builder()
                .cookie(TEST_COOKIE)
                .pathId(new PathId(TEST_FLOW_ID + "_path"))
                .flowId(TEST_FLOW_ID)
                .build();
        flowCookieRepository.createOrUpdate(cookie);

        Collection<FlowCookie> allCookies = flowCookieRepository.findAll();
        FlowCookie foundCookie = allCookies.iterator().next();

        assertEquals(TEST_COOKIE, foundCookie.getCookie());
        assertEquals(TEST_FLOW_ID, foundCookie.getFlowId());
    }

    @Test
    public void shouldDeleteFlowCookie() {
        FlowCookie cookie = FlowCookie.builder()
                .cookie(TEST_COOKIE)
                .pathId(new PathId(TEST_FLOW_ID + "_path"))
                .flowId(TEST_FLOW_ID)
                .build();
        flowCookieRepository.createOrUpdate(cookie);

        flowCookieRepository.delete(cookie);

        assertEquals(0, flowCookieRepository.findAll().size());
    }

    @Test
    public void shouldDeleteFoundFlowCookie() {
        FlowCookie cookie = FlowCookie.builder()
                .cookie(TEST_COOKIE)
                .pathId(new PathId(TEST_FLOW_ID + "_path"))
                .flowId(TEST_FLOW_ID)
                .build();
        flowCookieRepository.createOrUpdate(cookie);

        Collection<FlowCookie> allCookies = flowCookieRepository.findAll();
        FlowCookie foundCookie = allCookies.iterator().next();
        flowCookieRepository.delete(foundCookie);

        assertEquals(0, flowCookieRepository.findAll().size());
    }
}
