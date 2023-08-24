/* Copyright 2023 Telstra Open Source
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

package org.openkilda.wfm.topology.nbworker.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.openkilda.model.FlowEncapsulationType.TRANSIT_VLAN;
import static org.openkilda.model.FlowEncapsulationType.VXLAN;
import static org.openkilda.model.PathComputationStrategy.COST;
import static org.openkilda.model.PathComputationStrategy.LATENCY;
import static org.openkilda.model.PathComputationStrategy.MAX_LATENCY;

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.KildaConfiguration;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.KildaConfigurationRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

public class KildaConfigurationServiceTest extends InMemoryGraphBasedTest {
    private static KildaConfigurationRepository kildaConfigurationRepository;
    private static KildaConfigurationService kildaConfigurationService;

    @BeforeAll
    public static void setUpOnce() {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        kildaConfigurationRepository = repositoryFactory.createKildaConfigurationRepository();
        kildaConfigurationService = new KildaConfigurationService(persistenceManager.getRepositoryFactory(),
                persistenceManager.getTransactionManager());
    }

    @Test
    public void trueMultiTable() {
        createConfiguration(false, VXLAN, COST);
        KildaConfiguration kildaConfiguration = KildaConfiguration.builder().useMultiTable(true).build();
        kildaConfigurationService.updateKildaConfiguration(kildaConfiguration);

        Optional<KildaConfiguration> updatedConfiguration = kildaConfigurationRepository.find();
        Assertions.assertTrue(updatedConfiguration.isPresent());
        Assertions.assertTrue(updatedConfiguration.get().getUseMultiTable());
    }

    @Test
    public void nullMultiTable() {
        createConfiguration(false, VXLAN, COST);
        KildaConfiguration kildaConfiguration = KildaConfiguration.builder().useMultiTable(null).build();
        kildaConfigurationService.updateKildaConfiguration(kildaConfiguration);

        Optional<KildaConfiguration> updatedConfiguration = kildaConfigurationRepository.find();
        Assertions.assertTrue(updatedConfiguration.isPresent());
        Assertions.assertFalse(updatedConfiguration.get().getUseMultiTable());
    }

    @Test
    public void falseMultiTable() {
        createConfiguration(true, VXLAN, COST);
        KildaConfiguration kildaConfiguration = KildaConfiguration.builder().useMultiTable(false).build();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> kildaConfigurationService.updateKildaConfiguration(kildaConfiguration));
        assertEquals("Single table mode was deprecated. OpenKilda doesn't support it anymore. Configuration "
                + "property `use_multi_table` can't be 'false'.", thrown.getMessage());
    }

    @Test
    public void updateEncapsulationTypeTable() {
        createConfiguration(true, TRANSIT_VLAN, COST);
        KildaConfiguration kildaConfiguration = KildaConfiguration.builder()
                .flowEncapsulationType(VXLAN).build();
        kildaConfigurationService.updateKildaConfiguration(kildaConfiguration);

        Optional<KildaConfiguration> updatedConfiguration = kildaConfigurationRepository.find();
        Assertions.assertTrue(updatedConfiguration.isPresent());
        Assertions.assertEquals(VXLAN, updatedConfiguration.get().getFlowEncapsulationType());
    }

    @Test
    public void updateStrategyTable() {
        createConfiguration(true, VXLAN, MAX_LATENCY);
        KildaConfiguration kildaConfiguration = KildaConfiguration.builder().pathComputationStrategy(LATENCY).build();
        kildaConfigurationService.updateKildaConfiguration(kildaConfiguration);

        Optional<KildaConfiguration> updatedConfiguration = kildaConfigurationRepository.find();
        Assertions.assertTrue(updatedConfiguration.isPresent());
        Assertions.assertEquals(LATENCY, updatedConfiguration.get().getPathComputationStrategy());
    }

    private void createConfiguration(
            boolean useMultiTable, FlowEncapsulationType encapsulation, PathComputationStrategy computationStrategy) {
        KildaConfiguration kildaConfiguration = KildaConfiguration.builder()
                .useMultiTable(useMultiTable)
                .flowEncapsulationType(encapsulation)
                .pathComputationStrategy(computationStrategy)
                .build();
        kildaConfigurationRepository.add(kildaConfiguration);
    }
}
