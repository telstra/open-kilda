/* Copyright 2019 Telstra Open Source
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
import static org.junit.Assert.assertTrue;

import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchProperties;
import org.openkilda.persistence.Neo4jBasedTest;
import org.openkilda.persistence.repositories.SwitchPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Neo4JSwitchPropertiesRepositoryTest extends Neo4jBasedTest {
    static final SwitchId TEST_SWITCH_ID = new SwitchId(1);

    static SwitchRepository switchRepository;
    static SwitchPropertiesRepository switchPropertiesRepository;

    @BeforeClass
    public static void setUp() {
        switchRepository = new Neo4jSwitchRepository(neo4jSessionFactory, txManager);
        switchPropertiesRepository = new Neo4JSwitchPropertiesRepository(neo4jSessionFactory, txManager);
    }

    @Test
    public void shouldCreateSwitchPropertiesWithRelation() {
        Switch origSwitch = Switch.builder().switchId(TEST_SWITCH_ID)
                .description("Some description").build();

        switchRepository.createOrUpdate(origSwitch);
        SwitchProperties switchProperties = SwitchProperties.builder().switchObj(origSwitch)
                .supportedTransitEncapsulation(SwitchProperties.DEFAULT_FLOW_ENCAPSULATION_TYPES).build();

        switchPropertiesRepository.createOrUpdate(switchProperties);
        List<SwitchProperties> switchPropertiesResult = new ArrayList<>(switchPropertiesRepository.findAll());
        assertEquals(1, switchPropertiesResult.size());
        assertNotNull(switchPropertiesResult.get(0).getSwitchObj());
    }

    @Test
    public void shouldFindSwitchPropertiesBySwitchId() {
        Switch origSwitch = Switch.builder().switchId(TEST_SWITCH_ID)
                .description("Some description").build();

        switchRepository.createOrUpdate(origSwitch);
        SwitchProperties switchProperties = SwitchProperties.builder().switchObj(origSwitch)
                .supportedTransitEncapsulation(SwitchProperties.DEFAULT_FLOW_ENCAPSULATION_TYPES).build();

        switchPropertiesRepository.createOrUpdate(switchProperties);
        Optional<SwitchProperties> switchPropertiesOptional = switchPropertiesRepository.findBySwitchId(TEST_SWITCH_ID);
        assertTrue(switchPropertiesOptional.isPresent());
    }

}
