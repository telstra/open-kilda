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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.openkilda.model.PortProperties;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.Neo4jBasedTest;
import org.openkilda.persistence.repositories.PortPropertiesRepository;
import org.openkilda.persistence.repositories.SwitchRepository;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collection;
import java.util.Optional;

public class Neo4jPortPropertiesRepositoryTest extends Neo4jBasedTest {

    private static final SwitchId TEST_SWITCH_ID = new SwitchId(1);

    private static SwitchRepository switchRepository;
    private static PortPropertiesRepository portPropertiesRepository;

    @BeforeClass
    public static void setUp() {
        switchRepository = new Neo4jSwitchRepository(neo4jSessionFactory, txManager);
        portPropertiesRepository = new Neo4jPortPropertiesRepository(neo4jSessionFactory, txManager);
    }

    @Test
    public void shouldCreatePortPropertiesWithRelation() {
        Switch origSwitch = Switch.builder().switchId(TEST_SWITCH_ID).build();
        switchRepository.createOrUpdate(origSwitch);

        PortProperties portProperties = PortProperties.builder()
                .switchObj(origSwitch)
                .discoveryEnabled(false).build();
        portPropertiesRepository.createOrUpdate(portProperties);

        Collection<PortProperties> portPropertiesResult = portPropertiesRepository.findAll();
        assertEquals(1, portPropertiesResult.size());
        assertNotNull(portPropertiesResult.iterator().next().getSwitchObj());
    }

    @Test
    public void shouldGetPortPropertiesBySwitchIdAndPort() {
        Switch origSwitch = Switch.builder().switchId(TEST_SWITCH_ID).build();

        switchRepository.createOrUpdate(origSwitch);

        int port = 7;
        PortProperties portProperties = PortProperties.builder()
                .switchObj(origSwitch)
                .port(port)
                .discoveryEnabled(false).build();

        portPropertiesRepository.createOrUpdate(portProperties);
        Optional<PortProperties> portPropertiesResult =
                portPropertiesRepository.getBySwitchIdAndPort(origSwitch.getSwitchId(), port);
        assertTrue(portPropertiesResult.isPresent());
        assertEquals(origSwitch.getSwitchId(), portPropertiesResult.get().getSwitchObj().getSwitchId());
        assertEquals(port, portPropertiesResult.get().getPort());
        assertFalse(portPropertiesResult.get().isDiscoveryEnabled());
    }
}
