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
import static org.junit.Assert.assertNotNull;

import org.openkilda.model.LinkProps;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.Neo4jBasedTest;
import org.openkilda.persistence.repositories.LinkPropsRepository;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class Neo4jLinkPropsRepositoryTest extends Neo4jBasedTest {
    static final SwitchId TEST_SWITCH_A_ID = new SwitchId(1);
    static final SwitchId TEST_SWITCH_B_ID = new SwitchId(2);

    static LinkPropsRepository linkPropsRepository;

    @BeforeClass
    public static void setUp() {
        linkPropsRepository = new Neo4jLinkPropsRepository(neo4jSessionFactory);
    }

    @Test
    public void shouldCreateAndFindLinkProps() {
        LinkProps linkProps = new LinkProps();
        linkProps.setDstPort(1);
        linkProps.setSrcPort(1);
        linkProps.setSrcSwitchId(TEST_SWITCH_A_ID);
        linkProps.setDstSwitchId(TEST_SWITCH_B_ID);
        Map<String, Object> props = new HashMap<>();
        props.put("customProp", "customValue");
        linkProps.setProperties(props);
        linkPropsRepository.createOrUpdate(linkProps);
        Collection<LinkProps> rs = linkPropsRepository.findAll();
        assertEquals(1, rs.size());
        linkPropsRepository.delete(linkProps);
    }

    @Test
    public void shouldCreateAndFindLinkPropsByAttributes() {
        LinkProps linkProps = new LinkProps();
        linkProps.setDstPort(1);
        linkProps.setSrcPort(1);
        linkProps.setSrcSwitchId(TEST_SWITCH_A_ID);
        linkProps.setDstSwitchId(TEST_SWITCH_B_ID);
        Map<String, Object> props = new HashMap<>();
        props.put("customProp", "customValue");
        linkProps.setProperties(props);
        linkPropsRepository.createOrUpdate(linkProps);
        LinkProps dbObj = linkPropsRepository.findByEndpoints(TEST_SWITCH_A_ID, 1,
                 TEST_SWITCH_B_ID, 1);
        assertNotNull(dbObj);
        linkPropsRepository.delete(linkProps);
    }
}

