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

import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.KildaConfiguration;
import org.openkilda.persistence.Neo4jBasedTest;
import org.openkilda.persistence.repositories.KildaConfigurationRepository;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collection;

public class Neo4jKildaConfigurationRepositoryTest extends Neo4jBasedTest {
    private static KildaConfigurationRepository kildaConfigurationRepository;

    @BeforeClass
    public static void setUp() {
        kildaConfigurationRepository = new Neo4jKildaConfigurationRepository(neo4jSessionFactory, txManager);
    }

    @Test
    public void shouldCreateAndUpdateKildaConfiguration() {
        KildaConfiguration kildaConfiguration = kildaConfigurationRepository.get();
        assertEquals(KildaConfiguration.DEFAULTS, kildaConfiguration);

        KildaConfiguration emptyKildaConfiguration = KildaConfiguration.builder().build();
        kildaConfigurationRepository.createOrUpdate(emptyKildaConfiguration);

        KildaConfiguration foundKildaConfiguration = kildaConfigurationRepository.get();
        assertEquals(KildaConfiguration.DEFAULTS.getFlowEncapsulationType(),
                foundKildaConfiguration.getFlowEncapsulationType());

        kildaConfiguration = KildaConfiguration.builder()
                .flowEncapsulationType(FlowEncapsulationType.VXLAN)
                .build();
        kildaConfigurationRepository.createOrUpdate(kildaConfiguration);

        foundKildaConfiguration = kildaConfigurationRepository.get();
        assertEquals(kildaConfiguration, foundKildaConfiguration);

        emptyKildaConfiguration = KildaConfiguration.builder().build();
        kildaConfigurationRepository.createOrUpdate(emptyKildaConfiguration);

        foundKildaConfiguration = kildaConfigurationRepository.get();
        assertEquals(kildaConfiguration.getFlowEncapsulationType(),
                foundKildaConfiguration.getFlowEncapsulationType());

        kildaConfiguration = KildaConfiguration.builder()
                .flowEncapsulationType(FlowEncapsulationType.TRANSIT_VLAN)
                .build();
        kildaConfigurationRepository.createOrUpdate(kildaConfiguration);

        Collection<KildaConfiguration> kildaConfigurationCollection = kildaConfigurationRepository.findAll();
        assertThat(kildaConfigurationCollection, hasSize(1));
        assertEquals(kildaConfiguration, kildaConfigurationCollection.iterator().next());
    }
}
