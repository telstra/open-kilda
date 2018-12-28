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
import static org.junit.Assert.assertThat;

import org.openkilda.model.LinkProps;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.Neo4jBasedTest;
import org.openkilda.persistence.repositories.LinkPropsRepository;

import com.google.common.collect.Lists;
import org.hamcrest.Matchers;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

public class Neo4jLinkPropsRepositoryTest extends Neo4jBasedTest {
    static final SwitchId TEST_SWITCH_A_ID = new SwitchId(1);
    static final SwitchId TEST_SWITCH_B_ID = new SwitchId(2);

    static LinkPropsRepository linkPropsRepository;

    @BeforeClass
    public static void setUp() {
        linkPropsRepository = new Neo4jLinkPropsRepository(neo4jSessionFactory, txManager);
    }

    @Test
    public void shouldCreateLinkProps() {
        LinkProps linkProps = LinkProps.builder()
                .dstPort(1)
                .srcPort(1)
                .srcSwitchId(TEST_SWITCH_A_ID)
                .dstSwitchId(TEST_SWITCH_B_ID)
                .build();
        linkPropsRepository.createOrUpdate(linkProps);

        assertEquals(1, linkPropsRepository.findAll().size());
    }

    @Test
    public void shouldDeleteLinkProps() {
        LinkProps linkProps = LinkProps.builder()
                .dstPort(1)
                .srcPort(1)
                .srcSwitchId(TEST_SWITCH_A_ID)
                .dstSwitchId(TEST_SWITCH_B_ID)
                .build();
        linkPropsRepository.createOrUpdate(linkProps);
        linkPropsRepository.delete(linkProps);

        assertEquals(0, linkPropsRepository.findAll().size());
    }

    @Test
    public void shouldFindLinkPropsByEndpoints() {
        LinkProps linkProps = LinkProps.builder()
                .dstPort(2)
                .srcPort(1)
                .srcSwitchId(TEST_SWITCH_A_ID)
                .dstSwitchId(TEST_SWITCH_B_ID)
                .build();
        linkPropsRepository.createOrUpdate(linkProps);

        List<LinkProps> foundLinkProps = Lists.newArrayList(
                linkPropsRepository.findByEndpoints(TEST_SWITCH_A_ID, 1, TEST_SWITCH_B_ID, 2));
        assertThat(foundLinkProps, Matchers.hasSize(1));
    }

    @Test
    public void shouldFindLinkPropsBySrcEndpoint() {
        LinkProps linkProps = LinkProps.builder()
                .dstPort(2)
                .srcPort(1)
                .srcSwitchId(TEST_SWITCH_A_ID)
                .dstSwitchId(TEST_SWITCH_B_ID)
                .build();
        linkPropsRepository.createOrUpdate(linkProps);

        List<LinkProps> foundLinkProps = Lists.newArrayList(
                linkPropsRepository.findByEndpoints(TEST_SWITCH_A_ID, 1, null, null));
        assertThat(foundLinkProps, Matchers.hasSize(1));
    }

    @Test
    public void shouldFindLinkPropsByDestEndpoint() {
        LinkProps linkProps = LinkProps.builder()
                .dstPort(2)
                .srcPort(1)
                .srcSwitchId(TEST_SWITCH_A_ID)
                .dstSwitchId(TEST_SWITCH_B_ID)
                .build();
        linkPropsRepository.createOrUpdate(linkProps);

        List<LinkProps> foundLinkProps = Lists.newArrayList(
                linkPropsRepository.findByEndpoints(null, null, TEST_SWITCH_B_ID, 2));
        assertThat(foundLinkProps, Matchers.hasSize(1));
    }

    @Test
    public void shouldFindLinkPropsBySrcAndDestSwitches() {
        LinkProps linkProps = LinkProps.builder()
                .dstPort(2)
                .srcPort(1)
                .srcSwitchId(TEST_SWITCH_A_ID)
                .dstSwitchId(TEST_SWITCH_B_ID)
                .build();
        linkPropsRepository.createOrUpdate(linkProps);

        List<LinkProps> foundLinkProps = Lists.newArrayList(
                linkPropsRepository.findByEndpoints(TEST_SWITCH_A_ID, null, TEST_SWITCH_B_ID, null));
        assertThat(foundLinkProps, Matchers.hasSize(1));
    }
}

